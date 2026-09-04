package li.gkd.app.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import li.gkd.app.META
import li.gkd.app.MainActivity
import li.gkd.app.ui.component.AppAlertDialog
import li.gkd.app.ui.component.TextListDialog
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.PLAY_STORE_URL
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.format
import li.gkd.app.util.getShareApkFile
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.openUri

@Composable
fun AboutDialogs() {
    VersionInfoDialog()
    ShareAppDialog()
}

@Composable
private fun VersionInfoDialog() {
    val vm = viewModel<AboutVm>()
    val visible by vm.showInfoDlgFlow.collectAsStateWithLifecycle()
    if (visible) {
        AppAlertDialog(
            onDismissRequest = { vm.setInfoDialogVisible(false) },
            title = { Text(text = "Version info") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column {
                        Text(text = "Build channel")
                        Text(text = META.channel)
                    }
                    Column {
                        Text(text = "Version code")
                        Text(text = META.versionCode.toString())
                    }
                    Column {
                        Text(text = "Version name")
                        Text(text = META.versionName)
                    }
                    Column {
                        Text(text = "Commit")
                        Text(
                            modifier = Modifier.clickable { openUri(META.commitUrl) },
                            text = META.tagName ?: META.commitId.substring(0, 16),
                            color = MaterialTheme.colorScheme.primary,
                            style = LocalTextStyle.current.copy(textDecoration = TextDecoration.Underline),
                        )
                    }
                    Column {
                        Text(text = "Commit time")
                        Text(text = META.commitTime.format("yyyy-MM-dd HH:mm:ss ZZ"))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.setInfoDialogVisible(false) }) {
                    Text(text = "Close")
                }
            },
        )
    }
}

@Composable
private fun ShareAppDialog() {
    val context = LocalActivity.current as MainActivity
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<AboutVm>()
    val visible by vm.showShareAppDlgFlow.collectAsStateWithLifecycle()
    if (visible) {
        val exportPlayTipText = buildAnnotatedString {
            append("The exported APK file only works on devices with Google Play Services installed; otherwise it will show an error after installing and opening. ")
            withLink(
                LinkAnnotation.Url(
                    ShortUrlSet.URL13,
                    TextLinkStyles(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    )
                )
            ) {
                append("We recommend tapping here to download from the official site")
            }
            append(", or tap below to continue anyway")
        }
        TextListDialog(
            onDismiss = { vm.setShareAppDialogVisible(false) },
            textList = listOf(
                "Share to another app" to vm.scope.launchAsFn(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "Share notice",
                            text = exportPlayTipText,
                            confirmText = "Continue",
                        )) return@launchAsFn
                    }
                    context.shareFile(getShareApkFile(), "Share the install file")
                },
                "Save to Downloads" to vm.scope.launchAsFn(Dispatchers.IO) {
                    if (!META.isGkdChannel) {
                        if (!mainVm.dialogRequests.confirm(
                            title = "Save notice",
                            text = exportPlayTipText,
                            confirmText = "Continue",
                        )) return@launchAsFn
                    }
                    context.saveFileToDownloads(getShareApkFile())
                },
                "Google Play" to {
                    mainVm.openUrl(PLAY_STORE_URL)
                },
            )
        )
    }
}
