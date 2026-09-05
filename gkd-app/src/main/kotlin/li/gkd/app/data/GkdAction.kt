package li.gkd.app.data

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import li.gkd.app.a11y.A11yRuleEngine
import li.gkd.app.priv.privilegeContextFlow
import li.gkd.app.priv.toHidden
import li.gkd.app.service.A11yService
import li.gkd.app.service.TrackService
import li.gkd.app.util.ScreenUtils
import kotlin.time.Duration.Companion.milliseconds

@Serializable
data class GkdAction(
    val selector: String,
    val fastQuery: Boolean = false,
    val action: String? = null,
    override val position: RawSubscription.Position? = null,
    override val swipeArg: RawSubscription.SwipeArg? = null,
    override val text: String? = null,
) : RawSubscription.LocationProps

@Serializable
data class ActionResult(
    val action: String,
    val result: Boolean,
    val shell: Boolean = false,
    val position: Pair<Float, Float>? = null,
)

sealed class ActionPerformer(val action: String) {
    abstract suspend fun perform(
        node: AccessibilityNodeInfo,
        locationProps: RawSubscription.LocationProps,
    ): ActionResult

    data object ClickNode : ActionPerformer("clickNode") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            TrackService.addA11yNodePosition(node)
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
        }
    }

    data object ClickCenter : ActionPerformer("clickCenter") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rect = node.toHidden.boundsInScreen
            val p = locationProps.position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            if (!ScreenUtils.inScreen(x, y)) {
                return ActionResult(
                    action = action,
                    result = false,
                    position = x to y,
                )
            }
            TrackService.addXyPosition(x, y)
            return ActionResult(
                action = action,
                result = if (
                    privilegeContextFlow.value?.run {
                        inputManager.tap(x, y)
                    } == true
                ) {
                    true
                } else {
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, ViewConfiguration.getTapTimeout().toLong()
                        )
                    )
                    A11yService.instance?.dispatchGesture(
                        gestureDescription.build(), null, null
                    ) != null
                },
                position = x to y
            )
        }
    }

    data object Click : ActionPerformer("click") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            if (node.isClickable) {
                val result = ClickNode.perform(node, locationProps)
                if (result.result) {
                    return result
                }
            }
            return ClickCenter.perform(node, locationProps)
        }
    }

    data object LongClickNode : ActionPerformer("longClickNode") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            TrackService.addA11yNodePosition(node)
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK).apply {
                    if (this) {
                        delay(LongClickCenter.LONG_DURATION.milliseconds)
                    }
                }
            )
        }
    }

    data object LongClickCenter : ActionPerformer("longClickCenter") {
        const val LONG_DURATION = 500L
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rect = node.toHidden.boundsInScreen
            val p = locationProps.position?.calc(rect)
            val x = p?.first ?: ((rect.right + rect.left) / 2f)
            val y = p?.second ?: ((rect.bottom + rect.top) / 2f)
            // On some systems, ViewConfiguration.getLongPressTimeout() returns 300, which triggers a regular click event instead
            if (!ScreenUtils.inScreen(x, y)) {
                return ActionResult(
                    action = action,
                    result = false,
                    position = x to y,
                )
            }
            TrackService.addXyPosition(x, y)
            return ActionResult(
                action = action,
                result = if (
                    privilegeContextFlow.value?.run {
                        inputManager.tap(x, y, LONG_DURATION)
                    } == true
                ) {
                    true
                } else {
                    val gestureDescription = GestureDescription.Builder()
                    val path = Path()
                    path.moveTo(x, y)
                    gestureDescription.addStroke(
                        GestureDescription.StrokeDescription(
                            path, 0, LONG_DURATION
                        )
                    )
                    (A11yService.instance?.dispatchGesture(
                        gestureDescription.build(), null, null
                    ) != null).apply {
                        if (this) {
                            delay(LONG_DURATION.milliseconds)
                        }
                    }
                },
                position = x to y
            )
        }
    }

    data object LongClick : ActionPerformer("longClick") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            if (node.isLongClickable) {
                val result = LongClickNode.perform(node, locationProps)
                if (result.result) {
                    return result
                }
            }
            return LongClickCenter.perform(node, locationProps)
        }
    }

    data object SetText : ActionPerformer("setText") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rawText = locationProps.text
            // Same tier as ACTION_CLICK/ACTION_LONG_CLICK above: a direct
            // node action via the accessibility service, no root/Shizuku
            // needed. There's no coordinate-based fallback for typing, so
            // this just fails cleanly if the node can't take text.
            if (rawText == null || !node.isEditable) {
                return ActionResult(action = action, result = false)
            }
            TrackService.addA11yNodePosition(node)
            val arguments = Bundle().apply {
                putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    expandTextTemplate(rawText),
                )
            }
            return ActionResult(
                action = action,
                result = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments),
            )
        }
    }

    data object Back : ActionPerformer("back") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = A11yRuleEngine.performActionBack()
            )
        }
    }

    data object None : ActionPerformer("none") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            return ActionResult(
                action = action,
                result = true
            )
        }
    }

    data object Swipe : ActionPerformer("swipe") {
        override suspend fun perform(
            node: AccessibilityNodeInfo,
            locationProps: RawSubscription.LocationProps,
        ): ActionResult {
            val rect = node.toHidden.boundsInScreen
            val swipeArg = locationProps.swipeArg ?: return ActionResult(
                action = action,
                result = false,
            )
            val startP = swipeArg.start.calc(rect)
            val endP = swipeArg.end?.calc(rect) ?: startP
            if (startP == null || endP == null) {
                return ActionResult(
                    action = action,
                    result = false,
                )
            }
            val startX = startP.first
            val startY = startP.second
            val endX = endP.first
            val endY = endP.second
            if (!(ScreenUtils.inScreen(startX, startY) && ScreenUtils.inScreen(endX, endY))) {
                return ActionResult(
                    action = action,
                    result = false,
                    position = endX to endY,
                )
            }
            TrackService.addSwipePosition(startX, startY, endX, endY, swipeArg.duration)
            return if (
                privilegeContextFlow.value?.run {
                    inputManager.swipe(
                        startX,
                        startY,
                        endX,
                        endY,
                        swipeArg.duration
                    )
                } == true
            ) {
                ActionResult(
                    action = action,
                    result = true,
                    shell = true,
                    position = endX to endY,
                )
            } else {
                val gestureDescription = GestureDescription.Builder()
                val path = Path()
                path.moveTo(startX, startY)
                path.lineTo(endX, endY)
                gestureDescription.addStroke(
                    GestureDescription.StrokeDescription(
                        path, 0, swipeArg.duration
                    )
                )
                ActionResult(
                    action = action,
                    result = (A11yService.instance?.dispatchGesture(
                        gestureDescription.build(), null, null
                    ) != null).apply {
                        if (this) {
                            delay(swipeArg.duration.milliseconds)
                        }
                    },
                    position = endX to endY,
                )
            }
        }
    }

    companion object {
        private val allSubObjects by lazy {
            arrayOf(
                ClickNode,
                ClickCenter,
                Click,
                LongClickNode,
                LongClickCenter,
                LongClick,
                SetText,
                Back,
                None,
                Swipe,
            )
        }

        fun getAction(action: String?): ActionPerformer {
            return allSubObjects.find { it.action == action } ?: Click
        }
    }
}
