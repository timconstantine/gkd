package li.gkd.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.R
import li.gkd.app.app
import li.gkd.app.permission.PermissionStates
import li.gkd.app.service.ScreenshotService
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.CustomOutlinedTextField
import li.gkd.app.ui.component.PerfCustomIconButton
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.TextSwitch
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.AndroidTarget
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.launchTry
import li.gkd.app.util.throttle

@Serializable
data object SnapshotSettingsRoute : NavKey

@Composable
fun SnapshotSettingsPage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<SnapshotSettingsVm>()
    val scope = vm.scope
    val store by storeFlow.collectAsStateWithLifecycle()
    val screenshotServiceRunning by ScreenshotService.isRunning.collectAsStateWithLifecycle()
    var showCaptureScreenshotDialog by rememberSaveable { mutableStateOf(false) }

    fun setScreenshotServiceEnabled(enabled: Boolean) {
        scope.launchTry {
            if (!enabled) {
                ScreenshotService.stop()
                return@launchTry
            }
            if (!mainVm.permissionRequests.ensurePermissions(PermissionStates.notification)) {
                return@launchTry
            }
            val activityResult = mainVm.activityResults.startActivity(
                app.mediaProjectionManager.createScreenCaptureIntent(),
            )
            val intent = activityResult.data
            if (activityResult.resultCode == Activity.RESULT_OK && intent != null) {
                ScreenshotService.start(intent)
            }
        }
    }

    if (showCaptureScreenshotDialog) {
        CaptureScreenshotConfigDialog(
            appId = store.screenshotTargetAppId,
            eventSelector = store.screenshotEventSelector,
            onOpenHelp = {
                showCaptureScreenshotDialog = false
                mainVm.navigateWebPage(ShortUrlSet.URL15)
            },
            onDismissRequest = { showCaptureScreenshotDialog = false },
            onConfirm = { appId, selector ->
                if (vm.saveCaptureScreenshotConfig(appId, selector)) {
                    showCaptureScreenshotDialog = false
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
                title = { Text(text = "Snapshot settings") },
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
                text = "Generation method",
                modifier = Modifier.titleItemPadding(showTop = false),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!AndroidTarget.R) {
                TextSwitch(
                    title = "Screenshot service",
                    subtitle = "Generating a snapshot requires capturing a screenshot",
                    checked = screenshotServiceRunning,
                    onCheckedChange = ::setScreenshotServiceEnabled,
                )
            }
            TextSwitch(
                title = "Volume-key snapshot",
                subtitle = "Save a snapshot when the volume changes",
                checked = store.captureVolumeChange,
                onCheckedChange = vm::setCaptureVolumeChange,
            )
            TextSwitch(
                title = "Screenshot-triggered snapshot",
                subtitle = "Save a snapshot when a screenshot is taken",
                checked = store.captureScreenshot,
                suffixIcon = {
                    PerfCustomIconButton(
                        size = 32.dp,
                        iconSize = 20.dp,
                        onClickLabel = "Open the screenshot snapshot config dialog",
                        onClick = throttle { showCaptureScreenshotDialog = true },
                        id = R.drawable.ic_page_info,
                        contentDescription = "Screenshot snapshot settings",
                    )
                },
                onCheckedChange = vm::setCaptureScreenshot,
            )

            Text(
                text = "Screenshot processing",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextSwitch(
                title = "Hide status bar",
                subtitle = "Hide the status bar in snapshot screenshots",
                checked = store.hideSnapshotStatusBar,
                onCheckedChange = vm::setHideSnapshotStatusBar,
            )

            Text(
                text = "Export",
                modifier = Modifier.titleItemPadding(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextSwitch(
                title = "Auto-save to Downloads",
                subtitle = "Export a ZIP file after each snapshot",
                checked = store.autoSaveSnapshotToDownloads,
                onCheckedChange = scope.launchAsFn { enabled ->
                    if (
                        !enabled || mainVm.permissionRequests.ensurePermissions(
                            PermissionStates.writeExternalStorage,
                        )
                    ) {
                        vm.setAutoSaveSnapshotToDownloads(enabled)
                    }
                },
            )
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }
}

@Composable
private fun CaptureScreenshotConfigDialog(
    appId: String,
    eventSelector: String,
    onOpenHelp: () -> Unit,
    onDismissRequest: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var appIdValue by remember { mutableStateOf(appId) }
    var eventSelectorValue by remember { mutableStateOf(eventSelector) }
    AppAlertDialog(
        properties = DialogProperties(dismissOnClickOutside = false),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = "Screenshot-triggered snapshot")
                PerfIconButton(
                    imageVector = PerfIcon.HelpOutline,
                    onClick = throttle(onOpenHelp),
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                CustomOutlinedTextField(
                    label = { Text("App ID") },
                    value = appIdValue,
                    placeholder = { Text(text = "Enter the target app ID") },
                    onValueChange = { appIdValue = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                CustomOutlinedTextField(
                    label = { Text("Feature event selector") },
                    value = eventSelectorValue,
                    placeholder = { Text(text = "Enter a feature event selector") },
                    onValueChange = { eventSelectorValue = it },
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .autoFocus(),
                )
            }
        },
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = throttle { onConfirm(appIdValue, eventSelectorValue) },
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
