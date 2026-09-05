package li.gkd.app.data

import li.gkd.app.util.format

/**
 * Live-value placeholders a `setText` rule's [RawSubscription.LocationProps.text]
 * can contain. Expanded by [expandTextTemplate] at the moment the action
 * actually runs — not when the rule is authored — so e.g. `[today]` always
 * reflects the day the rule fires, not the day it was written.
 */
enum class TextTemplateToken(val token: String, val label: String, val description: String) {
    TODAY("[today]", "Today's date", "e.g. 2026-09-05"),
    NOW("[now]", "Current time", "e.g. 14:30:05"),
    DATETIME("[datetime]", "Date & time", "e.g. 2026-09-05 14:30:05"),
    YEAR("[year]", "Year", "e.g. 2026"),
    MONTH("[month]", "Month", "e.g. 09"),
    DAY("[day]", "Day", "e.g. 05"),
    ;

    fun resolve(now: Long): String = when (this) {
        TODAY -> now.format("yyyy-MM-dd")
        NOW -> now.format("HH:mm:ss")
        DATETIME -> now.format("yyyy-MM-dd HH:mm:ss")
        YEAR -> now.format("yyyy")
        MONTH -> now.format("MM")
        DAY -> now.format("dd")
    }
}

/**
 * Expands every recognized `[token]` placeholder in [text] to its current
 * value. Text that isn't a recognized token (a typo, or brackets the user
 * actually wants typed literally) is left untouched rather than dropped.
 */
fun expandTextTemplate(text: String, now: Long = System.currentTimeMillis()): String {
    var result = text
    TextTemplateToken.entries.forEach { token ->
        result = result.replace(token.token, token.resolve(now), ignoreCase = true)
    }
    return result
}
