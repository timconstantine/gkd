package li.gkd.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.app.data.AppInfo
import li.gkd.app.permission.PermissionStates
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.AppConfigRoute
import li.gkd.app.ui.EditBlockAppListRoute
import li.gkd.app.ui.component.AnimatedIconButton
import li.gkd.app.ui.component.AnimationFloatingActionButton
import li.gkd.app.ui.component.AppBarTextField
import li.gkd.app.ui.component.AppIcon
import li.gkd.app.ui.component.AppNameText
import li.gkd.app.ui.component.EmptyText
import li.gkd.app.ui.component.MenuGroupCard
import li.gkd.app.ui.component.MenuItemCheckbox
import li.gkd.app.ui.component.MenuItemRadioButton
import li.gkd.app.ui.component.PerfCheckbox
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.QueryPkgAuthCard
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberListScrollState
import li.gkd.app.ui.share.ListPlaceholder
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.share.noRippleClickable
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.appItemPadding
import li.gkd.app.util.AppGroupOption
import li.gkd.app.util.AppSortOption
import li.gkd.app.util.findOption
import li.gkd.app.util.getUpDownTransform
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle

@Composable
fun useAppListPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity

    val vm = viewModel { AppListVm(mainVm) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val appInfos = state.appInfos
    val searchStr = state.searchText
    val ruleSummary = state.ruleSummary

    val globalDesc = if (ruleSummary.globalGroups.isNotEmpty()) {
        "${ruleSummary.globalGroups.size} global"
    } else {
        null
    }
    val showSearchBar = state.showSearchBar
    val refreshing = state.refreshing
    val pullToRefreshState = rememberPullToRefreshState()
    val editWhiteListMode = state.editWhiteListMode
    val pageScrollState = rememberListScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val listState = pageScrollState.listState
    LaunchedEffect(null) {
        listOf(
            PermissionStates.queryPackages.stateFlow,
            vm.appInfosFlow,
        ).forEach {
            launch {
                it.drop(1).collect {
                    pageScrollState.resetScroll()
                }
            }
        }
    }
    ResetPageScrollOnRequest(BottomNavItem.AppList, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.AppList,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            DisposableEffect(null) {
                onDispose {
                    vm.onLeaveScreen()
                }
            }
            PerfTopAppBar(scrollBehavior = scrollBehavior, title = {
                val firstShowSearchBar = remember { showSearchBar }
                if (showSearchBar) {
                    BackHandler {
                        if (!context.imeController.requestHide()) {
                            vm.closeSearch()
                        }
                    }
                    AppBarTextField(
                        value = searchStr,
                        onValueChange = vm::setSearchText,
                        hint = "Enter app name/ID",
                        modifier = if (firstShowSearchBar) Modifier else Modifier.autoFocus(),
                    )
                } else {
                    val titleModifier = Modifier
                        .noRippleClickable(
                            onClick = throttle {
                                pageScrollState.resetScroll()
                            }
                        )
                    if (editWhiteListMode) {
                        BackHandler(onBack = vm::closeEditWhiteListMode)
                    }
                    AnimatedContent(
                        targetState = editWhiteListMode,
                        transitionSpec = { getUpDownTransform() },
                    ) { localEditWhiteListMode ->
                        if (localEditWhiteListMode) {
                            Text(
                                modifier = titleModifier,
                                text = "App allowlist",
                            )
                        } else {
                            Text(
                                modifier = titleModifier,
                                text = BottomNavItem.AppList.label,
                            )
                        }
                    }
                }
            }, actions = {
                if (state.queryPackagesAbnormal) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                        PerfIconButton(
                            imageVector = PerfIcon.WarningAmber,
                            contentDescription = PermissionStates.queryPackages.name + " abnormal",
                            onClick = throttle(vm.scope.launchAsFn {
                                mainVm.dialogRequests.showMessage(
                                    title = "Abnormal permission",
                                    text = "Detected that \"${PermissionStates.queryPackages.name}\" is granted, but very few apps were actually retrieved. A fallback method was used, but it may be incomplete. Pull to refresh on the app list to try again; if that doesn't help, try revoking and re-granting the permission, or restarting the device"
                                )
                            }),
                        )
                    }
                }
                PerfIconButton(
                    imageVector = PerfIcon.Block,
                    contentDescription = "Toggle allowlist edit mode",
                    onClickLabel = if (editWhiteListMode) "Exit editing" else "Enter editing",
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = if (editWhiteListMode) {
                            CheckboxDefaults.colors().checkedBoxColor
                        } else {
                            LocalContentColor.current
                        }
                    ),
                    onClick = throttle(vm::toggleEditWhiteListMode),
                )
                AnimatedIconButton(
                    onClick = throttle(vm::toggleSearch),
                    id = R.drawable.ic_anim_search_close,
                    atEnd = showSearchBar,
                    contentDescription = if (showSearchBar) "Close search" else "Search the app list",
                )
                var expanded by remember { mutableStateOf(false) }
                PerfIconButton(
                    imageVector = PerfIcon.Sort,
                    contentDescription = "Sort/filter",
                    onClick = {
                        expanded = true
                    }
                )
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.TopStart)
                ) {
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                        ) {
                        MenuGroupCard(inTop = true, title = "Sort") {
                            AppSortOption.objects.forEach { option ->
                                MenuItemRadioButton(
                                    text = option.label,
                                    selected = AppSortOption.objects.findOption(store.appSort) == option,
                                    onClick = { vm.setSortType(option) },
                                )
                            }
                        }
                        MenuGroupCard(title = "Group by") {
                            AppGroupOption.normalObjects.forEach { option ->
                                val newValue = option.invert(store.appGroupType)
                                MenuItemCheckbox(
                                    enabled = newValue != 0,
                                    text = option.label,
                                    checked = option.include(store.appGroupType),
                                    onClick = { vm.setAppGroupType(newValue) },
                                )
                            }
                        }
                        MenuGroupCard(title = "Filter") {
                            MenuItemCheckbox(
                                text = "Allowlist",
                                checked = store.showBlockApp,
                                onClick = {
                                    vm.setShowBlockApp(!store.showBlockApp)
                                },
                            )
                        }
                    }
                }
            })
        },
        floatingActionButton = {
            AnimationFloatingActionButton(
                visible = editWhiteListMode,
                contentDescription = "Edit allowlist",
                onClick = {
                    mainVm.navigatePage(EditBlockAppListRoute)
                },
                imageVector = PerfIcon.Edit,
            )
        }
    ) { contentPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(contentPadding),
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState
            ) {
                if (!state.canQueryPackages) {
                    item(key = 1, contentType = 1) {
                        QueryPkgAuthCard()
                    }
                }
                items(appInfos, { it.id }) { appInfo ->
                    val desc = run {
                        if (editWhiteListMode) return@run null
                        val appGroups = ruleSummary.appIdToAllGroups[appInfo.id] ?: emptyList()
                        val appDesc = if (appGroups.isNotEmpty()) {
                            when (val disabledCount = appGroups.count { g -> !g.enable }) {
                                0 -> "${appGroups.size} rule group(s)"
                                appGroups.size -> "${appGroups.size} rule group(s)/${disabledCount} disabled"
                                else -> {
                                    "${appGroups.size} rule group(s)/${appGroups.size - disabledCount} enabled/${disabledCount} disabled"
                                }
                            }
                        } else {
                            null
                        }
                        if (globalDesc != null) {
                            if (appDesc != null) {
                                "$globalDesc/$appDesc"
                            } else {
                                globalDesc
                            }
                        } else {
                            appDesc
                        }
                    }
                    AppItemCard(
                        appInfo = appInfo,
                        desc = desc,
                        editWhiteListMode = editWhiteListMode,
                        inWhiteList = appInfo.id in state.whiteListAppIds,
                        onClick = {
                            if (editWhiteListMode) {
                                vm.toggleWhiteList(appInfo.id)
                            } else {
                                context.imeController.requestHide()
                                mainVm.navigatePage(AppConfigRoute(appInfo.id))
                            }
                        },
                    )
                }
                item(ListPlaceholder.KEY, ListPlaceholder.TYPE) {
                    Spacer(modifier = Modifier.height(EmptyHeight))
                    if (appInfos.isEmpty() && searchStr.isNotEmpty()) {
                        EmptyText(text = if (state.showAllApps) "No search results" else "No search results, or adjust the filter")
                        Spacer(modifier = Modifier.height(EmptyHeight / 2))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppItemCard(
    appInfo: AppInfo,
    desc: String?,
    editWhiteListMode: Boolean,
    inWhiteList: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = throttle(onClick))
            .clearAndSetSemantics {
                contentDescription = if (editWhiteListMode) {
                    appInfo.name
                } else {
                    "App: ${appInfo.name}, ${desc ?: appInfo.id}"
                }
                if (inWhiteList) {
                    stateDescription = "In the allowlist"
                } else if (editWhiteListMode) {
                    stateDescription = "Not in the allowlist"
                }
                onClick(
                    label = if (editWhiteListMode) if (inWhiteList) "Remove from the allowlist" else "Add to the allowlist" else "Open the rule summary page",
                    action = null
                )
            }
            .appItemPadding(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(appId = appInfo.id)
        Column(
            modifier = Modifier
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            AppNameText(appInfo = appInfo)
            Text(
                text = desc ?: appInfo.id,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
        if (editWhiteListMode) {
            PerfCheckbox(
                key = appInfo.id,
                checked = inWhiteList,
            )
        } else if (inWhiteList) {
            PerfIcon(
                modifier = Modifier
                    .padding(2.dp)
                    .size(20.dp),
                imageVector = PerfIcon.Block,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
