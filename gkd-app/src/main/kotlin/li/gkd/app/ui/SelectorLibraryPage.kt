package li.gkd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.ui.component.AppBarTextField
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.ListPlaceholder
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.copyText
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.db.SelectorLibraryItem

@Serializable
data object SelectorLibraryRoute : NavKey

@Composable
fun SelectorLibraryPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<SelectorLibraryVm>()
    val scope = vm.scope
    val loadableState by vm.uiState.collectAsStateWithLifecycle()
    val searchStr by vm.searchStrFlow.collectAsStateWithLifecycle()
    val items = loadableState.value.orEmpty()

    Scaffold(topBar = {
        PerfTopAppBar(
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = mainVm::popPage)
            },
            title = {
                AppBarTextField(
                    value = searchStr,
                    onValueChange = vm::setSearchStr,
                    hint = "Search saved selectors",
                )
            },
        )
    }) { contentPadding ->
        LazyColumn(modifier = Modifier.scaffoldPadding(contentPadding)) {
            items(items, { it.id }) { item ->
                SelectorLibraryCard(
                    modifier = Modifier.animateListItem(),
                    item = item,
                    onClick = throttle { copyText(item.selector) },
                    onDelete = throttle {
                        scope.launchTry {
                            if (!mainVm.dialogRequests.confirm(
                                    title = "Delete",
                                    text = "Delete this saved selector?",
                                    error = true,
                                )
                            ) return@launchTry
                            vm.delete(item)
                        }
                    },
                )
            }
            item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                Spacer(modifier = Modifier.height(EmptyHeight))
                if (items.isEmpty() && loadableState !is Loadable.Loading) {
                    EmptyText(
                        text = if (searchStr.isBlank()) {
                            "No saved selectors yet"
                        } else {
                            "No matches"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectorLibraryCard(
    modifier: Modifier = Modifier,
    item: SelectorLibraryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = itemHorizontalPadding, vertical = itemVerticalPadding / 2)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name ?: "(unnamed)",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.selector,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.appId != null) {
                    Text(
                        text = item.appId,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
            PerfIconButton(imageVector = PerfIcon.Delete, onClick = onDelete)
        }
    }
}
