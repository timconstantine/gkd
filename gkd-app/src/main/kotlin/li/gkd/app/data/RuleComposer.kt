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
    val actionMaximum: String = "",
    val resetMatch: String? = null,
    val fastQuery: Boolean = false,
    val matchRoot: Boolean = false,
)
