package li.gkd.app.data

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import li.gkd.app.util.format
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
     */
    fun composeGroupText(formState: RuleFormState, appId: String?, isGlobal: Boolean): String {
        val rule = buildJsonObject {
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
