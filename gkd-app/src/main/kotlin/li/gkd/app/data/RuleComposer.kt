package li.gkd.app.data

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import li.gkd.app.util.SubscriptionStore
import li.gkd.app.util.format
import li.gkd.app.util.pasteText
import li.gkd.app.util.toJson5String

/**
 * Builds starter JSON5 rule text for [UpsertRuleGroupPage] when it's opened from
 * the snapshot node inspector with a selector already built, instead of leaving
 * the editor blank. Mirrors the web inspect tool's
 * rule_composer.ts::composeRuleParts, scaled down to what the app's single-group
 * text editor accepts (a bare group object; `key` is auto-assigned by
 * SubscriptionInputParser when absent).
 */
object RuleComposer {

    /**
     * Starter text for a rule seeded from a selector built in the snapshot node
     * inspector. [activityId], when known, scopes the rule to the screen the
     * selector was captured from.
     */
    fun composeSeededGroupText(selector: String, activityId: String?): String {
        val now = System.currentTimeMillis().format("yyyy-MM-dd HH:mm:ss")
        val group = buildJsonObject {
            put("name", "[ChangeMe]RuleName-$now")
            put("desc", "[ChangeMe]Generated from a captured snapshot")
            putJsonArray("rules") {
                add(buildJsonObject {
                    put("matches", selector)
                    if (!activityId.isNullOrBlank()) {
                        put("activityIds", activityId)
                    }
                })
            }
        }
        return toJson5String(group)
    }

    /**
     * Serializes a [RuleFormState] from [RuleBuilderPage] into the same
     * single-group JSON5 text shape [composeSeededGroupText] produces, so it can
     * be fed through the same [SubscriptionInputParser] the raw text editor
     * uses — the guided form never needs its own parse/validate path.
     *
     * [appId] scopes an activity for a global rule (via `apps`); it's ignored
     * for app rules, which scope activities directly on the rule.
     *
     * [ruleName]/[preKey] are only used when appending this rule into an
     * *existing* multi-rule group (see [li.gkd.app.ui.RuleBuilderVm]) — a
     * plain new group's sole rule leaves both unset, since setting `name` on
     * that rule would make [toRuleEditFormOrNull] refuse to edit it later
     * through the guided form. The wrapping "group" object built below is
     * discarded in that case; only the composed rule itself is kept.
     */
    fun composeGroupText(
        formState: RuleFormState,
        appId: String?,
        isGlobal: Boolean,
        ruleName: String? = null,
        preKey: Int? = null,
    ): String {
        val rule = buildJsonObject {
            if (ruleName != null) put("name", ruleName)
            if (preKey != null) putJsonArray("preKeys") { add(preKey) }
            put("matches", formState.selector)
            if (formState.action.isNotBlank() && formState.action != "click") {
                put("action", formState.action)
            }
            val scopedActivityId = formState.activityId
            if (formState.scopeToActivity && !scopedActivityId.isNullOrBlank()) {
                if (isGlobal) {
                    putJsonArray("apps") {
                        add(buildJsonObject {
                            put("id", appId ?: error("Missing appId for activity scope"))
                            putJsonArray("activityIds") { add(scopedActivityId) }
                        })
                    }
                } else {
                    putJsonArray("activityIds") { add(scopedActivityId) }
                }
            }
            if (formState.action == "setText") {
                formState.text.takeIf { it.isNotBlank() }?.let { put("text", it) }
            }
            if (formState.action == "swipe") {
                val (start, end) = swipePositionsFor(formState.swipeDirection)
                val duration = formState.swipeDuration.trim().toLongOrNull() ?: 300L
                putJsonObject("swipeArg") {
                    putJsonObject("start") { put("x", start.first); put("y", start.second) }
                    putJsonObject("end") { put("x", end.first); put("y", end.second) }
                    put("duration", duration)
                }
            }
            formState.matchDelay.trim().toLongOrNull()?.let { put("matchDelay", it) }
            formState.matchTime.trim().toLongOrNull()?.let { put("matchTime", it) }
            formState.actionCd.trim().toLongOrNull()?.let { put("actionCd", it) }
            formState.actionDelay.trim().toLongOrNull()?.let { put("actionDelay", it) }
            formState.actionMaximum.trim().toIntOrNull()?.let { put("actionMaximum", it) }
            if (!formState.resetMatch.isNullOrBlank() && formState.resetMatch != "activity") {
                put("resetMatch", formState.resetMatch)
            }
            if (formState.fastQuery) put("fastQuery", true)
            if (formState.matchRoot) put("matchRoot", true)
        }
        val group = buildJsonObject {
            put("name", formState.name.trim())
            if (formState.desc.isNotBlank()) put("desc", formState.desc.trim())
            putJsonArray("rules") { add(rule) }
        }
        return toJson5String(group)
    }
}

