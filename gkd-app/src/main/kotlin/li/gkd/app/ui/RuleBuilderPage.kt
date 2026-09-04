package li.gkd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.TextSwitch
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.ui.style.titleItemPadding
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle

private data class ActionOption(val value: String, val label: String)

// clickCenter/longClickCenter fall back to the node's own bounds when no
// custom position is set, and swipe needs a start/end drag the guided form
// doesn't collect — so those two are omitted here; both are still reachable
// through the JSON5 editor for anyone who needs them.
private val actionOptions = listOf(
    ActionOption("click", "Click"),
    ActionOption("longClick", "Long click"),
    ActionOption("clickNode", "Click (element only)"),
    ActionOption("longClickNode", "Long click (element only)"),
    ActionOption("clickCenter", "Click (screen center point)"),
    ActionOption("longClickCenter", "Long click (screen center point)"),
    ActionOption("back", "Press back"),
    ActionOption("none", "Do nothing (match only)"),
)

private data class ResetMatchOption(val value: String?, val label: String)

private val resetMatchOptions = listOf(
    ResetMatchOption(null, "When the screen changes"),
    ResetMatchOption("match", "When this rule matches"),
    ResetMatchOption("app", "When the app changes"),
)

@Composable
fun RuleBuilderPage(route: RuleBuilderRoute) {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel { RuleBuilderVm(route) }
    val scope = vm.scope
    val form by vm.formFlow.collectAsStateWithLifecycle()
    val selectorError by vm.selectorErrorFlow.collectAsStateWithLifecycle()
    var advancedExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    val canSave = form.name.isNotBlank() && form.selector.isNotBlank() && selectorError == null && !saving

    val onSave = throttle(scope.launchAsFn(Dispatchers.Main) {
        saving = true
        val error = withContext(Dispatchers.Default) { vm.trySave() }
        saving = false
        if (error != null) {
            errorText = error
            return@launchAsFn
        }
        errorText = null
        if (route.isGlobal) {
            mainVm.navigatePage(
                SubsGlobalGroupListRoute(subsItemId = route.subsId),
                replaced = true,
            )
        } else {
            mainVm.navigatePage(
                SubsAppGroupListRoute(subsItemId = route.subsId, appId = requireNotNull(route.appId)),
                replaced = true,
            )
        }
    })

    Scaffold(topBar = {
        PerfTopAppBar(
            navigationIcon = {
                PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = mainVm::popPage)
            },
            title = { Text(text = "Build a rule") },
        )
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .scaffoldPadding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = itemHorizontalPadding, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = vm::setName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Name") },
                supportingText = { Text(text = "Shown in your rule list") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.desc,
                onValueChange = vm::setDesc,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Description (optional)") },
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = form.selector,
                onValueChange = vm::setSelector,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Selector") },
                supportingText = {
                    Text(text = selectorError ?: "Which element on the captured screen this rule matches")
                },
                isError = selectorError != null,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Action", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "What happens when the selector matches",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                actionOptions.take(2).forEach { option ->
                    FilterChip(
                        selected = form.action == option.value,
                        onClick = { vm.setAction(option.value) },
                        label = { Text(text = option.label) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (!route.activityId.isNullOrBlank()) {
                TextSwitch(
                    paddingDisabled = true,
                    title = "Only on this screen",
                    subtitle = "Off applies the rule to the whole app instead",
                    checked = form.scopeToActivity,
                    onCheckedChange = vm::setScopeToActivity,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (advancedExpanded) "Hide advanced options" else "Show advanced options",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { advancedExpanded = !advancedExpanded })
                    .padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (advancedExpanded) {
                Text(
                    text = "Action (more options)",
                    modifier = Modifier.titleItemPadding(showTop = false),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    actionOptions.drop(2).forEach { option ->
                        FilterChip(
                            selected = form.action == option.value,
                            onClick = { vm.setAction(option.value) },
                            label = { Text(text = option.label) },
                        )
                    }
                }

                Text(
                    text = "Timing",
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                NumberField(
                    value = form.matchDelay,
                    onValueChange = vm::setMatchDelay,
                    label = "Match delay (ms)",
                    helper = "Wait this long after the screen appears before matching",
                )
                NumberField(
                    value = form.matchTime,
                    onValueChange = vm::setMatchTime,
                    label = "Match time limit (ms)",
                    helper = "Stop trying to match after this long",
                )
                NumberField(
                    value = form.actionCd,
                    onValueChange = vm::setActionCd,
                    label = "Cooldown after acting (ms)",
                    helper = "Minimum time between two triggers of this rule",
                )
                NumberField(
                    value = form.actionDelay,
                    onValueChange = vm::setActionDelay,
                    label = "Delay before acting (ms)",
                    helper = "Wait this long after matching before acting",
                )
                NumberField(
                    value = form.actionMaximum,
                    onValueChange = vm::setActionMaximum,
                    label = "Maximum triggers",
                    helper = "Stop acting after this many successful triggers",
                )

                Text(
                    text = "Reset trigger count",
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    resetMatchOptions.forEach { option ->
                        FilterChip(
                            selected = form.resetMatch == option.value,
                            onClick = { vm.setResetMatch(option.value) },
                            label = { Text(text = option.label) },
                        )
                    }
                }

                Text(
                    text = "Matching",
                    modifier = Modifier.titleItemPadding(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextSwitch(
                    paddingDisabled = true,
                    title = "Fast query",
                    subtitle = "Skip elements unlikely to match, for a quicker check",
                    checked = form.fastQuery,
                    onCheckedChange = vm::setFastQuery,
                )
                TextSwitch(
                    paddingDisabled = true,
                    title = "Match from screen root",
                    subtitle = "Search the whole screen instead of just what changed",
                    checked = form.matchRoot,
                    onCheckedChange = vm::setMatchRoot,
                )
            }

            val currentError = errorText
            if (currentError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = currentError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (saving) "Saving…" else "Save rule")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    helper: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.all(Char::isDigit)) onValueChange(new) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        supportingText = { Text(text = helper) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Spacer(modifier = Modifier.height(12.dp))
}
