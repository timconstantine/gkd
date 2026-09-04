package li.gkd.app.a11y

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.getAndUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import li.gkd.app.META
import li.gkd.app.data.ActionPerformer
import li.gkd.app.data.ActionResult
import li.gkd.app.data.AppRule
import li.gkd.app.data.GkdAction
import li.gkd.app.data.ResolvedRule
import li.gkd.app.data.RpcError
import li.gkd.app.data.RuleStatus
import li.gkd.app.isActivityVisible
import li.gkd.app.service.A11yService
import li.gkd.app.service.EventService
import li.gkd.app.service.topAppIdFlow
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.uiAutomationFlow
import li.gkd.app.store.actualBlockA11yAppList
import li.gkd.app.store.storeFlow
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.AutomatorModeOption
import li.gkd.app.util.launchTry
import li.gkd.app.util.runMainPost
import li.gkd.app.util.showActionToast
import li.gkd.app.util.systemUiAppId
import li.gkd.selector.MatchOptions
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult
import li.gkd.selector.SelectorTypeResult
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds


private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val queryDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
private val actionDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

private val latestServiceMode = atomic(0)
private val latestServiceTime = atomic(0L)

class A11yRuleEngine(val service: A11yCommonImpl) {
    private val a11yContext = A11yContext(this)
    private val effective get() = latestServiceMode.value == service.mode.value
    private val hasOthersService = when (service.mode) {
        AutomatorModeOption.A11yMode -> uiAutomationFlow.value != null
        AutomatorModeOption.AutomationMode -> A11yService.instance != null
    }

    fun onA11yConnected() {
        val serviceTime = System.currentTimeMillis()
        latestServiceMode.value = service.mode.value
        latestServiceTime.value = serviceTime
        if (storeFlow.value.enableBlockA11yAppList && !actualBlockA11yAppList.contains(topAppIdFlow.value)) {
            startQueryJob(byForced = true)
        }
        runMainPost(1000L) {// coexist for 1000ms, waiting for the other service to stabilize
            if (latestServiceTime.value == serviceTime) {
                when (service.mode) {
                    AutomatorModeOption.A11yMode -> uiAutomationFlow.value?.shutdown(true)
                    AutomatorModeOption.AutomationMode -> A11yService.instance?.shutdown(true)
                }
            }
        }
    }

    fun onScreenForcedActive() {
        // Screen off -> Activity::onStop -> Screen on -> Activity::onStart -> Activity::onResume
        val a = topActivityFlow.value
        synchronized(topActivityFlow) {
            updateTopActivity(
                a.appId,
                a.activityId,
                scene = ActivityScene.ScreenOn
            )
        }
        startQueryJob()
    }

    val safeActiveWindow: AccessibilityNodeInfo?
        get() = try {
            // Some apps take 554ms
            // java.lang.SecurityException: Call from user 0 as user -2 without permission INTERACT_ACROSS_USERS or INTERACT_ACROSS_USERS_FULL not allowed.
            service.windowNodeInfo?.setGeneratedTime()
        } catch (_: Throwable) {
            null
        }.apply {
            a11yContext.rootCache.value = this
        }

    val safeActiveWindowAppId: String?
        get() = safeActiveWindow?.packageName?.toString()

    private val scope get() = service.scope

