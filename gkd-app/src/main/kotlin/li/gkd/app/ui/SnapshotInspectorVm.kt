package li.gkd.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import li.gkd.app.data.ComplexSnapshot
import li.gkd.app.data.NodeInfo
import li.gkd.app.data.SelectorClause
import li.gkd.app.data.availableClauses
import li.gkd.app.data.buildSelectorExpression
import li.gkd.app.data.validateSelectorExpression
import li.gkd.app.snapshot.SnapshotStore
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.app.util.json
import li.gkd.db.Db
import li.gkd.db.SelectorLibraryItem

data class SnapshotInspectorUiState(
    val appId: String,
    val activityId: String?,
    val nodes: List<NodeInfo>,
)

/**
 * The action types a node can be filtered by in the inspector's node list.
 * Each maps to a per-node capability flag on [li.gkd.app.data.AttrInfo] —
 * [li.gkd.app.data.AttrInfo.clickable], [li.gkd.app.data.AttrInfo.longClickable],
 * [li.gkd.app.data.AttrInfo.editable] (for the `setText` action) — since those
 * are the only [li.gkd.app.data.GkdAction] variants that require a specific
 * per-node capability; the rest either act on raw coordinates (clickCenter/
 * longClickCenter/swipe) or don't target a node at all (back/none), so they
 * have nothing node-specific to filter by.
 */
enum class NodeActionTypeFilter(val label: String) {
    CLICKABLE("Clickable"),
    LONG_CLICKABLE("Long-clickable"),
    EDITABLE("Enter text"),
}

fun NodeInfo.matchesActionType(type: NodeActionTypeFilter): Boolean = when (type) {
    NodeActionTypeFilter.CLICKABLE -> attr.clickable
    NodeActionTypeFilter.LONG_CLICKABLE -> attr.longClickable
    NodeActionTypeFilter.EDITABLE -> attr.editable
}

class SnapshotInspectorVm(private val snapshotId: Long) : BaseViewModel() {
    val uiState = flow {
        emit(loadSnapshot())
    }.stateLoadable()

    val filterFlow = MutableStateFlow("")

    // Empty means "no action-type filter" (show every node); otherwise a
    // node is shown if it matches ANY selected type.
    val actionTypeFilterFlow = MutableStateFlow<Set<NodeActionTypeFilter>>(emptySet())

    val selectedNodeFlow = MutableStateFlow<NodeInfo?>(null)

    // Attribute clauses the user has tapped on for the selected node.
    private val clausesFlow = MutableStateFlow<List<SelectorClause>>(emptyList())

    // Set when the user hand-edits the selector text directly, overriding the
    // tap-built expression until a new node/attribute selection resets it.
    private val manualTextFlow = MutableStateFlow<String?>(null)

    val selectorTextFlow = combine(clausesFlow, manualTextFlow) { clauses, manual ->
        manual ?: buildSelectorExpression(clauses)
    }.stateInit("")

    val selectorErrorFlow = selectorTextFlow.mapNew(::validateSelectorExpression)

    val checkedAttrsFlow = clausesFlow.mapNew { clauses -> clauses.map { it.attr }.toSet() }

    private suspend fun loadSnapshot(): SnapshotInspectorUiState {
        val text = withContext(Dispatchers.IO) { SnapshotStore.snapshotFile(snapshotId).readText() }
        val snapshot = withContext(Dispatchers.Default) {
            json.decodeFromString<ComplexSnapshot>(text)
        }
        return SnapshotInspectorUiState(
            appId = snapshot.appId,
            activityId = snapshot.activityId,
            nodes = snapshot.nodes,
        )
    }

    fun updateFilter(value: String) {
        filterFlow.value = value
    }

    fun toggleActionTypeFilter(type: NodeActionTypeFilter) {
        val current = actionTypeFilterFlow.value
        actionTypeFilterFlow.value = if (current.contains(type)) {
            current - type
        } else {
            current + type
        }
    }

    fun selectNode(node: NodeInfo?) {
        selectedNodeFlow.value = node
        clausesFlow.value = emptyList()
        manualTextFlow.value = null
    }

    fun toggleClause(clause: SelectorClause) {
        manualTextFlow.value = null
        val current = clausesFlow.value
        clausesFlow.value = if (current.any { it.attr == clause.attr }) {
            current.filterNot { it.attr == clause.attr }
        } else {
            current + clause
        }
    }

    fun setSelectorText(text: String) {
        manualTextFlow.value = text
    }

    suspend fun saveToLibrary(name: String?, appId: String?) {
        val selector = selectorTextFlow.value
        if (selector.isBlank()) return
        Db.selectorLibraryDao.insert(
            SelectorLibraryItem(
                selector = selector,
                name = name?.trim()?.ifBlank { null },
                appId = appId,
            ),
        )
    }
}

fun NodeInfo.availableClauses(): List<SelectorClause> = attr.availableClauses()
