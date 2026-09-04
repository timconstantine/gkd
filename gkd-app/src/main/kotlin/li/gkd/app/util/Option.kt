package li.gkd.app.util

import androidx.compose.ui.graphics.vector.ImageVector
import li.gkd.app.ui.component.PerfIcon

sealed interface Option<T> {
    val value: T
    val label: String
    val options: List<Option<T>>
}

sealed interface OptionIcon {
    val icon: ImageVector
}

sealed interface OptionMenuLabel {
    val menuLabel: String
}

fun <V, T : Option<V>> Iterable<T>.findOption(value: V): T {
    return find { it.value == value } ?: first()
}

sealed class AppSortOption(override val value: Int, override val label: String) : Option<Int> {
    override val options get() = objects

    data object ByAppName : AppSortOption(0, "By app name")
    data object ByActionTime : AppSortOption(2, "By last triggered")
    data object ByUsedTime : AppSortOption(3, "By last used")

    companion object {
        val objects by lazy { listOf(ByAppName, ByUsedTime, ByActionTime) }
    }
}

sealed class UpdateTimeOption(
    override val value: Long,
    override val label: String
) : Option<Long> {
    override val options get() = objects

    data object Pause : UpdateTimeOption(-1, "Paused")
    data object Everyday : UpdateTimeOption(24 * 60 * 60_000, "Every day")
    data object Every3Days : UpdateTimeOption(24 * 60 * 60_000 * 3, "Every 3 days")
    data object Every7Days : UpdateTimeOption(24 * 60 * 60_000 * 7, "Every 7 days")

    companion object {
        val objects by lazy { listOf(Pause, Everyday, Every3Days, Every7Days) }
    }
}

sealed class DarkThemeOption(
    override val value: Boolean?,
    override val label: String,
    override val menuLabel: String,
    override val icon: ImageVector
) : Option<Boolean?>, OptionIcon, OptionMenuLabel {
    override val options get() = objects

    data object FollowSystem : DarkThemeOption(null, "Auto", "Auto", PerfIcon.AutoMode)
    data object AlwaysEnable : DarkThemeOption(true, "Enabled", "Dark", PerfIcon.DarkMode)
    data object AlwaysDisable : DarkThemeOption(false, "Disabled", "Light", PerfIcon.LightMode)

    companion object {
        val objects by lazy { listOf(FollowSystem, AlwaysEnable, AlwaysDisable) }
    }
}

sealed class EnableGroupOption(
    override val value: Boolean?,
    override val label: String
) : Option<Boolean?> {
    override val options get() = objects

    data object FollowSubs : EnableGroupOption(null, "Follow subscription")
    data object AllEnable : EnableGroupOption(true, "Enable all")
    data object AllDisable : EnableGroupOption(false, "Disable all")

    companion object {
        val objects by lazy { listOf(FollowSubs, AllEnable, AllDisable) }
    }
}

sealed class RuleSortOption(override val value: Int, override val label: String) : Option<Int> {
    override val options get() = objects

    data object ByDefault : RuleSortOption(0, "By default order")
    data object ByActionTime : RuleSortOption(1, "By last triggered")
    data object ByRuleName : RuleSortOption(2, "By rule name")

    companion object {
        val objects by lazy { listOf(ByDefault, ByActionTime, ByRuleName) }
    }
}

sealed class UpdateChannelOption(
    override val value: Int,
    override val label: String,
    val url: String
) : Option<Int> {
    override val options get() = objects

    data object Stable : UpdateChannelOption(
        0,
        "Stable",
        "https://registry.npmmirror.com/@gkd-kit/app/latest/files/index.json"
    )

    data object Beta : UpdateChannelOption(
        1,
        "Beta",
        "https://registry.npmmirror.com/@gkd-kit/app-beta/latest/files/index.json"
    )

    companion object {
        val objects by lazy { listOf(Stable, Beta) }
    }
}

sealed interface BinaryOption : Option<Int> {
    fun include(flag: Int): Boolean = (value and flag) != 0
    fun invert(flag: Int): Int = value xor flag

    companion object {
        fun combine(options: Collection<BinaryOption>): Int {
            return options.fold(0) { a, b -> a or b.value }
        }
    }
}


sealed class AppGroupOption(
    override val value: Int,
    override val label: String
) : BinaryOption {
    override val options get() = allObjects

    data object SystemGroup : AppGroupOption(1 shl 0, "System apps")
    data object UserGroup : AppGroupOption(1 shl 1, "User apps")
    data object UnInstalledGroup : AppGroupOption(1 shl 2, "Not installed")

    companion object {
        val normalObjects by lazy { listOf(SystemGroup, UserGroup) }
        val allObjects by lazy { listOf(SystemGroup, UserGroup, UnInstalledGroup) }
    }
}

sealed class AutomatorModeOption(
    override val value: Int,
    override val label: String,
) : Option<Int> {
    override val options get() = objects

    data object A11yMode : AutomatorModeOption(1, "Accessibility")
    data object AutomationMode : AutomatorModeOption(2, "Automation")

    companion object {
        val objects by lazy { listOf(A11yMode, AutomationMode) }
    }
}

sealed class CaptureTriggerOption(
    override val value: Int,
    override val label: String,
) : Option<Int> {
    override val options get() = objects

    // General-purpose, app-agnostic triggers only — usable regardless of
    // which app is currently in the foreground. Screenshot-triggered
    // capture is deliberately excluded: it needs its own per-app/event
    // pre-configuration (see SnapshotSettingsVm.setCaptureScreenshot), so
    // it isn't a generic "capture now" trigger.
    data object FloatingButton : CaptureTriggerOption(0, "Floating button")
    data object VolumeKey : CaptureTriggerOption(1, "Volume key")

    companion object {
        val objects by lazy { listOf(FloatingButton, VolumeKey) }
    }
}

