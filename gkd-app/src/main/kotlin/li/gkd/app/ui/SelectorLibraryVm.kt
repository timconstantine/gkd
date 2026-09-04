package li.gkd.app.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.db.Db
import li.gkd.db.SelectorLibraryItem

class SelectorLibraryVm : BaseViewModel() {
    private val itemsFlow = Db.selectorLibraryDao.query()

    val searchStrFlow = MutableStateFlow("")

    val uiState = combine(itemsFlow, searchStrFlow) { items, searchStr ->
        if (searchStr.isBlank()) {
            items
        } else {
            items.filter { it.matches(searchStr) }
        }
    }.stateLoadable()

    private fun SelectorLibraryItem.matches(searchStr: String): Boolean {
        return selector.contains(searchStr, ignoreCase = true) ||
            name?.contains(searchStr, ignoreCase = true) == true ||
            appId?.contains(searchStr, ignoreCase = true) == true
    }

    fun setSearchStr(value: String) {
        searchStrFlow.value = value
    }

    suspend fun delete(item: SelectorLibraryItem) {
        Db.selectorLibraryDao.delete(item)
    }
}
