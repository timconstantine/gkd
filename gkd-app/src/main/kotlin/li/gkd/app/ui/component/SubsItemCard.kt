package li.gkd.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import li.gkd.app.data.RawSubscription
import li.gkd.db.SubsItem
import li.gkd.app.util.formatTimeAgo
import li.gkd.app.util.throttle


@Composable
fun SubsItemCard(
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource,
    subsItem: SubsItem,
    subscription: RawSubscription?,
    index: Int,
    isSelectedMode: Boolean,
    isSelected: Boolean,
    loadError: Exception?,
    refreshError: Exception?,
    refreshing: Boolean,
    onOpen: () -> Unit,
    onCheckedChange: ((Boolean) -> Unit),
    onSelectedChange: (() -> Unit)? = null,
) {
    val dragged by interactionSource.collectIsDraggedAsState()
    val onClick = {
        if (!dragged) {
            if (isSelectedMode) {
                onSelectedChange?.invoke()
            } else if (!refreshing) {
                onOpen()
            }
        }
    }
    val containerColor = animateColorAsState(
        if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        tween()
    )
    Card(
        onClick = onClick,
        modifier = modifier
            .padding(16.dp, 4.dp)
            .semantics {
                stateDescription = if (isSelectedMode) {
                    if (isSelected) "Selected" else "Not selected"
                } else {
                    if (subsItem.enable) "Enabled" else "Disabled"
                }
                this.onClick(label = "View subscription details", action = null)
                this.onLongClick(label = "Enter multi-select mode", action = null)
            },
        shape = MaterialTheme.shapes.small,
        interactionSource = interactionSource,
        colors = CardDefaults.cardColors(
            containerColor = containerColor.value
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subscription != null) {
                    Text(
                        modifier = Modifier.semantics {
                            contentDescription = "Subscription order: $index, name: ${subscription.name}"
                        },
                        text = "$index. ${subscription.name}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = subscription.numText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (subscription.groupsSize == 0) {
                            LocalContentColor.current.copy(alpha = 0.5f)
                        } else {
                            LocalContentColor.current
                        }
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!subsItem.isLocal) {
                            if (subscription.author != null) {
                                Text(
                                    modifier = Modifier.semantics {
                                        contentDescription = "Author ${subscription.author}"
                                    },
                                    text = subscription.author,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            Text(
                                modifier = Modifier.semantics {
                                    contentDescription = "Subscription version ${subscription.version}"
                                },
                                text = "v" + (subscription.version.toString()),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        } else {
                            // Visually calls out a locally-editable rule
                            // collection (the built-in one, or one the user
                            // created by name) against a remote subscription,
                            // which shows author/version above instead.
                            Box(
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    modifier = Modifier.clearAndSetSemantics {
                                        contentDescription = "Your own editable rule collection"
                                    },
                                    text = "Your rules",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        val timeStr = formatTimeAgo(subsItem.mtime)
                        Text(
                            modifier = Modifier.semantics {
                                contentDescription = "Updated $timeStr"
                            },
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                } else {
                    Text(
                        text = "id=${subsItem.id}",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val color = if (loadError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    }
                    Text(
                        text = loadError?.message
                            ?: if (refreshing) "Loading..." else "File does not exist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = color
                    )
                }
                if (refreshError != null) {
                    Text(
                        text = "Update error: ${refreshError.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            val percent = usePercentAnimatable(!isSelectedMode)
            val switchModifier = Modifier.graphicsLayer(
                alpha = 0.5f + (1 - 0.5f) * percent.value,
            ).run {
                if (isSelectedMode) {
                    minimumInteractiveComponentSize()
                } else {
                    this
                }
            }
            PerfSwitch(
                key = subsItem.id,
                modifier = switchModifier,
                checked = subsItem.enable,
                onCheckedChange = if (isSelectedMode) null else throttle(fn = onCheckedChange),
            )
        }
    }
}






















