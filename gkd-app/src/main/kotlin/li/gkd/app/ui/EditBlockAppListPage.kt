package li.gkd.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.MainActivity
import li.gkd.app.store.blockMatchAppListFlow
import li.gkd.app.ui.component.MultiTextField
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data object EditBlockAppListRoute : NavKey

@Composable
fun EditBlockAppListPage() {
    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel<EditBlockAppListVm>()
    val text by vm.textFlow.collectAsStateWithLifecycle()
    val onBack = throttle(vm.scope.launchAsFn {
        if (vm.getChangedSet() != null) {
            context.imeController.requestHide()
            if (!mainVm.dialogRequests.confirm(
                title = "Notice",
                text = "The current content is unsaved. Discard changes?",
            )) return@launchAsFn
        } else {
            context.imeController.hideAndAwait()
        }
        mainVm.popPage()
    })
    BackHandler(onBack = onBack)
    Scaffold(modifier = Modifier, topBar = {
        PerfTopAppBar(
            modifier = Modifier.fillMaxWidth(),
            navigationIcon = {
                PerfIconButton(
                    imageVector = PerfIcon.ArrowBack,
                    onClick = onBack,
                )
            },
            title = { Text(text = "App allowlist") },
            actions = {
                PerfIconButton(
                    imageVector = PerfIcon.Save,
                    onClick = throttle(vm.scope.launchAsFn {
                        val newSet = vm.getChangedSet()
                        if (newSet != null) {
                            blockMatchAppListFlow.value = newSet
                            toast("Updated successfully")
                        } else {
                            toast("No changes")
                        }
                        context.imeController.hideAndAwait()
                        mainVm.popPage()
                    })
                )
            }
        )
    }) { contentPadding ->
        MultiTextField(
            modifier = Modifier.scaffoldPadding(contentPadding),
            text = text,
            onTextChange = vm::setText,
            indicatorSize = vm.indicatorSizeFlow.collectAsStateWithLifecycle().value
        )
    }
}
