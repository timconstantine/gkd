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
import li.gkd.app.ui.component.updateRuleGroupEnable
import li.gkd.app.ui.share.BaseViewModel
import li.gkd.db.Db
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
    // Non-null means "add a new rule into this existing group" instead of
    // creating a new group or editing an existing rule. Mutually exclusive
    // with groupKey.
    val appendToGroupKey: Int? = null,
) : NavKey

/** One other rule already in the target group, offered as a predecessor. */
data class SiblingRuleOption(val index: Int, val label: String)

/**
 * The guided "essentials, then advanced" form shown after a screen capture,
 * and reused (see [li.gkd.app.ui.component.RuleGroupState]) to edit an
 * existing rule, or to add an additional rule into an existing multi-rule
 * group, when either is simple enough for the form to represent. Saves
 * into [RuleBuilderRoute.subsId] — the user's own local subscription
 * ([LOCAL_SUBS_ID]) by default, matching whichever editable subscription the
 * entry point that started the capture was already showing.
 *
 * Saving reuses the exact same parse/validate/persist pipeline the raw JSON5
 * editor ([UpsertRuleGroupVm]) uses: the form is serialized to the same JSON5
 * text shape by [RuleComposer.composeGroupText], then run through
 * [SubscriptionInputParser] and [li.gkd.app.data.SubscriptionEditor]. This
 * way the guided form can never produce a rule the app itself would reject.
 *
 * [RuleBuilderRoute.isGlobal]/[RuleBuilderRoute.appId] are only the *initial*
 * scope for a new rule — [isGlobalFlow]/[selectedAppIdFlow] let the user
 * change either before saving (see [setIsGlobal]/[setSelectedAppId]); both
 * stay fixed on an edit or an append, since flipping scope would move the
 * rule between subscription lists (or groups) entirely.
 */
class RuleBuilderVm(private val route: RuleBuilderRoute) : BaseViewModel() {
    private val requiredSubscription = requiredSubscription(route.subsId)
    val isEdit = route.groupKey != null
    val isAppendToGroup = route.appendToGroupKey != null

    private val initialGroup: RawSubscription.RawGroupProps? = route.groupKey?.let { groupKey ->
        val subscription = requiredSubscription.state.value.value?.value ?: return@let null
        if (route.isGlobal) {
            subscription.globalGroups.find { it.key == groupKey }
        } else {
            subscription.getAppGroups(route.appId.orEmpty()).find { it.key == groupKey }
        }
    }

    // The existing group a new rule is being added into — read once, same
    // as initialGroup above; trySave() re-reads the live value before
    // actually persisting, so a stale read here can't lose concurrent edits.
    private val appendTargetGroup: RawSubscription.RawGroupProps? = route.appendToGroupKey?.let { groupKey ->
        val subscription = requiredSubscription.state.value.value?.value ?: return@let null
        if (route.isGlobal) {
            subscription.globalGroups.find { it.key == groupKey }
        } else {
            subscription.getAppGroups(route.appId.orEmpty()).find { it.key == groupKey }
        }
    }

    // Existing rules in the target group, offered as predecessors — shown by
    // name, falling back to a positional label for rules that don't have one
    // (e.g. any rule the guided form itself created before this feature).
    val siblingRuleOptions: List<SiblingRuleOption> =
        appendTargetGroup?.rules.orEmpty().mapIndexed { index, r ->
            SiblingRuleOption(index, r.name?.takeIf { it.isNotBlank() } ?: "Rule ${index + 1}")
        }

    val predecessorIndexFlow: StateFlow<Int?>
        field = MutableStateFlow(null)

    fun setPredecessorIndex(value: Int?) {
        predecessorIndexFlow.value = value
    }

    // For editing a global rule, the scoped app (if any) is read back from
    // the rule itself rather than from wherever the edit was opened from.
    private val editForm = initialGroup?.toRuleEditFormOrNull(route.appId)

    // Global-vs-app-specific is only choosable when creating a brand new
    // group — changing it for an existing rule, or for one being added into
    // an existing group, is a structural change (which subscription list /
    // group the rule lives in) better done via delete + recreate, so these
    // flows just stay fixed at the route's original values otherwise.
    val isGlobalFlow: StateFlow<Boolean>
        field = MutableStateFlow(route.isGlobal)
    val selectedAppIdFlow: StateFlow<String?>
        field = MutableStateFlow(if (isEdit && route.isGlobal) editForm?.appId else route.appId)