    @Volatile
    private var latestStateEvent: A11yEvent? = null
    private var lastContentEventTime = 0L
    private var lastEventTime = 0L
    private val eventDeque = ArrayDeque<A11yEvent>()
    fun onA11yEvent(event: AccessibilityEvent?) {
        if (!effective) return
        if (!event.isUseful()) return
        // Reject accessibility events from secondary displays
        if (AndroidTarget.TIRAMISU && event.displayId != Display.DEFAULT_DISPLAY) return
        onA11yFeatEvent(event)
        if (event.eventType == CONTENT_CHANGED) {
            if (!isInteractive) return // Accessibility events still arrive after the screen is off: type:2048, time:8094, app:com.miui.aod, cls:android.widget.TextView
            if (event.packageName == systemUiAppId && event.packageName != topActivityFlow.value.appId) return
        }
        // Filter out some IME events
        if (event.packageName == imeAppId && topActivityFlow.value.appId != imeAppId) {
            if (event.recordCount == 0 && event.action == 0 && !event.isFullScreen) return
        }
        // Directly discard our own events, and update topActivity ourselves
        if ((event.eventType == CONTENT_CHANGED || !isActivityVisible) && event.packageName == META.appId) return

        val a11yEvent = event.toA11yEvent() ?: return
        if (a11yEvent.type == CONTENT_CHANGED) {
            // Prevent content-type events from firing too fast
            if (a11yEvent.time - lastContentEventTime < 100 && a11yEvent.time - appChangeTime > 5000 && a11yEvent.time - lastTriggerTime > 3000) {
                return
            }
            lastContentEventTime = a11yEvent.time
        }
        EventService.logEvent(event)
        if (META.debuggable) {
            Log.d(
                "onNewA11yEvent",
                "type:${event.eventType}, time:${event.eventTime - lastEventTime}, app:${event.packageName}, cls:${event.className}"
            )
        }
        if (event.eventTime < lastEventTime) {
            // Some apps send events with a negative timestamp; discard them directly
            // type:32, time:-104, app:com.miui.home, cls:com.miui.home.launcher.Launcher
            return
        }
        lastEventTime = event.eventTime
        if (event.eventType == STATE_CHANGED) {
            latestStateEvent = a11yEvent
        }
        synchronized(eventDeque) { eventDeque.addLast(a11yEvent) }
        scope.launch(eventDispatcher) { consumeEvent(a11yEvent) }
    }

    private val queryEvents = mutableListOf<A11yEvent>()
    private suspend fun consumeEvent(headEvent: A11yEvent) {
        val consumedEvents = synchronized(eventDeque) {
            if (eventDeque.firstOrNull() !== headEvent) return
            eventDeque.filter { it.sameAs(headEvent) }.apply {
                repeat(size) { eventDeque.removeFirst() }
            }
        }
        val latestEvent = consumedEvents.last()
        val evAppId = latestEvent.appId
        val evActivityId = latestEvent.name
        val oldAppId = topActivityFlow.value.appId
        val rightAppId = if (oldAppId == evAppId) {
            evAppId
        } else {
            getTimeoutAppId() ?: return
        }
        if (rightAppId == evAppId) {
            if (latestEvent.type == STATE_CHANGED) {
                synchronized(topActivityFlow) {
                    // tv.danmaku.bili, com.miui.home, com.miui.home.launcher.Launcher
                    if (isActivity(evAppId, evActivityId)) {
                        updateTopActivity(evAppId, evActivityId)
                    }
                }
            }
        }
        if (rightAppId != topActivityFlow.value.appId) {
            synchronized(topActivityFlow) {
                // In cases like returning from the lock screen or pulling down the notification shade, the app itself won't send an event, but system components will
                val topCpn = privilegeContextFlow.value?.topCpn()
                if (topCpn?.packageName == rightAppId) {
                    updateTopActivity(topCpn.packageName, topCpn.className)
                } else {
                    updateTopActivity(rightAppId, null)
                }
            }
        }
        val activityRule = activityRuleFlow.value
        if (evAppId != rightAppId || activityRule.skipConsumeEvent || !storeFlow.value.enableMatch) {
            return
        }
        synchronized(queryEvents) { queryEvents.addAll(consumedEvents) }
        a11yContext.interruptKey++
        startQueryJob(byEvent = latestEvent)
    }

    private var lastGetAppIdTime = 0L
    private var lastAppId: String? = null
    private suspend fun getTimeoutAppId(): String? {
        if (lastAppId != null && System.currentTimeMillis() - lastGetAppIdTime <= 100) return lastAppId
        // For some apps, getting safeActiveWindow via accessibility takes a long time, causing events to pile up and block, so an appId switch isn't detected and state becomes inconsistent
        // https://github.com/gkd-kit/gkd/issues/622
        lastAppId = withTimeoutOrNull(100.milliseconds) {
            runInterruptible(Dispatchers.IO) { safeActiveWindowAppId }
        } ?: privilegeContextFlow.value?.run { topCpn()?.packageName }
        lastGetAppIdTime = System.currentTimeMillis()
        return lastAppId
    }

    // Some scenarios take 5000 ms
    private suspend fun getTimeoutActiveWindow(): AccessibilityNodeInfo? {
        return suspendCancellableCoroutine { s ->
            val temp = atomic<Continuation<AccessibilityNodeInfo?>?>(s)
            scope.launch(Dispatchers.IO) {
                delay(500L.milliseconds)
                if (s.isActive) {
                    temp.getAndUpdate { null }?.resume(null)
                }
            }
            scope.launch(Dispatchers.IO) {
                val a = safeActiveWindow
                if (s.isActive) {
                    temp.getAndUpdate { null }?.resume(a)
                }
            }
        }
    }

