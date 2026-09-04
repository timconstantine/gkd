package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.toast

class AdvancedVm : BaseViewModel() {

    val showEditPortDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)
    val httpSettingsDialogVisibleFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setEditPortDialogVisible(visible: Boolean) {
        showEditPortDialogFlow.value = visible
    }

    fun setHttpSettingsDialogVisible(visible: Boolean) {
        httpSettingsDialogVisibleFlow.value = visible
    }

    fun saveHttpServerPort(value: String): Boolean {
        val newPort = value.toIntOrNull()
        if (newPort == null || newPort !in 1000..65535) {
            toast("Enter an integer between 1000 and 65535")
            return false
        }
        if (newPort == storeFlow.value.httpServerPort) {
            return true
        }
        storeFlow.update { it.copy(httpServerPort = newPort) }
        toast("Updated successfully")
        return true
    }

    fun setAutoClearMemorySubs(enabled: Boolean) {
        storeFlow.update { it.copy(autoClearMemorySubs = enabled) }
    }
}