    fun setIsGlobal(value: Boolean) {
        if (isEdit || isAppendToGroup) return
        isGlobalFlow.value = value
    }

    fun setSelectedAppId(value: String?) {
        if (isEdit || isAppendToGroup) return
        selectedAppIdFlow.value = value
    }

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
    fun setText(value: String) = update { it.copy(text = value) }
    fun setSwipeDirection(value: String) = update { it.copy(swipeDirection = value) }
    fun setSwipeDuration(value: String) = update { it.copy(swipeDuration = value) }
    fun setScopeToActivity(value: Boolean) = update { it.copy(scopeToActivity = value) }
    fun setMatchDelay(value: String) = update { it.copy(matchDelay = value) }
    fun setMatchTime(value: String) = update { it.copy(matchTime = value) }
    fun setActionCd(value: String) = update { it.copy(actionCd = value) }
    fun setActionDelay(value: String) = update { it.copy(actionDelay = value) }
    fun setActionMaximum(value: String) = update { it.copy(actionMaximum = value) }
    fun setResetMatch(value: String?) = update { it.copy(resetMatch = value) }
    fun setFastQuery(value: Boolean) = update { it.copy(fastQuery = value) }
    fun setMatchRoot(value: Boolean) = update { it.copy(matchRoot = value) }

    private fun backfillAppRuleKeys(rules: List<RawSubscription.RawAppRule>): List<RawSubscription.RawAppRule> {
        var nextKey = (rules.mapNotNull { it.key }.maxOrNull() ?: -1) + 1
        return rules.map { r -> if (r.key != null) r else r.copy(key = nextKey++) }
    }

    private fun backfillGlobalRuleKeys(rules: List<RawSubscription.RawGlobalRule>): List<RawSubscription.RawGlobalRule> {
        var nextKey = (rules.mapNotNull { it.key }.maxOrNull() ?: -1) + 1
        return rules.map { r -> if (r.key != null) r else r.copy(key = nextKey++) }
    }

