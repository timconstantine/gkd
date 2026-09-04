package li.gkd.app.ui

import kotlinx.coroutines.flow.update
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.appInfoMapFlow
import li.gkd.app.util.toast
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult

class SnapshotSettingsVm : BaseViewModel() {
    fun saveCaptureScreenshotConfig(
        appId: String,
        eventSelector: String,
    ): Boolean {
        val store = storeFlow.value
        if (
            appId == store.screenshotTargetAppId &&
            eventSelector == store.screenshotEventSelector
        ) {
            return true
        }
        if (appId.isNotEmpty() && !appInfoMapFlow.value.contains(appId)) {
            toast("Invalid app ID")
            return false
        }
        if (
            eventSelector.isNotEmpty() &&
            Selector.compile(eventSelector) is SelectorCompileResult.Failure
        ) {
            toast("Invalid event selector")
            return false
        }
        storeFlow.update {
            it.copy(
                screenshotTargetAppId = appId,
                screenshotEventSelector = eventSelector,
            )
        }
        toast("Updated successfully")
        return true
    }

    fun setCaptureVolumeChange(enabled: Boolean) {
        storeFlow.update { it.copy(captureVolumeChange = enabled) }
    }

    fun setCaptureScreenshot(enabled: Boolean) {
        val store = storeFlow.value
        storeFlow.update { it.copy(captureScreenshot = enabled) }
        if (
            enabled && (
                store.screenshotTargetAppId.isEmpty() ||
                    store.screenshotEventSelector.isEmpty()
            )
        ) {
            toast("Please configure the target app and feature event selector")
        }
    }

    fun setHideSnapshotStatusBar(enabled: Boolean) {
        storeFlow.update { it.copy(hideSnapshotStatusBar = enabled) }
    }

    fun setAutoSaveSnapshotToDownloads(enabled: Boolean) {
        storeFlow.update { it.copy(autoSaveSnapshotToDownloads = enabled) }
    }
}
