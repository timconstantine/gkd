package li.gkd.app.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.MainViewModel
import li.gkd.app.notif.NotificationCatalog
import li.gkd.app.notif.StopServiceReceiver
import li.gkd.app.permission.PermissionStates
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.snapshot.SnapshotCapture
import li.gkd.app.util.launchTry
import li.gkd.app.util.startForegroundServiceByClass
import li.gkd.app.util.stopServiceByClass

class ButtonService : OverlayWindowService(
    positionKey = "button"
) {
    override val dragToDismissEnabled = true

    override fun onClickView() {
        if (isOverlayContentHidden) return
        scope.launchTry {
            withAllOverlaysHidden {
                SnapshotCapture.capture()
            }
            // A one-shot button: once it's done its job, get out of the way
            // instead of sitting on screen waiting for another tap.
            stopSelf()
        }
    }

    override fun onLongClickView() = stopSelf()

    @Composable
    override fun ComposeContent() {
        val alpha = 0.75f
        PerfIcon(
            imageVector = PerfIcon.CenterFocusWeak,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = alpha))
                .size(40.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        )
    }

    init {
        useAliveFlow(isRunning)
        // No useAliveToast here: this button is expected to start/stop
        // frequently (auto-hides after a capture, and can be dragged to the
        // trash to dismiss it), so a "stopped" toast on every one of those
        // would be more noise than signal.
        onCreated {
            NotificationCatalog.button().startForeground()
        }
        StopServiceReceiver.autoRegister()
    }

    companion object {
        val isRunning = MutableStateFlow(false)
        fun start() {
            if (!PermissionStates.drawOverlays.checkOrToast()) return
            startForegroundServiceByClass(ButtonService::class)
        }

        fun stop() = stopServiceByClass(ButtonService::class)

        suspend fun setEnabled(mainVm: MainViewModel, enabled: Boolean) {
            if (!enabled) {
                stop()
                return
            }
            if (!mainVm.permissionRequests.ensurePermissions(
                    PermissionStates.foregroundServiceSpecialUse,
                    PermissionStates.notification,
                    PermissionStates.drawOverlays,
                )
            ) return
            start()
        }
    }
}
