package li.gkd.app.ui.app

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import li.gkd.app.priv.uiAutomationOccupiedFlow
import li.gkd.app.service.A11yService
import li.gkd.app.ui.PrivilegeServiceRoute
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.TermsAcceptDialog
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.toast

@Composable
fun AppOverlayHost() {
    val mainVm = LocalMainViewModel.current
    if (!mainVm.termsAcceptedFlow.collectAsStateWithLifecycle().value) {
        TermsAcceptDialog()
    } else {
        // Sheet
        mainVm.subsSheet.Render()

        // Dialog
        UiAutomationAlreadyRegisteredDlg()
        AccessRestrictedSettingsDlg()
        mainVm.dialogRequests.Render()
        mainVm.githubUpload.Render()
        mainVm.updateStatus?.UpgradeDialog()
        mainVm.subsLinkDialog.Render()
        mainVm.ruleGroupState.Render()
        mainVm.textDialog.Render()
        mainVm.shareLog.Render()
    }
}

val accessRestrictedSettingsShowFlow = MutableStateFlow(false)

@Composable
private fun AccessRestrictedSettingsDlg() {
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    LaunchedEffect(a11yRunning) {
        if (a11yRunning) {
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    val accessRestrictedSettingsShow by accessRestrictedSettingsShowFlow.collectAsStateWithLifecycle()
    val mainVm = LocalMainViewModel.current
    val isPrivilegeServicePage = mainVm.topRoute is PrivilegeServiceRoute
    LaunchedEffect(isPrivilegeServicePage, accessRestrictedSettingsShow) {
        if (isPrivilegeServicePage && accessRestrictedSettingsShow && !a11yRunning) {
            toast("Please re-authorize to lift the restriction")
            accessRestrictedSettingsShowFlow.value = false
        }
    }
    if (accessRestrictedSettingsShow && !isPrivilegeServicePage && !a11yRunning) {
        AppAlertDialog(
            title = {
                Text(text = "Permission restricted")
            },
            text = {
                Text(text = "The \"access restricted settings\" permission has been restricted, please re-authorize via the privileged service")
            },
            onDismissRequest = {
                accessRestrictedSettingsShowFlow.value = false
            },
            confirmButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                    mainVm.navigatePage(PrivilegeServiceRoute)
                }) {
                    Text(text = "Go to authorize")
                }
            },
            dismissButton = {
                TextButton({
                    accessRestrictedSettingsShowFlow.value = false
                }) {
                    Text(text = "Close")
                }
            },
        )
    }
}

@Composable
private fun UiAutomationAlreadyRegisteredDlg() {
    if (uiAutomationOccupiedFlow.collectAsStateWithLifecycle().value) {
        AppAlertDialog(
            onDismissRequest = {
                uiAutomationOccupiedFlow.value = false
            },
            title = { Text(text = "Startup failed") },
            text = {
                Text(text = "Failed to start the automation service: it's already occupied by another app. Please close the existing service and try again\n\nNote: only one automation service can run at a time — make sure no other app or test framework is using it before starting")
            },
            confirmButton = {
                TextButton(onClick = {
                    uiAutomationOccupiedFlow.value = false
                }) {
                    Text(text = "Got it")
                }
            }
        )
    }
}