    @Volatile
    private var querying = false

    @Synchronized
    private fun startQueryJob(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        byDelayRule: ResolvedRule? = null,
    ) {
        if (!effective) return
        if (!storeFlow.value.enableMatch) return
        if (activityRuleFlow.value.currentRules.isEmpty()) return
        if (querying) return
        // Getting safeActiveWindow when accessibility is starting cold is very slow
        if (byEvent == null && service.justStarted && !hasOthersService) return checkFutureStartJob()
        scope.launchTry(queryDispatcher) {
            querying = true
            val st = if (META.debuggable) System.currentTimeMillis() else 0L
            try {
                if (META.debuggable) {
                    Log.d(
                        "A11yRuleEngine",
                        "startQueryJob start byEvent=${byEvent != null}, byForced=$byForced, byDelayRule=${byDelayRule != null}"
                    )
                }
                queryAction(byEvent, byForced, byDelayRule)
            } finally {
                checkFutureStartJob()
                if (META.debuggable) {
                    val et = System.currentTimeMillis() - st
                    Log.d("A11yRuleEngine", "startQueryJob end $et ms")
                }
                querying = false
            }
        }
    }

    private fun checkFutureStartJob() {
        val t = System.currentTimeMillis()
        if (t - lastTriggerTime < 3000L || t - appChangeTime < 3000L) {
            scope.launch(actionDispatcher) {
                delay(300.milliseconds)
                startQueryJob()
            }
        } else if (activityRuleFlow.value.hasFeatureAction) {
            scope.launch(actionDispatcher) {
                delay(300.milliseconds)
                startQueryJob(byForced = true)
            }
        }
    }

    private fun fixAppId(rightAppId: String) {
        if (topActivityFlow.value.appId == rightAppId) return
        synchronized(topActivityFlow) {
            val topCpn = privilegeContextFlow.value?.topCpn()
            if (topCpn?.packageName == rightAppId) {
                updateTopActivity(topCpn.packageName, topCpn.className)
            } else {
                updateTopActivity(rightAppId, null)
            }
        }
        scope.launch(actionDispatcher) {
            delay(300.milliseconds)
            startQueryJob()
        }
    }

