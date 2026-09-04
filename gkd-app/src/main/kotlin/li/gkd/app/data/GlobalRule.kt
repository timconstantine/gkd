package li.gkd.app.data

import li.gkd.app.a11y.launcherAppId
import li.gkd.app.util.systemAppsFlow

data class GlobalApp(
    val id: String,
    val enable: Boolean,
    val activityIds: List<String>,
    val excludeActivityIds: List<String>,
)

class GlobalRule(
    rule: RawSubscription.RawGlobalRule,
    g: ResolvedGlobalGroup,
    appInfoCache: Map<String, AppInfo>,
) : ResolvedRule(
    rule = rule,
    g = g,
) {
    val groupExcludeAppIds = g.groupExcludeAppIds
    val group = g.group
    private val matchAnyApp = rule.matchAnyApp ?: group.matchAnyApp ?: true
    private val matchLauncher = rule.matchLauncher ?: group.matchLauncher ?: false
    private val matchSystemApp = rule.matchSystemApp ?: group.matchSystemApp ?: false
    val apps = mutableMapOf<String, GlobalApp>().apply {
        (rule.apps ?: group.apps ?: emptyList()).filter { a ->
            // https://github.com/gkd-kit/gkd/issues/619
            appInfoCache.isEmpty() || appInfoCache.containsKey(a.id) // Filter out apps that aren't installed
        }.forEach { a ->
            val enable = a.enable ?: appInfoCache[a.id]?.let { appInfo ->
                if (a.versionCode?.match(appInfo.versionCode) == false) {
                    return@let false
                }
                if (a.versionName?.match(appInfo.versionName) == false) {
                    return@let false
                }
                null
            } ?: true
            this[a.id] = GlobalApp(
                id = a.id,
                enable = enable,
                activityIds = getFixActivityIds(a.id, a.activityIds),
                excludeActivityIds = getFixActivityIds(a.id, a.excludeActivityIds),
            )
        }
    }

    override val type = "global"

    private val excludeAppIds = apps.filter { e ->
        !e.value.enable
    }.keys

    private val enableApps = apps.filter { e -> e.value.enable }

    /**
     * Built-in disable > user config > rule's own default
     * The more precise the scope, the higher the priority
     */
    override fun matchActivity(appId: String, activityId: String?): Boolean {
        // Disabled by the rule itself
        if (excludeAppIds.contains(appId) || groupExcludeAppIds.contains(appId)) {
            return false
        }

        // Disabled by the user
        if (excludeData.excludeAppIds.contains(appId)) {
            return false
        }
        if (activityId != null && excludeData.activityIds.contains(appId to activityId)) {
            return false
        }
        if (excludeData.includeAppIds.contains(appId)) {
            activityId ?: return true
            val app = enableApps[appId] ?: return true
            // Page-level disable that's part of the rule itself
            return !app.excludeActivityIds.any { e -> e.startsWith(activityId) }
        }

        // Scope comparison
        val app = enableApps[appId]
        if (app != null) { // Enabled by the rule's own custom config
            activityId ?: return true
            return app.activityIds.isEmpty() || app.activityIds.any { e -> e.startsWith(activityId) }
        } else {
            if (!matchLauncher && appId == launcherAppId) {
                return false
            }
            if (!matchSystemApp && systemAppsFlow.value.contains(appId)) {
                return false
            }
            return matchAnyApp
        }
    }

}
