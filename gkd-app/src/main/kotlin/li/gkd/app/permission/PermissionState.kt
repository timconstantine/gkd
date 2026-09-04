package li.gkd.app.permission

import android.Manifest
import android.app.AppOpsManager
import android.app.AppOpsManagerHidden
import android.provider.Settings
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.updateAndGet
import li.gkd.app.app
import li.gkd.app.appScope
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.toast
import li.gkd.app.util.updateAllAppInfo
import li.gkd.app.util.updateAppMutex
import priv.kit.core.Privilege

class PermissionState(
    val name: String,
    private val check: () -> Boolean,
    val permission: IPermission? = null,
    val purpose: String? = null,
    val resolution: PermissionResolution? = null,
    private val onChanged: (() -> Unit)? = null,
) {
    val stateFlow = MutableStateFlow(false)
    val value get() = stateFlow.value

    fun updateAndGet(): Boolean {
        return stateFlow.updateAndGet { check() }
    }

    fun refresh(): Boolean {
        val oldValue = value
        val newValue = updateAndGet()
        if (oldValue != newValue) {
            onChanged?.invoke()
        }
        return newValue
    }

    fun checkOrToast(): Boolean {
        val granted = refresh()
        if (!granted) {
            toast("Please grant \"$name\" first")
        }
        return granted
    }
}

data class PermissionResolution(
    val message: String,
    val confirmText: String = "Go to settings",
    val navigateToPrivilegeService: Boolean = false,
)

private fun requestablePermissionState(
    name: String,
    purpose: String,
    permission: IPermission,
    check: () -> Boolean = { XXPermissions.isGrantedPermission(app, permission) },
    onChanged: (() -> Unit)? = null,
) = PermissionState(
    name = name,
    check = check,
    permission = permission,
    purpose = purpose,
    resolution = PermissionResolution(
        message = "\"$name\" is not granted\nPlease enable it in the system permission settings",
    ),
    onChanged = onChanged,
)

private fun checkAllowedOp(op: String): Boolean = app.appOpsManager.checkOpNoThrow(
    op,
    android.os.Process.myUid(),
    app.packageName
).let {
    it != AppOpsManager.MODE_IGNORED && it != AppOpsManager.MODE_ERRORED
}


object PermissionStates {
    // https://github.com/gkd-kit/gkd/issues/954
    // https://github.com/gkd-kit/gkd/issues/887
    val foregroundServiceSpecialUse by lazy {
        PermissionState(
            name = "Special-use foreground service",
            check = {
                if (AndroidTarget.UPSIDE_DOWN_CAKE) {
                    checkAllowedOp(AppOpsManagerHidden.OPSTR_FOREGROUND_SERVICE_SPECIAL_USE)
                } else {
                    true
                }
            },
            resolution = PermissionResolution(
                message = "\"Special-use foreground service\" has been restricted, please re-authorize via the privileged service",
                confirmText = "Go to authorize",
                navigateToPrivilegeService = true,
            ),
        )
    }

    // https://github.com/orgs/gkd-kit/discussions/1234
    private fun checkAccessA11y(): Boolean {
        return !AndroidTarget.Q ||
                checkAllowedOp(AppOpsManagerHidden.OPSTR_ACCESS_ACCESSIBILITY)
    }

    val Manifest_permission_GET_APP_OPS_STATS get() = "android.permission.GET_APP_OPS_STATS"

    private var canRestrictsRead = true
    private fun checkAccessRestrictedSettings(): Boolean {
        return if (
            canRestrictsRead &&
            AndroidTarget.UPSIDE_DOWN_CAKE &&
            app.checkGrantedPermission(Manifest_permission_GET_APP_OPS_STATS)
        ) {
            try {
                // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r55:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4237
                checkAllowedOp(AppOpsManagerHidden.OPSTR_ACCESS_RESTRICTED_SETTINGS)
            } catch (_: SecurityException) {
                // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r54:frameworks/base/services/core/java/com/android/server/appop/AppOpsService.java;l=4227
                canRestrictsRead = false
                true
            }
        } else {
            true
        }
    }

    private val appOpsAllowed by lazy {
        PermissionState(
            name = "Launch-related operation permission",
            check = {
                val accessA11yAllowed = checkAccessA11y()
                val accessRestrictedSettingsAllowed = checkAccessRestrictedSettings()
                accessA11yAllowed && accessRestrictedSettingsAllowed
            },
        )
    }

    val appOpsRestrictedFlow by lazy {
        combine(
            appOpsAllowed.stateFlow,
            foregroundServiceSpecialUse.stateFlow,
        ) { appOpsAllowed, foregroundServiceSpecialUseAllowed ->
            !appOpsAllowed || !foregroundServiceSpecialUseAllowed
        }.stateIn(appScope, SharingStarted.Eagerly, false)
    }

    val notification by lazy {
        requestablePermissionState(
            name = "Notification permission",
            purpose = "Used to show background service status and necessary notifications",
            permission = PermissionLists.getPostNotificationsPermission(),
        )
    }

    val localNetwork by lazy {
        requestablePermissionState(
            name = "Local network access permission",
            purpose = "Used to connect to the privileged service via wireless debugging, and to allow devices on the local network to access the HTTP service",
            permission = PermissionLists.getAccessLocalNetworkPermission(),
        )
    }

    val queryPackages by lazy {
        requestablePermissionState(
            name = "Read app list permission",
            purpose = "Used to display device apps and match app rules",
            permission = PermissionLists.getGetInstalledAppsPermission(),
            onChanged = {
                if (!updateAppMutex.mutex.isLocked) {
                    updateAllAppInfo()
                }
            },
        )
    }

    val drawOverlays by lazy {
        requestablePermissionState(
            name = "Overlay window permission",
            purpose = "Used to show floating content such as the snapshot button, screen info, and event hints",
            permission = PermissionLists.getSystemAlertWindowPermission(),
            check = {
                // https://developer.android.com/security/fraud-prevention/activities?hl=zh-cn#hide_overlay_windows
                Settings.canDrawOverlays(app)
            },
        )
    }

    val writeExternalStorage by lazy {
        requestablePermissionState(
            name = "Write external storage permission",
            purpose = "Used to save screenshots or files to public storage on Android 9 and below",
            permission = PermissionLists.getWriteExternalStoragePermission(),
            check = {
                if (AndroidTarget.Q) {
                    true
                } else {
                    app.checkGrantedPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            },
        )
    }

    val ignoreBatteryOptimizations by lazy {
        requestablePermissionState(
            name = "Ignore battery optimizations permission",
            purpose = "Used to reduce the chance of the background service being suspended or killed by the system",
            permission = PermissionLists.getRequestIgnoreBatteryOptimizationsPermission(),
            check = {
                app.powerManager.isIgnoringBatteryOptimizations(app.packageName)
            },
        )
    }

    val writeSecureSettings by lazy {
        PermissionState(
            name = "Write secure settings permission",
            check = { app.checkGrantedPermission(Manifest.permission.WRITE_SECURE_SETTINGS) },
        )
    }

    val privilegeGranted by lazy {
        PermissionState(
            name = "Privileged service",
            check = {
                privilegeContextFlow.value != null && Privilege.pingServer()
            },
        )
    }

    val all by lazy {
        listOf(
            notification,
            localNetwork,
            foregroundServiceSpecialUse,
            appOpsAllowed,
            drawOverlays,
            writeExternalStorage,
            ignoreBatteryOptimizations,
            writeSecureSettings,
            queryPackages,
            privilegeGranted,
        )
    }

    fun refreshAll() {
        all.forEach {
            it.refresh()
        }
    }
}
