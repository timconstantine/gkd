package li.gkd.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.store.storeFlow
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.CaptureTriggerOption
import li.gkd.app.util.findOption
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle
import li.gkd.db.LOCAL_SUBS_ID

@Serializable
data class CaptureWaitRoute(val isGlobal: Boolean, val subsId: Long = LOCAL_SUBS_ID) : NavKey

@Composable
fun CaptureWaitPage(route: CaptureWaitRoute) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { CaptureWaitVm() }
    val scope = vm.scope
    val newSnapshot by vm.newSnapshotFlow.collectAsStateWithLifecycle()
    val store by storeFlow.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.armDefaultTrigger(mainVm)
    }

    LaunchedEffect(newSnapshot) {
        val snapshot = newSnapshot ?: return@LaunchedEffect
        vm.disarmIfWeArmedIt(mainVm)
        mainVm.navigatePage(
            SnapshotInspectorRoute(snapshotId = snapshot.id, isGlobal = route.isGlobal, subsId = route.subsId),
            replaced = true,
        )
    }

    val cancel = throttle(scope.launchAsFn {
        vm.disarmIfWeArmedIt(mainVm)
        mainVm.popPage()
    })
    BackHandler(true, cancel)

    val triggerInstruction = when (CaptureTriggerOption.objects.findOption(store.defaultCaptureTrigger)) {
        CaptureTriggerOption.FloatingButton -> "tap the floating snapshot button"
        CaptureTriggerOption.VolumeKey -> "press a volume key"
    }

    Scaffold(topBar = {
        PerfTopAppBar(
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = cancel)
            },
            title = { Text(text = "Waiting for a snapshot") },
        )
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .scaffoldPadding(contentPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Switch to the screen you want a rule for, then $triggerInstruction.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "GKD will bring you back here automatically once the snapshot is saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = cancel) {
                Text(text = "Cancel")
            }
        }
    }
}
