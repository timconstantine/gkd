package li.gkd.app.ui

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import li.gkd.app.MainViewModel
import li.gkd.app.service.ButtonService
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.CaptureTriggerOption
import li.gkd.app.util.findOption
import li.gkd.db.Db
import li.gkd.db.Snapshot

/**
 * Whether [CaptureWaitPage] is currently alive and watching for a snapshot —
 * checked by [li.gkd.app.service.ButtonService] and the volume-key capture
 * so they don't *also* auto-navigate to the inspector when this page's own
 * (correctly-scoped, isGlobal/subsId-aware) navigation is about to fire for
 * the same snapshot.
 */
object CaptureWaitState {
    val isActive = MutableStateFlow(false)
}

class CaptureWaitVm : BaseViewModel() {
    init {
        CaptureWaitState.isActive.value = true
    }

    override fun onCleared() {
        super.onCleared()
        CaptureWaitState.isActive.value = false
    }

    // Only snapshots captured after the wait started count as "the" capture —
    // otherwise an old one already in the list would immediately match.
    private val baselineId = System.currentTimeMillis()

    val newSnapshotFlow = Db.snapshotDao.query()
        .map { list -> list.filter { it.id > baselineId }.maxByOrNull(Snapshot::id) }
        .stateInit(null)

    // Whether the trigger was already enabled before this page armed it —
    // starts true (assume "leave it alone") so disarm() never touches
    // anything unless arm() proves it was this page that turned it on.
    private var triggerWasAlreadyOn = true

    suspend fun armDefaultTrigger(mainVm: MainViewModel) {
        when (CaptureTriggerOption.objects.findOption(storeFlow.value.defaultCaptureTrigger)) {
            CaptureTriggerOption.FloatingButton -> {
                triggerWasAlreadyOn = ButtonService.isRunning.value
                if (!triggerWasAlreadyOn) {
                    ButtonService.setEnabled(mainVm, true)
                }
            }

            CaptureTriggerOption.VolumeKey -> {
                triggerWasAlreadyOn = storeFlow.value.captureVolumeChange
                if (!triggerWasAlreadyOn) {
                    storeFlow.update { it.copy(captureVolumeChange = true) }
                }
            }
        }
    }

    suspend fun disarmIfWeArmedIt(mainVm: MainViewModel) {
        if (triggerWasAlreadyOn) return
        when (CaptureTriggerOption.objects.findOption(storeFlow.value.defaultCaptureTrigger)) {
            CaptureTriggerOption.FloatingButton -> ButtonService.setEnabled(mainVm, false)
            CaptureTriggerOption.VolumeKey -> storeFlow.update { it.copy(captureVolumeChange = false) }
        }
        triggerWasAlreadyOn = true
    }
}
