package li.gkd.app.priv

import android.annotation.SuppressLint
import android.app.UiAutomation
import android.app.UiAutomationHidden
import android.graphics.Bitmap
import android.os.HandlerThread
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import li.gkd.app.a11y.A11yCommonImpl
import li.gkd.app.a11y.A11yRuleEngine
import li.gkd.app.store.updateEnableAutomator
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.AutomatorModeOption
import li.gkd.app.util.LogUtils
import li.gkd.app.util.toast

class AutomationService private constructor(
    private val privilegeContext: PrivilegeContext,
) : A11yCommonImpl {
    override val mode get() = AutomatorModeOption.AutomationMode
    private val handlerThread = HandlerThread("UiAutomatorHandlerThread")
    private val uiAutomationDelegate = lazy {
        UiAutomationHidden(
            handlerThread.looper,
            ProxyUiAutomationConnection(privilegeContext),
        ).toPublic
    }
    private val uiAutomation by uiAutomationDelegate

    override val scope = MainScope()

    override val ruleEngine by lazy { A11yRuleEngine(this) }

    private val listener = UiAutomation.OnAccessibilityEventListener {
        ruleEngine.onA11yEvent(it)
    }

    override suspend fun screenshot(): Bitmap? = withContext(Dispatchers.IO) {
        try {
            privilegeContextFlow.value?.screenshot()
        } catch (e: Throwable) {
            LogUtils.d("takeScreenshot failed", e)
            null
        }
    }

    override val windowNodeInfo: AccessibilityNodeInfo? get() = uiAutomation.rootInActiveWindow
    override val windowInfos: List<AccessibilityWindowInfo> get() = uiAutomation.windows
    private val startTime = System.currentTimeMillis()
    override var justStarted: Boolean = true
        get() {
            if (field) {
                field = System.currentTimeMillis() - startTime < 3_000
            }
            return field
        }

    private var connected = false

    // https://github.com/android-cs/16/blob/main/cmds/uiautomator/library/testrunner-src/com/android/uiautomator/core/UiAutomationShellWrapper.java#L25
    private fun connect() {
        handlerThread.start()
        uiAutomation.toHidden.connect(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        uiAutomation.setOnAccessibilityEventListener(listener)
        connected = true
        toast("Automation started")
        updateEnableAutomator(true)
        ruleEngine.onA11yConnected()
    }

    private fun disconnect() {
        scope.cancel()
        handlerThread.quit()
        if (!uiAutomationDelegate.isInitialized()) return
        val wasConnected = connected
        try {
            if (wasConnected) {
                uiAutomation.setOnAccessibilityEventListener(null)
            }
            uiAutomation.toHidden.disconnect()
        } catch (e: Exception) {
            LogUtils.d("disconnect automation failed", e)
        } finally {
            uiAutomation.quitRemoteCallbackThread()
            connected = false
            if (wasConnected) {
                if (tempShutdownFlag) {
                    toast("Automation partially disabled")
                } else {
                    toast("Automation disabled")
                    updateEnableAutomator(false)
                }
            }
        }
    }

    private var tempShutdownFlag = false
    private val shutdown = atomic(false)
    override fun shutdown(temp: Boolean) {
        if (!shutdown.compareAndSet(expect = false, update = true)) return
        if (temp) {
            tempShutdownFlag = true
        }
        try {
            disconnect()
        } finally {
            uiAutomationFlow.compareAndSet(this, null)
        }
    }

    companion object {
        private val connectLock = Any()

        fun isOtherUiAutomationRunning(): Boolean {
            if (uiAutomationFlow.value != null) return false
            return privilegeContextFlow.value?.run {
                a11yManager.isUiAutomationRunning()
            } == true
        }

        fun showOccupiedWarning(silent: Boolean = false) {
            toast("The automation service is occupied by another app")
            if (!silent) {
                uiAutomationOccupiedFlow.value = true
            }
        }

        fun tryConnect(silent: Boolean = false) {
            synchronized(connectLock) {
                uiAutomationOccupiedFlow.value = false
                if (uiAutomationFlow.value?.connected == true) {
                    return@synchronized
                }
                uiAutomationFlow.value?.shutdown()
                val privilegeContext = privilegeContextFlow.value ?: return@synchronized
                try {
                    if (isOtherUiAutomationRunning()) {
                        showOccupiedWarning(silent)
                        return@synchronized
                    }
                } catch (e: Exception) {
                    toast("Failed to detect automation state: ${e.message}")
                    LogUtils.d("detect automation state failed", e)
                    return@synchronized
                }
                val instance = AutomationService(privilegeContext)
                try {
                    instance.connect()
                    if (!uiAutomationFlow.compareAndSet(expect = null, update = instance)) {
                        instance.shutdown(true)
                        return@synchronized
                    }
                    if (
                        privilegeContextFlow.value !== privilegeContext ||
                        !privilegeContext.serverLifecycleBinder.pingBinder()
                    ) {
                        instance.shutdown(true)
                    }
                } catch (e: Exception) {
                    instance.shutdown(true)
                    toast("Failed to start automation: ${e.message}")
                    LogUtils.d(e)
                }
            }
        }
    }
}

val uiAutomationFlow = MutableStateFlow<AutomationService?>(null)
val uiAutomationOccupiedFlow = MutableStateFlow(false)

private val remoteCallbackThreadField by lazy {
    if (AndroidTarget.P) {
        // When UiAutomation fails synchronously during the CONNECTING phase, disconnect() throws
        // directly before reaching its own finally block, so we need this internal field as a fallback to shut down its callback thread.
        @SuppressLint("SoonBlockedPrivateApi")
        UiAutomation::class.java.getDeclaredField("mRemoteCallbackThread").apply {
            isAccessible = true
        }
    } else {
        null
    }
}

private fun UiAutomation.quitRemoteCallbackThread() {
    try {
        remoteCallbackThreadField?.let { field ->
            (field.get(this) as? HandlerThread)?.quit()
            field.set(this, null)
        }
    } catch (e: Exception) {
        LogUtils.d("quit UiAutomation callback thread failed", e)
    }
}
