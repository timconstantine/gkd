package li.gkd.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import li.gkd.app.MainActivity
import li.gkd.app.data.RuleComposer
import li.gkd.app.ui.component.AppDialog
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.component.SubscriptionPageContent
import li.gkd.app.ui.component.autoFocus
import li.gkd.app.ui.share.LocalDarkTheme
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.getJson5Transformation
import li.gkd.app.ui.style.scaffoldPadding
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle
import li.gkd.db.Db

@Serializable
data class UpsertRuleGroupRoute(
    val subsId: Long,
    val groupKey: Int? = null,
    val appId: String? = null,
    val forward: Boolean = false,
    // Pre-fills the editor when opened from the snapshot node inspector with a
    // selector already built, instead of leaving it blank. Ignored when editing
    // an existing rule (groupKey != null).
    val initialText: String? = null,
) : NavKey

@Composable
fun UpsertRuleGroupPage(route: UpsertRuleGroupRoute) {
    val subsId = route.subsId
    val appId = route.appId
    val forward = route.forward

    val mainVm = LocalMainViewModel.current
    val context = LocalActivity.current as MainActivity
    val vm = viewModel { UpsertRuleGroupVm(route) }
    SubscriptionPageContent(vm.uiState) { state ->
        val editedText by vm.textFlow.collectAsStateWithLifecycle()
        val text = editedText ?: state.initialText

        val checkIfSaveText = throttle(vm.scope.launchAsFn(Dispatchers.Default) {
            if (vm.hasTextChanged()) {
                withContext(Dispatchers.Main.immediate) {
                    context.imeController.requestHide()
                }
                if (!mainVm.dialogRequests.confirm(
                        title = "Notice",
                        text = "The current content is unsaved. Discard changes?",
                    )
                ) {
                    return@launchAsFn
                }
            } else {
                context.imeController.hideAndAwait()
            }
            mainVm.popPage()
        })

        val onClickSave = throttle(vm.scope.launchAsFn(Dispatchers.Main) {
            val addedAppId = withContext(Dispatchers.Default) { vm.saveRule() }
            context.imeController.hideAndAwait()
            if (forward) {
                if (appId == null) {
                    mainVm.navigatePage(
                        SubsGlobalGroupListRoute(subsItemId = subsId),
                        replaced = true,
                    )
                } else {
                    mainVm.navigatePage(
                        SubsAppGroupListRoute(
                            subsItemId = subsId,
                            appId = addedAppId ?: appId,
                        ),
                        replaced = true,
                    )
                }
            } else {
                mainVm.popPage()
            }
        })
        var showLibraryPicker by remember { mutableStateOf(false) }
        BackHandler(true, checkIfSaveText)
        Scaffold(modifier = Modifier, topBar = {
            PerfTopAppBar(
                modifier = Modifier.fillMaxWidth(),
                navigationIcon = {
                    PerfIconButton(imageVector = PerfIcon.ArrowBack, onClick = checkIfSaveText)
                },
                title = {
                    Text(text = if (vm.isEdit) "Edit rule" else "Add rule")
                },
                actions = {
                    PerfIconButton(
                        imageVector = PerfIcon.Layers,
                        contentDescription = "Insert from library",
                        onClick = { showLibraryPicker = true },
                    )
                    PerfIconButton(
                        imageVector = PerfIcon.Save,
                        onClick = onClickSave,
                        enabled = text.isNotBlank(),
                    )
                },
            )
        }) { paddingValues ->
            val textColors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            )
            Box(
                modifier = Modifier
                    .scaffoldPadding(paddingValues)
                    .fillMaxSize(),
            ) {
                CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
                    val imeShowing by context.imeController.showAnimationRunningFlow.collectAsStateWithLifecycle()
                    val modifier = Modifier
                        .autoFocus()
                        .fillMaxSize()
                        .run {
                            if (imeShowing) {
                                this
                            } else {
                                imePadding()
                            }
                        }
                    TextField(
                        value = text,
                        onValueChange = vm::setText,
                        modifier = modifier,
                        shape = RectangleShape,
                        colors = textColors,
                        visualTransformation = getJson5Transformation(LocalDarkTheme.current),
                        placeholder = {
                            Text(text = if (vm.isApp) "Enter an app rule\n" else "Enter a global rule\n")
                        },
                    )
                }
                if (text.isNotEmpty()) {
                    Text(
                        text = text.length.toString(),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 2.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
        if (showLibraryPicker) {
            SelectorLibraryPickerDialog(
                onDismissRequest = { showLibraryPicker = false },
                onPick = { selector ->
                    vm.setText(
                        if (text.isBlank()) {
                            RuleComposer.composeSeededGroupText(selector, activityId = null)
                        } else {
                            "$text\n$selector"
                        },
                    )
                    showLibraryPicker = false
                },
            )
        }
    }
}

@Composable
private fun SelectorLibraryPickerDialog(
    onDismissRequest: () -> Unit,
    onPick: (String) -> Unit,
) {
    val items by Db.selectorLibraryDao.query().collectAsStateWithLifecycle(initialValue = emptyList())
    AppDialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "Insert from library", style = MaterialTheme.typography.titleMedium)
                if (items.isEmpty()) {
                    Text(
                        text = "No saved selectors yet",
                        modifier = Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(items, key = { it.id }) { item ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(item.selector) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(text = item.name ?: "(unnamed)", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = item.selector,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
