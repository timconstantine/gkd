package li.gkd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.data.NodeInfo
import li.gkd.app.ui.component.AppBarTextField
import li.gkd.app.ui.component.AppDialog
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.copyText
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.db.LOCAL_SUBS_ID

@Serializable
data class SnapshotInspectorRoute(
    val snapshotId: Long,
    val isGlobal: Boolean = false,
    val subsId: Long = LOCAL_SUBS_ID,
) : NavKey

@Composable
fun SnapshotInspectorPage(route: SnapshotInspectorRoute) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { SnapshotInspectorVm(route.snapshotId) }
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val filter by vm.filterFlow.collectAsStateWithLifecycle()
    val selectedNode by vm.selectedNodeFlow.collectAsStateWithLifecycle()

    Scaffold(topBar = {
        PerfTopAppBar(
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = mainVm::popPage)
            },
            title = {
                AppBarTextField(
                    value = filter,
                    onValueChange = vm::updateFilter,
                    hint = "Search nodes by text/id/desc",
                )
            },
        )
    }) { contentPadding ->
        when (val current = loadableState) {
            Loadable.Loading -> Box(
                modifier = Modifier.scaffoldPadding(contentPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is Loadable.Failure -> Box(
                modifier = Modifier.scaffoldPadding(contentPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = current.cause.message ?: "Failed to load the snapshot",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is Loadable.Ready -> {
                val state = current.value
                val filteredNodes = remember(state.nodes, filter) {
                    if (filter.isBlank()) {
                        state.nodes
                    } else {
                        state.nodes.filter { it.matchesFilter(filter) }
                    }
                }
                LazyColumn(modifier = Modifier.scaffoldPadding(contentPadding).fillMaxSize()) {
                    item {
                        Text(
                            text = "Tap the element you want to build a rule for. The number before each row is how deeply it's nested.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = itemHorizontalPadding, vertical = 8.dp),
                        )
                    }
                    items(filteredNodes, key = { it.id }) { node ->
                        NodeRow(node = node, onClick = { vm.selectNode(node) })
                    }
                    item {
                        if (filteredNodes.isEmpty()) {
                            EmptyText(text = if (filter.isBlank()) "No nodes captured" else "No matches")
                        }
                    }
                }

                val currentSelectedNode = selectedNode
                if (currentSelectedNode != null) {
                    NodeDetailDialog(
                        vm = vm,
                        node = currentSelectedNode,
                        appId = state.appId,
                        activityId = state.activityId,
                        isGlobal = route.isGlobal,
                        subsId = route.subsId,
                        onDismissRequest = { vm.selectNode(null) },
                    )
                }
            }
        }
    }
}

private fun NodeInfo.matchesFilter(filter: String): Boolean {
    return attr.text?.contains(filter, ignoreCase = true) == true ||
        attr.desc?.contains(filter, ignoreCase = true) == true ||
        attr.id?.contains(filter, ignoreCase = true) == true ||
        attr.vid?.contains(filter, ignoreCase = true) == true ||
        attr.name?.contains(filter, ignoreCase = true) == true
}

private fun NodeInfo.shortLabel(): String {
    val attr = attr
    val text = attr.text?.takeIf { it.isNotBlank() }
    val desc = attr.desc?.takeIf { it.isNotBlank() }
    val vid = attr.vid?.takeIf { it.isNotBlank() }
    val name = attr.name?.takeIf { it.isNotBlank() }
    return text ?: desc ?: vid ?: name ?: "(node #$id)"
}

// Real screens can nest many levels deep, and indenting by the full depth
// pushes rows off the right edge of a phone screen — so the indent itself
// caps out, and the exact depth is always shown as a small number instead
// (which also stays readable once rows past the cap all line up together).
private val nodeIndentStep = 8.dp
private const val NODE_INDENT_MAX_DEPTH = 6

@Composable
private fun NodeRow(node: NodeInfo, onClick: () -> Unit) {
    val depth = node.attr.depth
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = itemHorizontalPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(nodeIndentStep * depth.coerceAtMost(NODE_INDENT_MAX_DEPTH)))
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = depth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = node.shortLabel(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = node.attr.name ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NodeDetailDialog(
    vm: SnapshotInspectorVm,
    node: NodeInfo,
    appId: String,
    activityId: String?,
    isGlobal: Boolean,
    subsId: Long,
    onDismissRequest: () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    val scope = vm.scope
    val selectorText by vm.selectorTextFlow.collectAsStateWithLifecycle()
    val selectorError by vm.selectorErrorFlow.collectAsStateWithLifecycle()
    val checkedAttrs by vm.checkedAttrsFlow.collectAsStateWithLifecycle()
    var showSaveNameDialog by remember { mutableStateOf(false) }

    AppDialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .heightIn(max = 620.dp),
            ) {
                Text(text = node.shortLabel(), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap attributes below to narrow the selector down to just this element (or ones like it).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.heightIn(max = 180.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    node.availableClauses().forEach { clause ->
                        FilterChip(
                            selected = checkedAttrs.contains(clause.attr),
                            onClick = { vm.toggleClause(clause) },
                            label = { Text(text = "${clause.attr}=${clause.value}") },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = selectorText,
                    onValueChange = vm::setSelectorText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Selector") },
                    isError = selectorError != null,
                    supportingText = selectorError?.let { { Text(text = it) } },
                )

                val selectorReady = selectorText.isNotBlank() && selectorError == null
                Spacer(modifier = Modifier.height(4.dp))
                DialogActionRow(
                    title = "Save to library",
                    description = "Store just this selector so you can reuse it in other rules later.",
                    enabled = selectorReady,
                    onClick = throttle { showSaveNameDialog = true },
                )
                HorizontalDivider()
                DialogActionRow(
                    title = "Copy",
                    description = "Copy the raw selector text to your clipboard.",
                    enabled = selectorReady,
                    onClick = throttle { copyText(selectorText) },
                )

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    enabled = selectorReady,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = throttle {
                        onDismissRequest()
                        mainVm.navigatePage(
                            RuleBuilderRoute(
                                subsId = subsId,
                                appId = appId,
                                activityId = activityId,
                                initialSelector = selectorText,
                                isGlobal = isGlobal,
                            ),
                        )
                    },
                ) { Text(text = "Create rule") }
                Text(
                    text = "Open the guided rule builder pre-filled with this selector — the recommended next step.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }

    if (showSaveNameDialog) {
        SaveToLibraryDialog(
            onDismissRequest = { showSaveNameDialog = false },
            onConfirm = { name ->
                scope.launchTry {
                    vm.saveToLibrary(name = name, appId = appId)
                    showSaveNameDialog = false
                }
            },
        )
    }
}

/**
 * A compact secondary-action row (title + one-sentence description) used for
 * the utility actions in [NodeDetailDialog] — kept visually distinct from the
 * primary "Create rule" button below them so it's clear which one to reach
 * for first.
 */
@Composable
private fun DialogActionRow(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.4f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
    }
}

@Composable
private fun SaveToLibraryDialog(
    onDismissRequest: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AppDialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Save to selector library", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = "Name (optional)") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismissRequest) { Text(text = "Cancel") }
                    TextButton(onClick = { onConfirm(name.ifBlank { null }) }) { Text(text = "Save") }
                }
            }
        }
    }
}
