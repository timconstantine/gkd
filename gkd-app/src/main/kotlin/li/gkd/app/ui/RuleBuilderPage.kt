package li.gkd.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import li.gkd.app.data.TextTemplateToken
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.TextSwitch
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle

private data class ActionOption(val value: String, val label: String, val description: String)

// clickCenter/longClickCenter fall back to the node's own bounds when no
// custom position is set, and swipe needs a start/end drag the guided form
// doesn't collect — so those two are omitted here; both are still reachable
// through the JSON5 editor for anyone who needs them.
//
// The first three options (click, long click, enter text) are shown as
// essentials; the rest live under "Advanced" — see the take(3)/drop(3) split
// below.
private val actionOptions = listOf(
    ActionOption(
        "click", "Click",
        "Taps the element, or its center point if it isn't directly tappable.",
    ),
    ActionOption(
        "longClick", "Long click",
        "Long-presses the element, or its center point if it isn't directly tappable.",
    ),
    ActionOption(
        "setText", "Enter text",
        "Types the given text into the element, if it's an editable field.",
    ),
    ActionOption(
        "clickNode", "Click (element only)",
        "Taps the element directly; does nothing if it isn't tappable.",
    ),
    ActionOption(
        "longClickNode", "Long click (element only)",
        "Long-presses the element directly; does nothing if it isn't tappable.",
    ),
    ActionOption(
        "clickCenter", "Click (center point)",
        "Taps the exact center point of the matched element's bounds.",
    ),
    ActionOption(
        "longClickCenter", "Long click (center point)",
        "Long-presses the exact center point of the matched element's bounds.",
    ),
    ActionOption(
        "back", "Press back",
        "Presses the device back button instead of tapping anything.",
    ),
    ActionOption(
        "none", "Do nothing",
        "Takes no action — just records that the rule matched.",
    ),
)

private data class ResetMatchOption(val value: String?, val label: String, val description: String)

private val resetMatchOptions = listOf(
    ResetMatchOption(
        null, "On screen change",
        "Starts the trigger count over each time you switch to a different screen.",
    ),
    ResetMatchOption(
        "match", "On every match",
        "Starts the trigger count over every time this rule matches, not just on screen change.",
    ),
    ResetMatchOption(
        "app", "On app change",
        "Starts the trigger count over only when you leave this app entirely.",
    ),
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

    val canSave = form.name.isNotBlank() && form.selector.isNotBlank() && selectorError == null &&
        (form.action != "setText" || form.text.isNotBlank()) && !saving

    val onSave = throttle(scope.launchAsFn(Dispatchers.Main) {
        saving = true
        val error = withContext(Dispatchers.Default) { vm.trySave() }
        saving = false
        if (error != null) {
            errorText = error
            return@launchAsFn
        }
        errorText = null
        if (vm.isEdit) {
            mainVm.popPage()
        } else if (route.isGlobal) {
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
            title = { Text(text = if (vm.isEdit) "Edit rule" else "Build a rule") },
        )
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .scaffoldPadding(contentPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = itemHorizontalPadding, vertical = 4.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = vm::setName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Name") },
                supportingText = { Text(text = "Shown in your rule list") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = form.desc,
                onValueChange = vm::setDesc,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "Description (optional)") },
            )
            Spacer(modifier = Modifier.height(8.dp))
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

            SectionLabel(text = "Action")
            actionOptions.take(3).forEach { option ->
                OptionRow(
                    title = option.label,
                    description = option.description,
                    selected = form.action == option.value,
                    onClick = { vm.setAction(option.value) },
                )
            }

            if (form.action == "setText") {
                TextToEnterField(text = form.text, onTextChange = vm::setText)
            }

            if (!form.activityId.isNullOrBlank()) {
                TextSwitch(
                    paddingDisabled = true,
                    title = "Only on this screen",
                    subtitle = "Off applies the rule to the whole app instead",
                    checked = form.scopeToActivity,
                    onCheckedChange = vm::setScopeToActivity,
                )
            }

            Text(
                text = if (advancedExpanded) "Hide advanced options" else "Show advanced options",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { advancedExpanded = !advancedExpanded })
                    .padding(vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            if (advancedExpanded) {
                SectionLabel(text = "More actions", showTop = false)
                actionOptions.drop(3).forEach { option ->
                    OptionRow(
                        title = option.label,
                        description = option.description,
                        selected = form.action == option.value,
                        onClick = { vm.setAction(option.value) },
                    )
                }

                SectionLabel(text = "Timing")
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
                    helper = "Stop acting after this many successful triggers; clear this to allow unlimited triggers",
                )

                SectionLabel(text = "Reset trigger count")
                resetMatchOptions.forEach { option ->
                    OptionRow(
                        title = option.label,
                        description = option.description,
                        selected = form.resetMatch == option.value,
                        onClick = { vm.setResetMatch(option.value) },
                    )
                }

                SectionLabel(text = "Matching")
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = if (saving) "Saving…" else if (vm.isEdit) "Save changes" else "Save rule")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// Vertical-only section spacing — the page's own Column already applies
// horizontal padding, so titleItemPadding()'s matching horizontal padding
// would just double up here.
@Composable
private fun SectionLabel(text: String, showTop: Boolean = true) {
    Text(
        text = text,
        modifier = Modifier.padding(top = if (showTop) 12.dp else 0.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * A compact, single-line-title-plus-one-sentence-caption radio row — used
 * for every "pick one" choice in this form instead of a section header (title
 * + subtitle + spacer) followed by a separate chip row, so each option's own
 * description carries the explanation instead of duplicating it above.
 */
@Composable
private fun OptionRow(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = throttle(onClick))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The text field for the "Enter text" action, plus a row of chips for
 * inserting a live-value placeholder (today's date, the current time, …) at
 * the cursor — so building something like "Checked in on [today]" doesn't
 * require typing the bracket syntax by hand. [TextTemplateToken] defines
 * what each placeholder expands to when the rule actually runs.
 */
@Composable
private fun TextToEnterField(
    text: String,
    onTextChange: (String) -> Unit,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(text)) }
    OutlinedTextField(
        value = fieldValue,
        onValueChange = { new ->
            fieldValue = new
            onTextChange(new.text)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = "Text to enter") },
        supportingText = { Text(text = "What to type into the matched element") },
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = "Insert a live value:",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextTemplateToken.entries.forEach { token ->
            AssistChip(
                onClick = {
                    val selection = fieldValue.selection
                    val newText = fieldValue.text.replaceRange(
                        selection.min,
                        selection.max,
                        token.token,
                    )
                    val newCursor = selection.min + token.token.length
                    fieldValue = TextFieldValue(newText, TextRange(newCursor))
                    onTextChange(newText)
                },
                label = { Text(text = token.label) },
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
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
    Spacer(modifier = Modifier.height(8.dp))
}
