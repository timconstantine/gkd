package li.gkd.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.R
import li.gkd.app.service.ActivityService
import li.gkd.app.service.ButtonService
import li.gkd.app.service.EventService
import li.gkd.app.service.HttpService
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.PerfCustomIconButton
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.SettingItem
import li.gkd.app.ui.component.SettingsDialog
import li.gkd.app.ui.component.TextSwitch
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.TABULAR_NUMBERS_FONT_FEATURE
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.itemVerticalPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle

@Serializable
data object AdvancedPageRoute : NavKey

@Composable
fun AdvancedPage() {
    AdvancedContent()
}

@Composable
private fun AdvancedContent() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AdvancedVm>()
    val scope = vm.scope
    val showEditPortDialog by vm.showEditPortDialogFlow.collectAsStateWithLifecycle()
    val showHttpSettingsDialog by vm.httpSettingsDialogVisibleFlow.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()
    val httpServer by HttpService.httpServerFlow.collectAsStateWithLifecycle()
    val localNetworkIps by HttpService.localNetworkIpsFlow.collectAsStateWithLifecycle()
    val buttonServiceRunning by ButtonService.isRunning.collectAsStateWithLifecycle()
    val activityServiceRunning by ActivityService.isRunning.collectAsStateWithLifecycle()
    val eventServiceRunning by EventService.isRunning.collectAsStateWithLifecycle()

    if (showHttpSettingsDialog) {
        SettingsDialog(
            title = "HTTP settings",
            onDismissRequest = { vm.setHttpSettingsDialogVisible(false) },
        ) {
            SettingItem(
                title = "Service port",
                subtitle = store.httpServerPort.toString(),
                imageVector = PerfIcon.Edit,
                onClickLabel = "Edit service port",
                onClick = {
                    vm.setEditPortDialogVisible(true)
                },
            )
            TextSwitch(
                title = "Clear subscription",
                subtitle = "Delete the in-memory subscription when the service stops",
                checked = store.autoClearMemorySubs,
                onCheckedChange = vm::setAutoClearMemorySubs,
            )
        }
    }

    if (showEditPortDialog) {
        EditHttpPortDialog(
            currentPort = store.httpServerPort,
            onDismissRequest = { vm.setEditPortDialogVisible(false) },
            onConfirm = {
                if (vm.saveHttpServerPort(it)) {
                    vm.setEditPortDialogVisible(false)
                }
            },
        )
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            PerfTopAppBar(
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = { Text(text = "Advanced settings") },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        ) {
            Text(
                text = "HTTP",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            HttpServiceItem(
                running = httpServer != null,
                settingsSelected = showHttpSettingsDialog,
                port = store.httpServerPort,
                localNetworkIps = localNetworkIps,
                onSettingsClick = { vm.setHttpSettingsDialogVisible(true) },
                onRunningChange = throttle(fn = scope.launchAsFn { enabled ->
                    HttpService.setEnabled(mainVm, enabled)
                }),
                onAddressClick = mainVm::openUrl,
            )
            Text(
                text = "Snapshot",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingItem(
                title = "Snapshot records",
                subtitle = "Screen node info and screenshots",
                onClick = { mainVm.navigatePage(SnapshotPageRoute) },
            )
            TextSwitch(
                title = "Snapshot button",
                subtitle = "Show a button to tap-capture a snapshot",
                checked = buttonServiceRunning,
                onCheckedChange = scope.launchAsFn { enabled ->
                    ButtonService.setEnabled(mainVm, enabled)
                },
            )
            SettingItem(
                title = "Snapshot settings",
                subtitle = "Trigger method, screenshot processing, and export",
                onClick = { mainVm.navigatePage(SnapshotSettingsRoute) },
            )

            Text(
                text = "Upload",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingItem(
                title = "GitHub Cookie",
                subtitle = "Generate a snapshot/log link",
                suffix = "Get instructions",
                suffixUnderline = true,
                onSuffixClick = mainVm.githubUpload::openCookieHelp,
                imageVector = PerfIcon.Edit,
                onClick = mainVm.githubUpload::editCookie,
            )

            Text(
                text = "Logs",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            SettingItem(
                title = "Activity log",
                subtitle = "Activity switch log",
                onClick = { mainVm.navigatePage(ActivityLogRoute) },
            )
            TextSwitch(
                title = "Activity logging service",
                subtitle = "Show the current screen's info",
                checked = activityServiceRunning,
                onCheckedChange = scope.launchAsFn { enabled ->
                    ActivityService.setEnabled(mainVm, enabled)
                },
            )
            SettingItem(
                title = "Event log",
                subtitle = "Accessibility event log",
                onClick = { mainVm.navigatePage(A11yEventLogRoute) },
            )
            TextSwitch(
                title = "Event logging service",
                subtitle = "Show accessibility events",
                checked = eventServiceRunning,
                onCheckedChange = scope.launchAsFn { enabled ->
                    EventService.setEnabled(mainVm, enabled)
                },
            )
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun HttpServiceItem(
    running: Boolean,
    settingsSelected: Boolean,
    port: Int,
    localNetworkIps: List<String>,
    onSettingsClick: () -> Unit,
    onRunningChange: (Boolean) -> Unit,
    onAddressClick: (String) -> Unit,
) {
    val addressStyle = MaterialTheme.typography.bodySmall.copy(
        fontFeatureSettings = TABULAR_NUMBERS_FONT_FEATURE,
    )
    val addressItem: @Composable (String, String) -> Unit = { host, type ->
        Text(
            text = "${host}:${port} · $type",
            style = addressStyle,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "View the $type access address",
                    onClick = throttle { onAddressClick("http://${host}:${port}") },
                )
                .padding(vertical = 2.dp),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    onClickLabel = "Toggle HTTP service state",
                    onClick = { onRunningChange(!running) },
                )
                .padding(
                    start = itemHorizontalPadding,
                    top = itemVerticalPadding,
                    end = itemHorizontalPadding,
                    bottom = 4.dp,
                ),
        ) {
            TextSwitch(
                modifier = Modifier.fillMaxWidth(),
                paddingDisabled = true,
                title = "HTTP service",
                subtitle = "Connect and debug via a browser",
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "Open the HTTP settings dialog",
                        onClick = onSettingsClick,
                        id = R.drawable.ic_page_info,
                        contentDescription = "HTTP settings",
                        tint = if (settingsSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            LocalContentColor.current
                        },
                    )
                },
                checked = running,
                onCheckedChange = onRunningChange,
                onClick = null,
            )
        }
        AnimatedVisibility(visible = running) {
            Column(
                modifier = Modifier.padding(
                    start = itemHorizontalPadding,
                    top = 0.dp,
                    end = itemHorizontalPadding,
                    bottom = 4.dp,
                ),
            ) {
                addressItem("127.0.0.1", "This device")
                localNetworkIps.forEach { host ->
                    addressItem(host, "Local network")
                }
            }
        }
    }
}

@Composable
private fun EditHttpPortDialog(
    currentPort: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(currentPort.toString()) }
    AppAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(text = "Service port") },
        text = {
            OutlinedTextField(
                value = value,
                placeholder = { Text(text = "Enter an integer between 1000 and 65535") },
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .autoFocus(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(
                        text = "${value.length} / 5",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End,
                    )
                },
            )
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                enabled = value.isNotEmpty(),
                onClick = { onConfirm(value) },
            ) {
                Text(text = "Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Cancel")
            }
        },
    )
}