/**
 * Parses the clipboard's text as a single rule group — the same bare-group
 * JSON5 shape [composeGroupText] produces, and what tapping the copy button
 * on an existing rule's detail dialog puts on the clipboard — and appends it
 * to [subsId], scoped to [appId] (global when null). Runs through the exact
 * same [SubscriptionInputParser]/[SubscriptionEditor] pipeline as every
 * other way of adding a rule, so a pasted rule can't skip validation.
 *
 * Returns a user-facing error message, or null on success.
 */
suspend fun pasteRuleFromClipboard(subsId: Long, appId: String?): String? {
    val text = pasteText() ?: return "Clipboard is empty"
    return try {
        val input = SubscriptionInputParser.parse(text, 0)
        SubscriptionStore.update(subsId) { subscription ->
            subscription.edit {
                if (appId == null) {
                    appendGlobalGroup(input.parseGlobalGroup())
                } else {
                    appendAppGroups(
                        targetApp = subscription.getApp(appId),
                        groups = input.parseAppGroups(appId),
                    )
                }
            }
        }
        null
    } catch (e: Exception) {
        e.message ?: "Failed to paste the rule"
    }
}

/**
 * The 4 directions the guided form's "Swipe" action offers. Each resolves to
 * a start/end point using the same `Position` expression syntax the JSON5
 * schema already supports for clickCenter/swipe (variables `left/top/right/
 * bottom/width/height`, evaluated against the matched element's own bounds
 * at the moment the rule fires) — inset 10% from the edge on both ends so
 * the gesture stays reliably inside the element rather than starting/ending
 * exactly on its border.
 */
enum class SwipeDirectionOption(val value: String, val label: String) {
    UP("up", "Up"),
    DOWN("down", "Down"),
    LEFT("left", "Left"),
    RIGHT("right", "Right"),
}

private fun swipePositionsFor(direction: String): Pair<Pair<String, String>, Pair<String, String>> {
    val midX = "(left+right)/2"
    val midY = "(top+bottom)/2"
    val nearTop = "top + height*0.1"
    val nearBottom = "bottom - height*0.1"
    val nearLeft = "left + width*0.1"
    val nearRight = "right - width*0.1"
    return when (direction) {
        "down" -> (midX to nearTop) to (midX to nearBottom)
        "left" -> (nearRight to midY) to (nearLeft to midY)
        "right" -> (nearLeft to midY) to (nearRight to midY)
        else -> (midX to nearBottom) to (midX to nearTop) // "up"
    }
}

/**
 * Form state collected by [RuleBuilderPage]. String fields that represent
 * numbers are kept as strings so the text fields can hold intermediate/
 * invalid input while typing; [RuleComposer.composeGroupText] only emits
 * ones that parse.
 */
data class RuleFormState(
    val name: String = "",
    val desc: String = "",
    val selector: String = "",
    val action: String = "click",
    val text: String = "",
    val swipeDirection: String = "up",
    val swipeDuration: String = "300",
    val scopeToActivity: Boolean = true,
    val activityId: String? = null,
    val matchDelay: String = "",
    val matchTime: String = "",
    val actionCd: String = "",
    val actionDelay: String = "",
    val actionMaximum: String = "1",
    val resetMatch: String? = null,
    val fastQuery: Boolean = false,
    val matchRoot: Boolean = false,
)

/**
 * A [RuleFormState] recovered from an existing group, plus the appId a
 * global rule's activity scope (if any) is actually attached to — read back
 * from the rule's own `apps` entry rather than whichever page the edit was
 * opened from, since that's what a save has to reproduce.
 */
data class RuleEditForm(
    val formState: RuleFormState,
    val appId: String?,
)

/**
 * Converts an existing group's single rule into the form [RuleBuilderPage]
 * can edit, or null if the group uses anything the guided form doesn't have
 * a control for — more than one rule, alternate/exclude selectors, a
 * swipe/position action, more than one activity/app scope, priority
 * windows, and so on. Editing then falls back to the JSON5 editor instead,
 * so nothing the group actually relies on gets silently dropped on save.
 *
 * [contextAppId] is the owning app for an app rule (required there);
 * ignored for a global rule.
 */
