package li.gkd.app.ui.home

import android.view.KeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import li.gkd.app.MainActivity
import li.gkd.app.R
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.service.StatusService
import li.gkd.app.service.TrackService
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.AboutRoute
import li.gkd.app.ui.AdvancedPageRoute
import li.gkd.app.ui.BlockA11yAppListRoute
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.component.CustomOutlinedTextField
import li.gkd.app.ui.component.FullscreenDialog
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.PerfCustomIconButton
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.SettingItem
import li.gkd.app.ui.component.SettingsDialog
import li.gkd.app.ui.component.TextListDialog
import li.gkd.app.ui.component.TextMenu
import li.gkd.app.ui.component.TextSwitch
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.component.rememberColumnScrollState
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.iconTextSize
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.DarkThemeOption
import li.gkd.app.util.findOption
import li.gkd.app.util.launchTry
import li.gkd.app.util.openAppDetailsSettings
import li.gkd.app.util.throttle
import li.gkd.app.util.toast
import kotlin.time.Duration.Companion.milliseconds

private const val ZIP_MIME_TYPE = "application/zip"

@Composable
fun useSettingsPage(): ScaffoldExt {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<SettingsVm>()
    val subsStatus by vm.subsStatusFlow.collectAsStateWithLifecycle()
    val trackServiceRunning by TrackService.isRunning.collectAsStateWithLifecycle()
    val privilegeAvailable = privilegeContextFlow.collectAsStateWithLifecycle().value != null
    val store by storeFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope
    val showToastInputDlg by vm.showActionToastDialogFlow.collectAsStateWithLifecycle()
    val showNotifTextInputDlg by vm.showNotificationTextDialogFlow.collectAsStateWithLifecycle()
    val showA11yBlockDlg by vm.showA11yBlockDialogFlow.collectAsStateWithLifecycle()
    val showBackupDialog by vm.showBackupDialogFlow.collectAsStateWithLifecycle()
    val showExportBackupDialog by vm.showExportBackupDialogFlow.collectAsStateWithLifecycle()
    val showToastSettingsDialog by vm.toastSettingsDialogVisibleFlow.collectAsStateWithLifecycle()

    if (showToastSettingsDialog) {
        SettingsDialog(
            title = "Toast settings",
            onDismissRequest = { vm.setToastSettingsDialogVisible(false) },
        ) {
            TextSwitch(
                title = "Toast style",
                subtitle = "Use the system style",
                suffix = "View limitations",
                onSuffixClick = {
                    actionScope.launchTry {
                        mainVm.dialogRequests.showMessage(
                            title = "About the limitation",
                            text = "The system Toast has a rate limit; triggering it too often will cause the system to force it not to show\n\nIf you only use low-frequency rules like app-open triggers, the system toast is fine — otherwise it's recommended to turn this off and use the custom-style toast",
                        )
                    }
                },
                checked = store.useSystemToast,
                onCheckedChange = vm::setUseSystemToast,
            )
            TextSwitch(
                title = "Trace hint",
                subtitle = "Show the trigger location info",
                checked = trackServiceRunning,
                onCheckedChange = { enabled ->
                    actionScope.launchTry {
                        if (enabled) {
                            if (!mainVm.dialogRequests.confirm(
                                title = "Before you enable this",
                                text = "After enabling \"trace hint\", a tap or swipe will draw a trace on screen using an overlay window (which disappears after a while). If a new touch event happens to land within the overlay window's area, the target app may reject it, causing the tap or swipe to not respond",
                                confirmText = "Continue",
                            )) return@launchTry
                            if (
                                !mainVm.permissionRequests.ensurePermissions(
                                    PermissionStates.foregroundServiceSpecialUse,
                                    PermissionStates.notification,
                                    PermissionStates.drawOverlays,
                                )
                            ) {
                                return@launchTry
                            }
                        }
                        vm.setTrackServiceEnabled(enabled)
                    }
                },
            )
        }
    }

    if (showToastInputDlg) {
        var value by remember {
            mutableStateOf(store.actionToast)
        }
        val maxCharLen = 64
        AppAlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Trigger toast")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "Text rules",
                        onClickLabel = "Open the text rules dialog",
                        onClick = throttle {
                            actionScope.launchTry {
                                mainVm.dialogRequests.showMessage(
                                    title = "Text rules",
                                    text = $$"The trigger text supports variable substitution, with these rules\n${1} sub-rule name\n${2} rule name\n${3} trigger count\n\nExample template\n${1}/${2}/${3}\n\nSubstituted result\nSub-rule a/Rule A/3",
                                )
                            }
                        },
                    )
                }
            },
            text = {
                OutlinedTextField(
                    value = value,
                    placeholder = {
                        Text(text = "Enter the toast content")
                    },
                    onValueChange = {
                        value = it.take(maxCharLen)
                    },
                    supportingText = {
                        Text(
                            text = "${value.length} / $maxCharLen",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus()
                )
            },
            onDismissRequest = { vm.setActionToastDialogVisible(false) },
            confirmButton = {
                TextButton(enabled = value.isNotEmpty(), onClick = {
                    if (vm.saveActionToast(value)) {
                        toast("Updated successfully")
                    }
                    vm.setActionToastDialogVisible(false)
                }) {
                    Text(text = "Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setActionToastDialogVisible(false) }) {
                    Text(text = "Cancel")
                }
            }
        )
    }

    if (showNotifTextInputDlg) {
        var titleValue by remember { mutableStateOf(store.customNotifTitle) }
        var textValue by remember { mutableStateOf(store.customNotifText) }
        AppAlertDialog(
            properties = DialogProperties(dismissOnClickOutside = false),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = "Notification text")
                    PerfIconButton(
                        imageVector = PerfIcon.HelpOutline,
                        contentDescription = "Text rules",
                        onClickLabel = "Open the text rules dialog",
                        onClick = throttle {
                            actionScope.launchTry {
                                mainVm.dialogRequests.showMessage(
                                    title = "Text rules",
                                    text = $$"The notification text supports variable substitution, with these rules\n${i} global rule count\n${k} app count\n${u} app rule count\n${n} trigger count\n\nExample template\n${i} global/${k} apps/${u} rules/${n} triggered\n\nSubstituted result\n0 global/1 apps/2 rules/3 triggered",
                                )
                            }
                        },
                    )
                }
            },
            text = {
                val titleMaxLen = 32
                val textMaxLen = 64
                Column(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CustomOutlinedTextField(
                        label = { Text("Title") },
                        value = titleValue,
                        placeholder = { Text(text = "Enter the content; variable substitution is supported") },
                        onValueChange = {
                            titleValue = (if (it.length > titleMaxLen) it.take(titleMaxLen) else it)
                                .filter { c -> c !in "\n\r" }
                        },
                        supportingText = {
                            Text(
                                text = "${titleValue.length} / $titleMaxLen",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    CustomOutlinedTextField(
                        label = { Text("Subtitle") },
                        value = textValue,
                        placeholder = { Text(text = "Enter the content; variable substitution is supported") },
                        onValueChange = {
                            textValue = if (it.length > textMaxLen) it.take(textMaxLen) else it
                        },
                        supportingText = {
                            Text(
                                text = "${textValue.length} / $textMaxLen",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                            )
                        },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                        contentPadding = PaddingValues(12.dp),
                    )
                }
            },
            onDismissRequest = {
                vm.setNotificationTextDialogVisible(false)
            },
            confirmButton = {
                TextButton(onClick = {
                    context.imeController.requestHide()
                    if (vm.saveNotificationText(titleValue, textValue)) {
                        toast("Updated successfully")
                    }
                    vm.setNotificationTextDialogVisible(false)
                }) {
                    Text(
                        text = "Confirm",
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.setNotificationTextDialogVisible(false) }) {
                    Text(
                        text = "Cancel",
                    )
                }
            })
    }


    if (showA11yBlockDlg) {
        BlockA11yDialog(
            onDismissRequest = { vm.setA11yBlockDialogVisible(false) },
        )
    }
    if (showBackupDialog) {
        TextListDialog(
            onDismiss = { vm.setBackupDialogVisible(false) },
            textList = listOf(
                "Import backup" to {
                    actionScope.launchTry {
                        val uri = mainVm.activityResults.openDocument(ZIP_MIME_TYPE)
                        if (uri == null) {
                            toast("No file selected")
                            return@launchTry
                        }
                        vm.importBackup(uri)
                    }
                },
                "Export backup" to {
                    vm.setExportBackupDialogVisible(true)
                },
            )
        )
    }
    if (showExportBackupDialog) {
        TextListDialog(
            onDismiss = { vm.setExportBackupDialogVisible(false) },
            textList = listOf(
                "Share to another app" to {
                    actionScope.launchTry {
                        val file = vm.exportBackup()
                        context.shareFile(file, "Share the backup file")
                    }
                },
                "Save to Downloads" to {
                    actionScope.launchTry {
                        val file = vm.exportBackup()
                        context.saveFileToDownloads(file)
                    }
                },
            )
        )
    }

    val pageScrollState = rememberColumnScrollState()
    val scrollBehavior = pageScrollState.scrollBehavior
    val scrollState = pageScrollState.scrollState
    ResetPageScrollOnRequest(BottomNavItem.Settings, pageScrollState::resetScrollAndAwait)
    return ScaffoldExt(
        navItem = BottomNavItem.Settings,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = BottomNavItem.Settings.label,
                    )
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(contentPadding)
        ) {

            Text(
                text = "General",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextSwitch(
                title = "Trigger toast",
                subtitle = store.actionToast,
                checked = store.toastWhenClick,
                onClickLabel = "Open the trigger toast dialog",
                onClick = {
                    vm.setActionToastDialogVisible(true)
                },
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "Open the toast settings dialog",
                        onClick = { vm.setToastSettingsDialogVisible(true) },
                        id = R.drawable.ic_page_info,
                        contentDescription = "Toast settings",
                        tint = if (showToastSettingsDialog) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                },
                onCheckedChange = {
                    vm.setToastWhenClick(it)
                })

            TextSwitch(
                title = "Notification text",
                subtitle = if (store.useCustomNotifText) {
                    store.customNotifTitle + " / " + store.customNotifText
                } else {
                    subsStatus
                },
                checked = store.useCustomNotifText,
                onClickLabel = "Open the edit notification text dialog",
                onClick = { vm.setNotificationTextDialogVisible(true) },
                onCheckedChange = {
                    vm.setUseCustomNotificationText(it)
                })

            TextSwitch(
                title = "Hide from Recents",
                subtitle = "Hide the card in \"Recent tasks\"",
                checked = store.excludeFromRecents,
                onCheckedChange = { enabled ->
                    actionScope.launchTry {
                        if (enabled) {
                            if (!mainVm.dialogRequests.confirm(
                                title = "Hide from Recents",
                                text = "Hiding the card may prevent some devices from locking the task card in the background; it's recommended to lock it first before hiding. If it's already locked, or there's no lock-background mechanism, please continue",
                                confirmText = "Continue",
                            )) return@launchTry
                        }
                        vm.setExcludeFromRecents(enabled)
                    }
                })

            var blockSectionVisible by remember {
                mutableStateOf(store.enableBlockA11yAppList)
            }
            LaunchedEffect(store.enableBlockA11yAppList) {
                delay(300.milliseconds)
                blockSectionVisible = store.enableBlockA11yAppList
            }
            AnimatedVisibility(visible = blockSectionVisible) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .titleItemPadding(),
                    text = "Accessibility",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextSwitch(
                title = "Partial disable",
                subtitle = "Disable the service for allowlisted apps",
                checked = store.enableBlockA11yAppList && privilegeAvailable,
                onCheckedChange = {
                    if (it && !privilegeAvailable) {
                        mainVm.navigatePage(PrivilegeServiceRoute)
                    } else if (it) {
                        vm.setA11yBlockDialogVisible(true)
                    } else {
                        vm.setBlockA11yAppListEnabled(false)
                    }
                },
            )
            AnimatedVisibility(visible = blockSectionVisible) {
                SettingItem(title = "Allowlist", onClickLabel = "Open the accessibility allowlist page", onClick = {
                    mainVm.navigatePage(BlockA11yAppListRoute)
                })
            }

            Text(
                text = "Appearance",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            TextMenu(
                title = "Dark mode",
                option = DarkThemeOption.objects.findOption(store.enableDarkTheme),
                onOptionChange = {
                    vm.setDarkTheme(it.value)
                }
            )

            if (AndroidTarget.S) {
                TextSwitch(
                    title = "Dynamic color",
                    checked = store.enableDynamicColor,
                    onCheckedChange = {
                        vm.setDynamicColor(it)
                    }
                )
            }

            Text(
                text = "Other",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SettingItem(title = "Advanced settings", onClick = {
                mainVm.navigatePage(AdvancedPageRoute)
            })
            SettingItem(title = "Backup & restore", onClick = {
                vm.setBackupDialogVisible(true)
            })

            SettingItem(title = "About", onClick = {
                mainVm.navigatePage(AboutRoute)
            })

            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun BlockA11yDialog(
    onDismissRequest: () -> Unit,
) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<SettingsVm>()
    val statusRunning by StatusService.isRunning.collectAsStateWithLifecycle()
    val privilegeContext by privilegeContextFlow.collectAsStateWithLifecycle()
    val ignoreBatteryOptimizations by PermissionStates.ignoreBatteryOptimizations.stateFlow.collectAsStateWithLifecycle()
    val actionScope = vm.scope
    val scrollState = rememberScrollState()
    FullscreenDialog(onDismissRequest) {
        Scaffold(
            topBar = {
                PerfTopAppBar(
                    navigationIcon = {
                        PerfIconButton(
                            imageVector = PerfIcon.Close,
                            onClickLabel = "Close the dialog",
                            onClick = onDismissRequest,
                        )
                    },
                    title = {
                        Text(text = "Partial disable")
                    },
                )
            },
            bottomBar = {
                BottomAppBar {
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        enabled = privilegeContext != null && statusRunning && ignoreBatteryOptimizations,
                        onClick = {
                            actionScope.launchTry {
                                onDismissRequest()
                                delay(200.milliseconds)
                                vm.setBlockA11yAppListEnabled(true)
                            }
                        }
                    ) {
                        Text(text = "Continue")
                    }
                    Spacer(modifier = Modifier.width(itemHorizontalPadding))
                }
            },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(horizontal = itemHorizontalPadding)
            ) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                    Text(text = "\"Partial disable\" can turn off the service for allowlisted apps, to work around display glitches, game frame drops, or accessibility detection")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Before you enable this", style = MaterialTheme.typography.titleMedium)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RequiredTextItem(text = "Switching the service causes a brief touch stutter, please test it yourself before editing the allowlist")
                        RequiredTextItem(text = "Using another accessibility app may make the optimization ineffective; you can verify this yourself after the service is off")
                        RequiredTextItem(text = "You must ensure the service keeps running in the background after being turned off, otherwise the system may suspend or kill it, causing a restart failure")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Requirements", style = MaterialTheme.typography.titleMedium)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RequiredTextItem(
                            text = "Privileged service",
                            enabled = privilegeContext == null,
                            imageVector = if (privilegeContext != null) PerfIcon.Check else PerfIcon.ArrowForward,
                            onClick = {
                                mainVm.navigatePage(PrivilegeServiceRoute)
                            },
                        )
                        RequiredTextItem(
                            text = "Enable \"persistent notification\"",
                            enabled = !statusRunning,
                            imageVector = if (statusRunning) PerfIcon.Check else PerfIcon.ArrowForward,
                            onClick = {
                                actionScope.launchTry {
                                    StatusService.requestStart(mainVm)
                                }
                            },
                        )
                        RequiredTextItem(
                            text = "Set battery saver to unrestricted",
                            enabled = !ignoreBatteryOptimizations,
                            imageVector = if (ignoreBatteryOptimizations) PerfIcon.Check else PerfIcon.ArrowForward,
                            onClickLabel = "Open the ignore battery optimizations settings page",
                            onClick = {
                                actionScope.launchTry {
                                    mainVm.permissionRequests.ensurePermissions(
                                        PermissionStates.ignoreBatteryOptimizations,
                                    )
                                }
                            },
                        )
                        RequiredTextItem(
                            text = "(Optional) Allow auto-start",
                            enabled = true,
                            imageVector = PerfIcon.OpenInNew,
                            onClickLabel = "Open the app details page",
                            onClick = {
                                openAppDetailsSettings()
                            },
                        )
                        RequiredTextItem(
                            text = "(Optional) Lock in \"Recent tasks\"",
                            enabled = true,
                            imageVector = PerfIcon.OpenInNew,
                            onClickLabel = "Open the app details page",
                            onClick = {
                                val inputManager = privilegeContextFlow.value?.inputManager
                                if (inputManager == null) {
                                    mainVm.navigatePage(PrivilegeServiceRoute)
                                } else {
                                    inputManager.keyevent(KeyEvent.KEYCODE_APP_SWITCH)
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "In some scenarios the service may occasionally not work right after starting; if you encounter this repeatedly, this feature is not recommended")
                }
                Spacer(modifier = Modifier.height(EmptyHeight))
            }
        }
    }
}

@Composable
private fun RequiredTextItem(
    text: String,
    imageVector: ImageVector? = null,
    enabled: Boolean = false,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .run {
                if (onClick != null) {
                    clickable(
                        enabled = enabled,
                        onClick = throttle(onClick),
                        onClickLabel = onClickLabel
                    )
                } else {
                    this
                }
            }
            .padding(horizontal = 4.dp),
    ) {
        val lineHeightDp = LocalDensity.current.run { LocalTextStyle.current.lineHeight.toDp() }
        Spacer(
            modifier = Modifier
                .padding(vertical = (lineHeightDp - 4.dp) / 2)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
                .size(4.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text)
        if (imageVector != null) {
            PerfIcon(
                imageVector = imageVector,
                modifier = Modifier.iconTextSize(),
            )
        }
    }

}
