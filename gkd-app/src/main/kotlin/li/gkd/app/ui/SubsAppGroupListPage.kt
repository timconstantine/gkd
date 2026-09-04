package li.gkd.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.ui.component.AddRuleEntryDialog
import li.gkd.app.ui.component.AnimationFloatingActionButton
import li.gkd.app.ui.component.BatchActionButtonGroup
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.RuleGroupCard
import li.gkd.app.ui.component.SubscriptionPageContent
import li.gkd.app.ui.component.TowLineText
import li.gkd.app.ui.component.animateListItem
import li.gkd.app.ui.component.rememberMultiSelectionState
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.icon.BackCloseIcon
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.Loadable
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.copyText
import li.gkd.app.util.getUpDownTransform
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data class SubsAppGroupListRoute(
    val subsItemId: Long,
    val appId: String,
    val focusGroupKey: Int? = null, // Briefly highlight the background/border
) : NavKey

@Composable
fun SubsAppGroupListPage(route: SubsAppGroupListRoute) {
    val subsItemId = route.subsItemId
    val appId = route.appId
    val focusGroupKey = route.focusGroupKey

    val mainVm = LocalMainViewModel.current
    val vm = viewModel { SubsAppGroupListVm(route) }
    val scope = vm.scope
    val focusGroup = vm.focusGroupFlow?.collectAsStateWithLifecycle()?.value

    SubscriptionPageContent(vm.uiState) { state ->
        val subs = state.subscription
        val configs = state.configs.value
        val subsConfigs = configs?.subsConfigs.orEmpty()
        val categoryConfigs = configs?.categoryConfigs.orEmpty()
        val switchEnabled = state.configs is Loadable.Ready
        val app = state.app
        val editable = subsItemId < 0
        var showAddRuleDialog by remember { mutableStateOf(false) }
        val selectionState = rememberMultiSelectionState<Int>()
        val selectedKeys = selectionState.selectedKeys
        val isSelectedMode = selectionState.active
        LaunchedEffect(app.groups) {
            selectionState.retain(app.groups.mapTo(mutableSetOf()) { it.key })
        }
        BackHandler(isSelectedMode) {
            selectionState.clear()
        }
        val updateSelected: (Boolean?) -> Unit = { enabled ->
            scope.launchTry {
                val action = when (enabled) {
                    false -> "Disable"
                    true -> "Enable"
                    null -> "Reset to default"
                }
                if (!mainVm.dialogRequests.confirm(
                    title = "Action notice",
                    text = "Apply \"$action\" to all selected rules?\n\nNote: this can also be done under \"Subscription - Rule categories\"",
                )) return@launchTry
                val changedSize = vm.updateSelectedEnabled(selectedKeys, enabled)
                if (changedSize > 0) {
                    val result = if (enabled == null) "Reset" else if (enabled) "Enabled" else "Disabled"
                    toast("$result $changedSize rule(s)")
                } else {
                    toast(if (enabled == null) "No rules to reset" else "No rules were changed")
                }
            }
        }
        val pageScrollState = rememberListScrollState()
        val scrollBehavior = pageScrollState.scrollBehavior
        val listState = pageScrollState.listState
        pageScrollState.ResetOnChange(app.groups.isEmpty())
        if (focusGroupKey != null) {
            LaunchedEffect(null) {
                if (focusGroup != null) {
                    val i = app.groups.indexOfFirst { it.key == focusGroupKey }
                    if (i >= 0) {
                        listState.scrollToItem(i)
                    }
                }
            }
        }
        Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
            PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
                IconButton(onClick = throttle {
                    if (isSelectedMode) {
                        selectionState.clear()
                    } else {
                        mainVm.popPage()
                    }
                }) {
                    BackCloseIcon(backOrClose = !isSelectedMode)
                }
            }, title = {
                val titleModifier = Modifier.noRippleClickable(onClick = pageScrollState::resetScroll)
                if (isSelectedMode) {
                    Text(
                        modifier = titleModifier,
                        text = selectedKeys.size.toString(),
                    )
                } else {
                    TowLineText(
                        modifier = titleModifier,
                        title = subs.name,
                        subtitle = appId,
                        showApp = true,
                        appFallbackName = app.name,
                    )
                }
            }, actions = {
                var expanded by remember { mutableStateOf(false) }
                AnimatedContent(
                    targetState = isSelectedMode,
                    transitionSpec = { getUpDownTransform() },
                    contentAlignment = Alignment.TopEnd,
                ) {
                    if (it) {
                        Row {
                            PerfIconButton(
                                imageVector = PerfIcon.ContentCopy,
                                onClick = throttle {
                                    scope.launchTry {
                                        copyText(vm.buildSelectedGroupsText(selectedKeys))
                                    }
                                },
                            )
                            BatchActionButtonGroup(
                                onDisable = { updateSelected(false) },
                                onEnable = { updateSelected(true) },
                                onReset = { updateSelected(null) },
                            )
                            if (editable) {
                                PerfIconButton(
                                    imageVector = PerfIcon.Delete,
                                    onClick = throttle {
                                        val keysToDelete = selectedKeys
                                        scope.launchTry {
                                            if (!mainVm.dialogRequests.confirm(
                                                title = "Delete rule",
                                                text = "Delete the currently selected rules?",
                                                error = true,
                                            )) return@launchTry
                                            val deletedSize = vm.deleteSelectedGroups(keysToDelete)
                                            selectionState.clear()
                                            toast(
                                                if (deletedSize > 0) {
                                                    "Deleted successfully"
                                                } else {
                                                    "The selected rules have changed"
                                                }
                                            )
                                        }
                                    },
                                )
                            }
                            PerfIconButton(imageVector = PerfIcon.MoreVert, onClick = {
                                expanded = true
                            })
                        }
                    }
                }
                if (isSelectedMode) {
                    Box(
                        modifier = Modifier
                            .wrapContentSize(Alignment.TopStart)
                    ) {
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(text = "Select all")
                                },
                                onClick = {
                                    expanded = false
                                    selectionState.selectAll(app.groups.map { it.key })
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(text = "Invert selection")
                                },
                                onClick = {
                                    expanded = false
                                    selectionState.invert(app.groups.map { it.key })
                                }
                            )
                        }
                    }
                }
            })
        }, floatingActionButton = {
            if (editable) {
                AnimationFloatingActionButton(
                    visible = !isSelectedMode,
                    onClick = { showAddRuleDialog = true },
                    contentDescription = "Add rule",
                    imageVector = PerfIcon.Add,
                )
            }
        }) { contentPadding ->
            LazyColumn(
                modifier = Modifier.scaffoldPadding(contentPadding),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(app.groups, { it.key }) { group ->
                    val category = subs.getCategory(group.name)
                    val subsConfig = subsConfigs.find { it.groupKey == group.key }
                    val categoryConfig = categoryConfigs.find {
                        it.categoryKey == category?.key
                    }
                    RuleGroupCard(
                        modifier = Modifier.animateListItem(),
                        subs = subs,
                        appId = appId,
                        group = group,
                        subsConfig = subsConfig,
                        categoryConfig = categoryConfig,
                        switchEnabled = switchEnabled,
                        onOpen = {
                            mainVm.showRuleGroup(
                                subscriptionId = subs.id,
                                appId = appId,
                                group = group,
                            )
                        },
                        onCheckedChange = { enabled ->
                            scope.launchTry {
                                vm.setGroupEnabled(group, subsConfig, enabled)
                            }
                        },
                        focusGroup = focusGroup,
                        onFocusHandled = vm::consumeFocusGroup,
                        isSelectedMode = isSelectedMode,
                        isSelected = group.key in selectedKeys,
                        onLongClick = {
                            if (app.groups.size > 1) {
                                selectionState.selectOnly(group.key)
                            }
                        },
                        onSelectedChange = {
                            selectionState.toggle(group.key)
                        }
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (app.groups.isEmpty()) {
                        EmptyText(text = "No rules yet")
                    }
                }
            }
        }

        if (showAddRuleDialog) {
            AddRuleEntryDialog(
                onDismissRequest = { showAddRuleDialog = false },
                onTypeManually = {
                    mainVm.navigatePage(
                        UpsertRuleGroupRoute(
                            subsId = subsItemId,
                            groupKey = null,
                            appId = appId,
                        )
                    )
                },
                onStartCapture = {
                    mainVm.navigatePage(CaptureWaitRoute(isGlobal = false, subsId = subsItemId))
                },
            )
        }
    }
}