    /**
     * Adds this form's rule into the existing group identified by
     * [appendToGroupKey], instead of creating a new group. Any sibling rule
     * that doesn't have a `key` yet (every rule the guided form made before
     * this feature existed) gets one backfilled first, so it can actually be
     * referenced as a predecessor — by this new rule, or a future one.
     */
    private suspend fun trySaveAppend(appendToGroupKey: Int, state: RuleFormState): String? {
        val isGlobal = isGlobalFlow.value
        val appId = selectedAppIdFlow.value
        val ruleName = state.name.trim()
        val predecessorIndex = predecessorIndexFlow.value
        return try {
            requiredSubscription.update { subscription ->
                subscription.edit {
                    if (isGlobal) {
                        val original = subscription.globalGroups.find { it.key == appendToGroupKey }
                            ?: error("The rule group no longer exists")
                        val backfilled = backfillGlobalRuleKeys(original.rules)
                        val preKey = predecessorIndex?.let { backfilled.getOrNull(it)?.key }
                        val text = RuleComposer.composeGroupText(
                            state, appId = null, isGlobal = true, ruleName = ruleName, preKey = preKey,
                        )
                        val parsedRule = SubscriptionInputParser.parse(text, 0).parseGlobalGroup().rules.single()
                        val newKey = (backfilled.mapNotNull { it.key }.maxOrNull() ?: -1) + 1
                        replaceGlobalGroup(
                            appendToGroupKey,
                            original,
                            original.copy(rules = backfilled + parsedRule.copy(key = newKey)),
                        )
                    } else {
                        val targetAppId = appId ?: error("Missing app id")
                        val original = subscription.getAppGroups(targetAppId).find { it.key == appendToGroupKey }
                            ?: error("The rule group no longer exists")
                        val backfilled = backfillAppRuleKeys(original.rules)
                        val preKey = predecessorIndex?.let { backfilled.getOrNull(it)?.key }
                        val text = RuleComposer.composeGroupText(
                            state, appId = targetAppId, isGlobal = false, ruleName = ruleName, preKey = preKey,
                        )
                        val parsedRule =
                            SubscriptionInputParser.parse(text, 0).parseAppGroup(targetAppId).rules.single()
                        val newKey = (backfilled.mapNotNull { it.key }.maxOrNull() ?: -1) + 1
                        replaceAppGroup(
                            targetApp = subscription.getApp(targetAppId),
                            groupKey = appendToGroupKey,
                            expectedGroup = original,
                            newGroup = original.copy(rules = backfilled + parsedRule.copy(key = newKey)),
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: "Failed to save the rule"
        }
    }

    /**
     * Returns a user-facing error message on failure, or null on success
     * (the rule has been persisted by the time this returns).
     */
    suspend fun trySave(): String? {
        val state = formFlow.value
        val isGlobal = isGlobalFlow.value
        val appId = selectedAppIdFlow.value
        if (state.name.isBlank()) return "Name is required"
        if (state.selector.isBlank()) return "Selector is required"
        if (validateSelectorExpression(state.selector) != null) {
            return "The selector is invalid"
        }
        if (state.action == "setText" && state.text.isBlank()) {
            return "Text to enter is required"
        }
        if (!isGlobal && appId.isNullOrBlank()) {
            return "Choose which app this rule applies to"
        }
        val appendToGroupKey = route.appendToGroupKey
        if (appendToGroupKey != null) {
            // A rule added to an already-existing group just inherits that
            // group's own enabled state — there's no separate "enable this
            // one rule" concept, so no extra enable step is needed here.
            return trySaveAppend(appendToGroupKey, state)
        }
        val text = try {
            RuleComposer.composeGroupText(state, appId = appId, isGlobal = isGlobal)
        } catch (e: Exception) {
            return e.message ?: "Failed to build the rule"
        }
        val groupKey = route.groupKey
        // Whether this subscription had no rules at all before this save —
        // used below to also enable the subscription itself, so a rule
        // that's otherwise ready to run isn't silently inert because its
        // subscription was never turned on.
        val subsWasEmpty = requiredSubscription.requireValue().let {
            it.globalGroups.isEmpty() && it.apps.all { app -> app.groups.isEmpty() }
        }
        return try {
            var newGroup: RawSubscription.RawGroupProps? = null
            if (groupKey != null) {
                val input = SubscriptionInputParser.parse(text, groupKey)
                requiredSubscription.update { subscription ->
                    subscription.edit {
                        if (isGlobal) {
                            val original = requireNotNull(initialGroup) as RawSubscription.RawGlobalGroup
                            replaceGlobalGroup(
                                groupKey,
                                original,
                                input.parseGlobalGroup().copy(key = groupKey),
                            )
                        } else {
                            val targetAppId = appId ?: error("Missing app id")
                            val original = requireNotNull(initialGroup) as RawSubscription.RawAppGroup
                            replaceAppGroup(
                                targetApp = subscription.getApp(targetAppId),
                                groupKey = groupKey,
                                expectedGroup = original,
                                newGroup = input.parseAppGroup(targetAppId).copy(key = groupKey),
                            )
                        }
                    }
                }
            } else {
                val input = SubscriptionInputParser.parse(text, 0)
                requiredSubscription.update { subscription ->
                    subscription.edit {
                        if (isGlobal) {
                            newGroup = appendGlobalGroup(input.parseGlobalGroup())
                        } else {
                            val targetAppId = appId ?: error("Missing app id")
                            newGroup = appendAppGroups(
                                targetApp = subscription.getApp(targetAppId),
                                groups = input.parseAppGroups(targetAppId),
                            ).groups.last()
                        }
                    }
                }
            }
            // New rules are enabled by default already (no config row means
            // "enabled"), but write it explicitly rather than lean on that
            // default, and bring the subscription itself online too if this
            // was its very first rule — otherwise a freshly-built rule can
            // sit there doing nothing because nobody ever turned the
            // subscription on.
            val createdGroup = newGroup
            if (createdGroup != null) {
                updateRuleGroupEnable(
                    subscription = requiredSubscription.requireValue(),
                    appId = if (isGlobal) null else appId,
                    group = createdGroup,
                    subsConfig = null,
                    enabled = true,
                )
                if (subsWasEmpty) {
                    Db.subsItemDao.updateEnable(route.subsId, true)
                }
            }
            null
        } catch (e: Exception) {
            e.message ?: "Failed to save the rule"
        }
    }
}