fun RawSubscription.RawGroupProps.toRuleEditFormOrNull(contextAppId: String?): RuleEditForm? {
    if (enable != null) return null
    if (!scopeKeys.isNullOrEmpty()) return null
    if (!hasOnlyGuidedFormGroupFields()) return null
    if (rules.size != 1) return null
    val rule = rules.single()
    if (rule.key != null) return null
    if (!rule.name.isNullOrBlank()) return null
    if (!rule.preKeys.isNullOrEmpty()) return null
    if (rule.position != null || rule.swipeArg != null) return null
    if (!rule.excludeMatches.isNullOrEmpty()) return null
    if (!rule.excludeAllMatches.isNullOrEmpty()) return null
    if (!rule.anyMatches.isNullOrEmpty()) return null
    if (!rule.hasOnlyGuidedFormRuleFields()) return null
    val matches = rule.matches
    if (matches == null || matches.size != 1) return null
    val selector = matches.single()
    val action = rule.action
    if (action == "swipe") return null

    var scopedAppId: String? = null
    var activityId: String? = null
    when (this) {
        is RawSubscription.RawGlobalGroup -> {
            if (!this.apps.isNullOrEmpty()) return null
            if (this.disableIfAppGroupMatch != null) return null
            val globalRule = rule as RawSubscription.RawGlobalRule
            if (globalRule.matchAnyApp != null) return null
            if (globalRule.matchSystemApp != null) return null
            if (globalRule.matchLauncher != null) return null
            val ruleApps = globalRule.apps
            if (!ruleApps.isNullOrEmpty()) {
                if (ruleApps.size != 1) return null
                val app = ruleApps.single()
                if (app.enable != null) return null
                if (app.versionCode != null || app.versionName != null) return null
                if (!app.excludeActivityIds.isNullOrEmpty()) return null
                val ids = app.activityIds
                if (ids != null && ids.size > 1) return null
                scopedAppId = app.id
                activityId = ids?.singleOrNull()
            }
        }

        is RawSubscription.RawAppGroup -> {
            if (this.versionCode != null || this.versionName != null) return null
            if (!this.excludeActivityIds.isNullOrEmpty()) return null
            if (!this.activityIds.isNullOrEmpty()) return null
            if (this.ignoreGlobalGroupMatch != null) return null
            val appRule = rule as RawSubscription.RawAppRule
            if (appRule.versionCode != null || appRule.versionName != null) return null
            if (!appRule.excludeActivityIds.isNullOrEmpty()) return null
            val ids = appRule.activityIds
            if (ids != null && ids.size > 1) return null
            activityId = ids?.singleOrNull()
            scopedAppId = contextAppId ?: return null
        }
    }

    val formState = RuleFormState(
        name = name,
        desc = desc.orEmpty(),
        selector = selector,
        action = action ?: "click",
        text = rule.text.orEmpty(),
        scopeToActivity = activityId != null,
        activityId = activityId,
        matchDelay = rule.matchDelay?.toString().orEmpty(),
        matchTime = rule.matchTime?.toString().orEmpty(),
        actionCd = rule.actionCd?.toString().orEmpty(),
        actionDelay = rule.actionDelay?.toString().orEmpty(),
        actionMaximum = rule.actionMaximum?.toString().orEmpty(),
        resetMatch = rule.resetMatch,
        fastQuery = rule.fastQuery ?: false,
        matchRoot = rule.matchRoot ?: false,
    )
    return RuleEditForm(formState, scopedAppId)
}

// Guided-form-produced groups only ever set these fields on the rule, never
// on the group itself — so a group carrying any of them wasn't (only) built
// by the guided form, and it isn't safe to fully round-trip through it.
private fun RawSubscription.RawGroupProps.hasOnlyGuidedFormGroupFields(): Boolean =
    actionCd == null && actionDelay == null && fastQuery == null && matchRoot == null &&
        matchDelay == null && matchTime == null && actionMaximum == null && resetMatch == null &&
        actionCdKey == null && actionMaximumKey == null && order == null && forcedTime == null &&
        snapshotUrls.isNullOrEmpty() && excludeSnapshotUrls.isNullOrEmpty() && exampleUrls.isNullOrEmpty() &&
        priorityTime == null && priorityActionMaximum == null

// The remaining RawCommonProps fields the guided form's Advanced section
// doesn't expose at all (rule-level).
private fun RawSubscription.RawRuleProps.hasOnlyGuidedFormRuleFields(): Boolean =
    actionCdKey == null && actionMaximumKey == null && order == null && forcedTime == null &&
        snapshotUrls.isNullOrEmpty() && excludeSnapshotUrls.isNullOrEmpty() && exampleUrls.isNullOrEmpty() &&
        priorityTime == null && priorityActionMaximum == null
