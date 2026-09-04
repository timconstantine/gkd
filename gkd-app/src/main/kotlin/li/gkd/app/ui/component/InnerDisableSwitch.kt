package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import li.gkd.app.ui.share.LocalMainViewModel
import li.gkd.app.util.launchAsFn
import li.gkd.app.util.throttle

@Composable
fun InnerDisableSwitch(
    modifier: Modifier = Modifier,
    valid: Boolean = true,
    isSelectedMode: Boolean = false,
) {
    val mainVm = LocalMainViewModel.current
    val onClick = mainVm.scope.launchAsFn {
        mainVm.dialogRequests.showMessage(
            title = if (valid) "Built-in disabled" else "Invalid rule",
            text = if (valid) {
                "This rule has already been internally configured to be disabled for the current app; forcing it on would be meaningless or have no effect\n\nNote: this usually happens when this global rule can't be adapted to, skips adaptation, or is adapted separately for the current app"
            } else {
                "The rule has an error and cannot be enabled"
            },
        )
    }
    PerfSwitch(
        checked = false,
        enabled = false,
        onCheckedChange = null,
        modifier = modifier.semantics {
            stateDescription = "Disabled"
        }
            .minimumInteractiveComponentSize().run {
                if (isSelectedMode) {
                    this
                } else {
                    clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Switch,
                        onClick = throttle(onClick),
                        onClickLabel = "Open the rule-disabled explanation",
                    )
                }
            }
    )
}