    private suspend fun queryAction(
        byEvent: A11yEvent? = null,
        byForced: Boolean = false,
        delayRule: ResolvedRule? = null,
    ) {
        val tempStateEvent = latestStateEvent
        val newEvents = if (delayRule != null) {// A delayed rule does not consume events
            null
        } else {
            synchronized(queryEvents) {
                if (byEvent != null && queryEvents.isEmpty()) {
                    return
                }
                (if (queryEvents.size > 1) {
                    val hasDiffItem = queryEvents.any { e ->
                        queryEvents.any { e2 -> !e.sameAs(e2) }
                    }
                    if (hasDiffItem) {
                        // There are events for different nodes; discard all and query from root instead
                        null
                    } else {
                        // type, appId, and className match; need to verify outside the synchronized block whether it's the same node
                        arrayOf(
                            queryEvents[queryEvents.size - 2],
                            queryEvents.last(),
                        )
                    }
                } else if (queryEvents.size == 1) {
                    arrayOf(queryEvents.last())
                } else {
                    null
                }).apply {
                    queryEvents.clear()
                }
            }
        }
        val activityRule = synchronized(topActivityFlow) { activityRuleFlow.value }
        activityRule.currentRules.forEach { rule ->
            if (rule.status == RuleStatus.Status3 && rule.matchDelayJob.value == null) {
                rule.matchDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.matchDelay.milliseconds)
                    rule.matchDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
            }
        }
        if (activityRule.skipMatch) {
            // If the current app has no rules, or matching is paused, avoid fetching the event node to prevent blocking
            return
        }
        var lastNode = if (newEvents == null || newEvents.size <= 1) {
            newEvents?.firstOrNull()?.safeSource
        } else {
            // Get the last two events; if their nodes don't match, discard
            // If they match, they're consecutive events from the same node, common on countdown screens
            val lastNode = newEvents.last().safeSource
            if (lastNode == null || lastNode == newEvents[0].safeSource) {
                lastNode
            } else {
                null
            }
        }
        var lastNodeUsed = false
        if (!a11yContext.clearOldAppNodeCache()) {
            if (byEvent != null) { // This is the common case
                // When a new event arrives, if the cache isn't cleared in time, the node can't be found
                a11yContext.clearNodeCache(lastNode)
            }
        }
        for (rule in activityRule.priorityRules) { // There may be too many rules, taking too long
            if (!effective) return
            if (checkOutDate(activityRule, tempStateEvent)) break
            if (delayRule != null && delayRule !== rule) continue
            if (rule.status != RuleStatus.StatusOk) continue
            if (byForced && !rule.checkForced()) continue
            lastNode?.let { n ->
                val refreshOk = (!lastNodeUsed) || (try {
                    val e = n.refresh()
                    if (e) {
                        n.setGeneratedTime()
                    }
                    e
                } catch (_: Throwable) {
                    false
                })
                lastNodeUsed = true
                if (!refreshOk) {
                    lastNode = null
                }
            }
            val nodeVal = (lastNode ?: getTimeoutActiveWindow()) ?: continue
            val rightAppId = nodeVal.packageName?.toString() ?: break
            val matchApp = rule.matchActivity(rightAppId)
            if (topActivityFlow.value.appId != rightAppId || (!matchApp && rule is AppRule)) {
                scope.launch(eventDispatcher) { fixAppId(rightAppId) }
                return
            }
            if (!matchApp) continue
            val target = a11yContext.queryRule(rule, nodeVal) ?: continue
            if (rule.checkDelay() && rule.actionDelayJob.value == null) {
                rule.actionDelayJob.value = scope.launch(actionDispatcher) {
                    delay(rule.actionDelay.milliseconds)
                    rule.actionDelayJob.value = null
                    startQueryJob(byDelayRule = rule)
                }
                continue
            }
            if (rule.status != RuleStatus.StatusOk) break
            if (checkOutDate(activityRule, tempStateEvent)) break
            val actionResult = rule.performAction(target)
            if (actionResult.result) {
                val topActivity = topActivityFlow.value
                rule.trigger()
                scope.launch(actionDispatcher) {
                    delay(300.milliseconds)
                    startQueryJob()
                }
                if (actionResult.action != ActionPerformer.None.action) {
                    showActionToast(rule)
                }
                addActionLog(rule, topActivity, target, actionResult)
            }
        }
    }

    private fun checkOutDate(
        activityRule: ActivityRule,
        stateEvent: A11yEvent?
    ): Boolean {
        if (stateEvent !== latestStateEvent) return true
        synchronized(topActivityFlow) {
            if (activityRule !== activityRuleFlow.value) return true
        }
        return false
    }

    companion object {
        val service: A11yCommonImpl?
            get() = uiAutomationFlow.value?.takeIf {
                it.mode.value == latestServiceMode.value
            } ?: A11yService.instance
        val instance: A11yRuleEngine? get() = service?.ruleEngine

        fun compatWindows(): List<AccessibilityWindowInfo> {
            return try {
                service?.windowInfos
            } catch (_: Throwable) {
                null
            } ?: emptyList()
        }

        fun onScreenForcedActive() {
            instance?.onScreenForcedActive()
        }

        fun performActionBack(): Boolean {
            val r1 = privilegeContextFlow.value?.run {
                inputManager.keyevent(KeyEvent.KEYCODE_BACK)
            }
            if (r1 == true) return true
            return A11yService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true
        }

        suspend fun screenshot(): Bitmap? = service?.screenshot()

        suspend fun execAction(gkdAction: GkdAction): ActionResult {
            val selectorResult = Selector.compile(gkdAction.selector)
            val selector = (selectorResult as? SelectorCompileResult.Success)?.value
                ?: throw RpcError("Invalid selector")
            val typeResult = selector.validateType(selectorTypeModel)
            if (typeResult is SelectorTypeResult.Failure) {
                throw RpcError("Selector type error: ${typeResult.error.message}")
            }
            val s = instance ?: throw RpcError("Service not connected")
            val a = s.safeActiveWindow ?: throw RpcError("No node information for the current screen")
            val targetNode = A11yContext(s, interruptable = false).querySelfOrSelector(
                a, selector, MatchOptions(fastQuery = gkdAction.fastQuery)
            ) ?: throw RpcError("No matching node found")
            return withContext(Dispatchers.IO) {
                ActionPerformer
                    .getAction(gkdAction.action ?: ActionPerformer.None.action)
                    .perform(targetNode, gkdAction)
            }
        }

    }
}
