package li.gkd.app.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import li.gkd.app.data.RawSubscription
import li.gkd.app.data.RuleComposer
import li.gkd.app.data.RuleFormState
import li.gkd.app.data.SubscriptionInputParser
import li.gkd.app.data.edit
import li.gkd.app.data.toRuleEditFormOrNull
import li.gkd.app.data.validateSelectorExpression
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.db.LOCAL_SUBS_ID

@Serializable
data class RuleBuilderRoute(
    val subsId: Long = LOCAL_SUBS_ID,
    val appId: String?,
    val activityId: String? = null,
    val initialSelector: String = "",
    val isGlobal: Boolean,
    // Non-null means "edit this existing rule" instead of creating a new one.
    val groupKey: Int? = null,
) : NavKey

/**
 * The guided "essentials, then advanced" form shown after a screen capture,
 * and reused (see [li.gkd.app.ui.component.RuleGroupState]) to edit an
 * existing rule when it's simple enough for the form to represent. Saves
 * into [RuleBuilderRoute.subsId] — the user's own local subscription
 * ([LOCAL_SUBS_ID]) by default, matching whichever editable subscription the
 * entry point that started the capture was already showing.
 *
 * Saving reuses the exact same parse/validate/persist pipeline the raw JSON5
 * editor ([UpsertRuleGroupVm]) uses: the form is serialized to the same JSON5
 * text shape by [RuleComposer.composeGroupText], then run through
 * [SubscriptionInputParser] and [li.gkd.app.data.SubscriptionEditor]. This
 * way the guided form can never produce a rule the app itself would reject.
 */
class RuleBuilderVm(private val route: RuleBuilderRoute) : BaseViewModel() {
    private val requiredSubscription = requiredSubscription(route.subsId)
    val isEdit = route.groupKey != null

    private val initialGroup: RawSubscription.RawGroupProps? = route.groupKey?.let { groupKey ->
        val subscription = requiredSubscription.state.value.value?.value ?: return@let null
        if (route.isGlobal) {
            subscription.globalGroups.find { it.key == groupKey }
        } else {
            subscription.getAppGroups(route.appId.orEmpty()).find { it.key == groupKey }
        }
    }

    // For editing a global rule, the scoped app (if any) is read back from
    // the rule itself rather than from wherever the edit was opened from.
    private val editForm = initialGroup?.toRuleEditFormOrNull(route.appId)
    private val effectiveAppId: String? =
        if (isEdit && route.isGlobal) editForm?.appId else route.appId

    val formFlow: StateFlow<RuleFormState>
        field = MutableStateFlow(
            editForm?.formState ?: RuleFormState(
                selector = route.initialSelector,
                activityId = route.activityId,
                scopeToActivity = !route.activityId.isNullOrBlank(),
            ),
        )

    val selectorErrorFlow = formFlow.mapNew { validateSelectorExpression(it.selector) }

    private fun update(transform: (RuleFormState) -> RuleFormState) {
        formFlow.value = transform(formFlow.value)
    }

    fun setName(value: String) = update { it.copy(name = value) }
    fun setDesc(value: String) = update { it.copy(desc = value) }
    fun setSelector(value: String) = update { it.copy(selector = value) }
    fun setAction(value: String) = update { it.copy(action = value) }
    fun setScopeToActivity(value: Boolean) = update { it.copy(scopeToActivity = value) }
    fun setMatchDelay(value: String) = update { it.copy(matchDelay = value) }
    fun setMatchTime(value: String) = update { it.copy(matchTime = value) }
    fun setActionCd(value: String) = update { it.copy(actionCd = value) }
    fun setActionDelay(value: String) = update { it.copy(actionDelay = value) }
    fun setActionMaximum(value: String) = update { it.copy(actionMaximum = value) }
    fun setResetMatch(value: String?) = update { it.copy(resetMatch = value) }
    fun setFastQuery(value: Boolean) = update { it.copy(fastQuery = value) }
    fun setMatchRoot(value: Boolean) = update { it.copy(matchRoot = value) }

    /**
     * Returns a user-facing error message on failure, or null on success
     * (the rule has been persisted by the time this returns).
     */
    suspend fun trySave(): String? {
        val state = formFlow.value
        if (state.name.isBlank()) return "Name is required"
        if (state.selector.isBlank()) return "Selector is required"
        if (validateSelectorExpression(state.selector) != null) {
            return "The selector is invalid"
        }
        val text = try {
            RuleComposer.composeGroupText(state, appId = effectiveAppId, isGlobal = route.isGlobal)
        } catch (e: Exception) {
            return e.message ?: "Failed to build the rule"
        }
        val groupKey = route.groupKey
        return try {
            if (groupKey != null) {
                val input = SubscriptionInputParser.parse(text, groupKey)
                requiredSubscription.update { subscription ->
                    subscription.edit {
                        if (route.isGlobal) {
                            val original = requireNotNull(initialGroup) as RawSubscription.RawGlobalGroup
                            replaceGlobalGroup(
                                groupKey,
                                original,
                                input.parseGlobalGroup().copy(key = groupKey),
                            )
                        } else {
                            val appId = route.appId ?: error("Missing app id")
                            val original = requireNotNull(initialGroup) as RawSubscription.RawAppGroup
                            replaceAppGroup(
                                targetApp = subscription.getApp(appId),
                                groupKey = groupKey,
                                expectedGroup = original,
                                newGroup = input.parseAppGroup(appId).copy(key = groupKey),
                            )
                        }
                    }
                }
            } else {
                val input = SubscriptionInputParser.parse(text, 0)
                requiredSubscription.update { subscription ->
                    subscription.edit {
                        if (route.isGlobal) {
                            appendGlobalGroup(input.parseGlobalGroup())
                        } else {
                            val appId = route.appId ?: error("Missing app id")
                            appendAppGroups(
                                targetApp = subscription.getApp(appId),
                                groups = input.parseAppGroups(appId),
                            )
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: "Failed to save the rule"
        }
    }
}
