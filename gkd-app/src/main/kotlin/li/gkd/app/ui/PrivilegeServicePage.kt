package li.gkd.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import li.gkd.app.priv.gkdPrivilegeUiConfig
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.throttle
import priv.kit.ui.PrivilegeScaffold
import priv.kit.ui.PrivilegeUiViewModel

@Serializable
data object PrivilegeServiceRoute : NavKey

@Composable
fun PrivilegeServicePage() {
    val mainVm = LocalMainViewModel.current
    val application = LocalContext.current.applicationContext as Application
    val privilegeVm = viewModel {
        GkdPrivilegeUiViewModel(application) {
            mainVm.popPage()
        }
    }
    val showInfoDialog by privilegeVm.showInfoDialogFlow.collectAsStateWithLifecycle()
    if (showInfoDialog) {
        PrivilegeServiceInfoDialog(
            onDismissRequest = { privilegeVm.setInfoDialogVisible(false) },
        )
    }
    PrivilegeScaffold(
        modifier = Modifier.fillMaxSize(),
        viewModel = privilegeVm,
        topBar = {
            PerfTopAppBar(
                navigationIcon = {
                    PerfIconButton(
                        imageVector = PerfIcon.ArrowBack,
                        onClick = mainVm::popPage,
                    )
                },
                title = {
                    Text(text = "Privileged service")
                },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Info,
                        contentDescription = "Page info",
                        onClick = throttle {
                            privilegeVm.setInfoDialogVisible(true)
                        },
                    )
                },
            )
        },
    )
}

@Composable
private fun PrivilegeServiceInfoDialog(onDismissRequest: () -> Unit) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
        ),
    )
    AppAlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = "Privileged service")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "This page is used to start and manage the privileged service. Once connected, it provides GKD with system-level capabilities such as automation and necessary permission grants; once disconnected, features that depend on the privileged service will be unavailable.",
                )
                Text(
                    text = buildAnnotatedString {
                        append("The privileged service is built on the open-source project ")
                        withLink(
                            LinkAnnotation.Url(
                                url = "https://github.com/priv-kit/priv-kit",
                                styles = linkStyles,
                            ),
                        ) {
                            append("priv-kit")
                        }
                        append(" (its own privileged runtime), gaining elevated privileges without relying on an external authorizer")
                    },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = "Got it")
            }
        },
    )
}

private class GkdPrivilegeUiViewModel(
    application: Application,
    private val backAction: () -> Unit,
) : PrivilegeUiViewModel(
    application,
    gkdPrivilegeUiConfig,
) {
    val showInfoDialogFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    fun setInfoDialogVisible(visible: Boolean) {
        showInfoDialogFlow.value = visible
    }

    override fun onBackClick(): Boolean {
        backAction()
        return true
    }
}
