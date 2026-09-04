package li.gkd.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import li.gkd.app.META
import li.gkd.app.permission.PermissionStates
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.service.A11yService
import li.gkd.app.ui.component.AnimatedBooleanContent
import li.gkd.app.ui.component.PerfIcon
import li.gkd.app.ui.component.PerfIconButton
import li.gkd.app.ui.component.PerfTopAppBar
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.ui.style.EmptyHeight
import li.gkd.app.ui.style.cardHorizontalPadding
import li.gkd.app.ui.style.itemHorizontalPadding
import li.gkd.app.ui.style.surfaceCardColors
import li.gkd.app.util.AutomatorModeOption
import li.gkd.app.util.ShortUrlSet
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.openA11ySettings
import li.gkd.app.util.throttle
import li.gkd.app.util.toast

@Serializable
data object WorkModeRoute : NavKey

@Composable
fun WorkModePage() {
    val mainVm = LocalMainViewModel.current
    val vm = viewModel<WorkModeVm>()
    val writeSecureSettings by PermissionStates.writeSecureSettings.stateFlow.collectAsStateWithLifecycle()
    val a11yRunning by A11yService.isRunning.collectAsStateWithLifecycle()
    val privilegeContext by privilegeContextFlow.collectAsStateWithLifecycle()
    val automatorMode by mainVm.automatorModeFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection), topBar = {
        PerfTopAppBar(scrollBehavior = scrollBehavior, navigationIcon = {
            PerfIconButton(
                imageVector = PerfIcon.ArrowBack,
                onClick = {
                    mainVm.popPage()
                })
        }, title = {
            Text(text = "Work mode")
        })
    }) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
        ) {
            Card(
                modifier = Modifier
                    .padding(horizontal = itemHorizontalPadding)
                    .fillMaxWidth(),
                onClick = throttle { mainVm.updateAutomatorMode(AutomatorModeOption.A11yMode) },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorModeOption.A11yMode,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = AutomatorModeOption.A11yMode.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding)
                        .padding(start = 4.dp),
                    text = "Basic",
                    style = MaterialTheme.typography.titleSmall
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding)
                        .padding(start = 8.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "Grant \"accessibility permission\"",
                        "Must re-authorize after accessibility is turned off"
                    ),
                )
                AnimatedBooleanContent(
                    targetState = writeSecureSettings || a11yRunning,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = cardHorizontalPadding)
                                .padding(start = 8.dp, top = 4.dp),
                            text = "\"Accessibility permission\" already granted, you're all set",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    contentFalse = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = cardHorizontalPadding),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = throttle { openA11ySettings() },
                            ) {
                                Text(
                                    text = "Authorize manually",
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                            Text(
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .clickable(onClick = throttle {
                                        mainVm.navigateWebPage(ShortUrlSet.URL2)
                                    })
                                    .padding(horizontal = 4.dp),
                                text = "Can't turn on accessibility?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                )
                Text(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding)
                        .padding(start = 4.dp, top = 8.dp),
                    text = "Enhanced",
                    style = MaterialTheme.typography.titleSmall,
                )
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding)
                        .padding(start = 8.dp, top = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "Grant \"write secure settings\" permission",
                        "The app can control accessibility on/off by itself",
                    ),
                )
                AnimatedBooleanContent(
                    targetState = writeSecureSettings,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = cardHorizontalPadding)
                                .padding(start = 8.dp, top = 4.dp),
                            text = "\"Write secure settings\" permission already granted, this option is preferred",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    contentFalse = {
                        PrivilegeAuthButton(
                            modifier = Modifier.padding(horizontal = cardHorizontalPadding),
                        )
                    }
                )
                TextButton(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding),
                    onClick = throttle(vm.scope.launchAsFn {
                        if (!writeSecureSettings) {
                            toast("Please grant \"${PermissionStates.writeSecureSettings.name}\" first")
                        }
                        mainVm.dialogRequests.showMessage(
                            title = "Seamless keep-alive",
                            text = "Add a quick-settings tile\n\n1. Pull down the notification shade to the quick settings screen\n2. Find the tile named ${META.appName}\n3. Add this tile to the notification panel\n\nAs long as this tile is visible in the notification panel,\nwhether the system kills the background process or it crashes,\na simple pull-down and tap will restart it"
                        )
                    })
                ) {
                    Text(
                        text = "Seamless keep-alive",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .padding(horizontal = itemHorizontalPadding)
                    .fillMaxWidth(),
                onClick = throttle {
                    if (privilegeContext == null) {
                        mainVm.navigatePage(PrivilegeServiceRoute)
                        return@throttle
                    }
                    mainVm.updateAutomatorMode(AutomatorModeOption.AutomationMode)
                },
                colors = surfaceCardColors,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = automatorMode == AutomatorModeOption.AutomationMode,
                        onClick = null,
                    )
                    Text(
                        modifier = Modifier.padding(start = 12.dp),
                        text = AutomatorModeOption.AutomationMode.label,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextListItem(
                    modifier = Modifier
                        .padding(horizontal = cardHorizontalPadding)
                        .padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    list = listOf(
                        "Automation-driven accessibility",
                        "Won't cause display glitches",
                        "Won't be detected as accessibility by apps",
                        "If incompatible, configure \"partial accessibility\"",
                    ),
                )
                AnimatedBooleanContent(
                    targetState = privilegeContext != null,
                    contentTrue = {
                        Text(
                            modifier = Modifier
                                .padding(horizontal = cardHorizontalPadding)
                                .padding(start = 8.dp, top = 8.dp),
                            text = "Connected to the privileged service, you're all set",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    contentFalse = {
                        PrivilegeAuthButton(
                            modifier = Modifier.padding(
                                start = cardHorizontalPadding
                            )
                        )
                    }
                )
                TextButton(
                    modifier = Modifier.padding(start = cardHorizontalPadding),
                    onClick = throttle {
                        mainVm.navigatePage(A11YScopeAppListRoute)
                    },
                ) {
                    Text(
                        text = "Partial accessibility",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            Spacer(modifier = Modifier.height(EmptyHeight))
        }
    }

}

@Composable
private fun PrivilegeAuthButton(
    modifier: Modifier = Modifier,
) {
    val mainVm = LocalMainViewModel.current
    TextButton(
        modifier = modifier,
        onClick = throttle {
            mainVm.navigatePage(PrivilegeServiceRoute)
        },
    ) {
        Text(
            text = "Privileged service",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun TextListItem(
    list: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val lineHeightDp = LocalDensity.current.run { style.lineHeight.toDp() }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        list.forEach { text ->
            Row {
                Spacer(
                    modifier = Modifier
                        .padding(vertical = (lineHeightDp - 4.dp) / 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary)
                        .size(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = text, style = style)
            }
        }
    }
}
