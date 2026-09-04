package li.gkd.app.data

import li.gkd.app.a11y.selectorTypeModel
import li.gkd.selector.Selector
import li.gkd.selector.SelectorCompileResult
import li.gkd.selector.SelectorTypeResult

/**
 * One tapped attribute in the snapshot node inspector, e.g. tapping a node's
 * `text` field produces `SelectorClause("text", "=", "Skip", quoted = true)`,
 * which renders as `[text="Skip"]`.
 */
data class SelectorClause(
    val attr: String,
    val comparator: String = "=",
    val value: String,
    val quoted: Boolean,
) {
    val render: String
        get() {
            val renderedValue = if (quoted) "\"${escapeSelectorStringLiteral(value)}\"" else value
            return "[$attr$comparator$renderedValue]"
        }
}

/**
 * Joins tapped attributes into a selector expression. Each clause becomes its
 * own `[...]` block; consecutive blocks are implicitly AND-ed by the selector
 * syntax (see CONTRIBUTING.md), so this alone covers the common case of
 * combining several attributes on one node.
 */
fun buildSelectorExpression(clauses: List<SelectorClause>): String =
    clauses.joinToString(separator = "") { it.render }

/**
 * Escapes a raw attribute value for use inside a double-quoted selector string
 * literal. The selector syntax has its own escape parser (StringScanner.kt),
 * separate from JSON5's — this only needs to cover what that parser accepts:
 * `\\ \" \n \r \t \b` plus `\uHHHH` for other control characters. There's no
 * existing encode-side helper in gkd-selector (StringScanner only decodes), so
 * this mirrors its accepted escape set by hand.
 */
fun escapeSelectorStringLiteral(value: String): String = buildString {
    for (char in value) {
        when {
            char == '\\' -> append("\\\\")
            char == '"' -> append("\\\"")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char == '\b' -> append("\\b")
            char.code in 0x0000..0x001F -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
}

/**
 * Compiles and type-validates a selector string the same way
 * RawSubscription.getErrorDesc() validates rules before save, returning a
 * human-readable error, or null if the selector is valid.
 */
fun validateSelectorExpression(source: String): String? {
    if (source.isBlank()) return null
    val selector = when (val result = Selector.compile(source)) {
        is SelectorCompileResult.Success -> result.value
        is SelectorCompileResult.Failure -> return result.error.message
    }
    return when (val result = selector.validateType(selectorTypeModel)) {
        is SelectorTypeResult.Success -> null
        is SelectorTypeResult.Failure -> result.error.message
    }
}

/**
 * The selectable attribute clauses offered for a node in the inspector, in the
 * same order CONTRIBUTING.md's attribute table lists them. Null/blank string
 * attributes are omitted — there's nothing useful to match on.
 */
fun AttrInfo.availableClauses(): List<SelectorClause> = buildList {
    if (!text.isNullOrEmpty()) add(SelectorClause(attr = "text", value = text, quoted = true))
    if (!desc.isNullOrEmpty()) add(SelectorClause(attr = "desc", value = desc, quoted = true))
    if (!id.isNullOrEmpty()) add(SelectorClause(attr = "id", value = id, quoted = true))
    if (!vid.isNullOrEmpty()) add(SelectorClause(attr = "vid", value = vid, quoted = true))
    if (!name.isNullOrEmpty()) add(SelectorClause(attr = "name", value = name, quoted = true))
    add(SelectorClause(attr = "clickable", value = clickable.toString(), quoted = false))
    add(SelectorClause(attr = "focusable", value = focusable.toString(), quoted = false))
    add(SelectorClause(attr = "checkable", value = checkable.toString(), quoted = false))
    checked?.let { add(SelectorClause(attr = "checked", value = it.toString(), quoted = false)) }
    add(SelectorClause(attr = "editable", value = editable.toString(), quoted = false))
    add(SelectorClause(attr = "longClickable", value = longClickable.toString(), quoted = false))
    add(SelectorClause(attr = "visibleToUser", value = visibleToUser.toString(), quoted = false))
    add(SelectorClause(attr = "childCount", value = childCount.toString(), quoted = false))
    add(SelectorClause(attr = "index", value = index.toString(), quoted = false))
    add(SelectorClause(attr = "depth", value = depth.toString(), quoted = false))
}
