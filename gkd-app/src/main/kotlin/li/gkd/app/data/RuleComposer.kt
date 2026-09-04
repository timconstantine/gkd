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
}
