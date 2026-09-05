package li.gkd.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.gkd.app.util.throttle

/**
 * Shown whenever the user starts adding a rule, before anything else — lets
 * them choose between the existing raw-JSON5 editor, starting a screen
 * capture (which leads into the guided rule builder), or pasting one copied
 * from elsewhere. [onPasteRule] is omitted at entry points that have no
 * single, unambiguous app to attach a pasted app-rule to.
 */
@Composable
fun AddRuleEntryDialog(
    onDismissRequest: () -> Unit,
    onTypeManually: () -> Unit,
    onStartCapture: () -> Unit,
    onPasteRule: (() -> Unit)? = null,
) {
    AppDialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            val itemModifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
            Text(
                text = "Start screen capture",
                modifier = Modifier
                    .clickable(onClick = throttle {
                        onDismissRequest()
                        onStartCapture()
                    })
                    .then(itemModifier),
            )
            Text(
                text = "Capture a screen, then build the rule step by step — no need to know the rule syntax.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text(
                text = "Type it in",
                modifier = Modifier
                    .clickable(onClick = throttle {
                        onDismissRequest()
                        onTypeManually()
                    })
                    .then(itemModifier),
            )
            Text(
                text = "Write the rule directly, using JSON5 syntax.",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onPasteRule != null) {
                HorizontalDivider()
                Text(
                    text = "Paste rule",
                    modifier = Modifier
                        .clickable(onClick = throttle {
                            onDismissRequest()
                            onPasteRule()
                        })
                        .then(itemModifier),
                )
                Text(
                    text = "Add a rule copied from another app, screen, or subscription.",
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
