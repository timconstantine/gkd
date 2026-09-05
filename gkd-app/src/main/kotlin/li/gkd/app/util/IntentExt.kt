package li.gkd.app.util

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri
import li.gkd.app.MainActivity
import li.gkd.app.META
import li.gkd.app.app
import li.gkd.app.isActivityVisible
import li.gkd.app.permission.PermissionStates
import kotlin.reflect.KClass

// Carries a snapshot id from a manual capture (floating button/volume key)
// through a fresh Intent to MainActivity, so [li.gkd.app.MainViewModel.handleIntent]
// can bring up the node inspector for it — see [openSnapshotInspector].
const val SNAPSHOT_INSPECTOR_ID_EXTRA = "snapshotInspectorId"

/**
 * Brings the app to the foreground (or starts it fresh) and opens the
 * snapshot node inspector for [snapshotId]. Used after a manually-triggered
 * capture (the floating button or the volume-key shortcut) so the user lands
 * straight on the element list instead of having to dig it up from the
 * snapshot history afterward.
 */
fun openSnapshotInspector(snapshotId: Long) {
    val intent = Intent(app, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        putExtra(SNAPSHOT_INSPECTOR_ID_EXTRA, snapshotId)
    }
    app.tryStartActivity(intent)
}

fun Context.tryStartActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        LogUtils.d("tryStartActivity", e)
        toast("Navigation failed\n" + (e.message ?: e.stackTraceToString()))
    }
}

fun openWeChatScaner() {
    val intent = app.packageManager.getLaunchIntentForPackage("com.tencent.mm")?.apply {
        putExtra("LauncherUI.From.Scaner.Shortcut", true)
    }
    if (intent == null) {
        toast("Please check whether WeChat is installed or disabled")
        return
    }
    app.tryStartActivity(intent)
}

fun openA11ySettings() {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    app.tryStartActivity(intent)
}

fun openAppDetailsSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:${app.packageName}".toUri()
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    app.tryStartActivity(intent)
}

fun openUri(uri: String) {
    val u = try {
        uri.toUri()
    } catch (e: Exception) {
        e.printStackTrace()
        toast("Invalid link")
        return
    }
    openUri(u)
}

fun openUri(uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    app.tryStartActivity(intent)
}

fun <T : Service> stopServiceByClass(clazz: KClass<T>) {
    val intent = Intent(app, clazz.java)
    app.stopService(intent)
}

fun <T : Service> startForegroundServiceByClass(clazz: KClass<T>) {
    if (!PermissionStates.notification.checkOrToast()) return
    if (!PermissionStates.foregroundServiceSpecialUse.checkOrToast()) return
    val intent = Intent(app, clazz.java)
    try {
        app.startForegroundService(intent)
    } catch (e: Throwable) {
        LogUtils.d(e)
        val prefix = if (isActivityVisible) "" else "${META.appName}: "
        toast("${prefix}Failed to start service: ${e.message}", forced = true)
    }
}

val Intent.extraCptName: ComponentName?
    get() = if (AndroidTarget.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_COMPONENT_NAME) as? ComponentName?
    }
