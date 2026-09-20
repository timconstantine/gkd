package com.tconstantine.clarity.ai

data class AiTextResult(
    val mainText: String,
    val clarifications: List<String>,
)

private val clarificationHeadingRegex = Regex(
    "^\\s*[#*_\\s]*${Regex.escape(NEEDS_CLARIFICATION_HEADING)}[:*_\\s]*$",
    RegexOption.IGNORE_CASE,
)
private val listMarkerRegex = Regex("^[-*•]\\s*|^\\d+[.)]\\s*")

fun parseAiTextResult(rawResponse: String): AiTextResult {
    val lines = rawResponse.trim().lines()
    val headingIndex = lines.indexOfFirst { clarificationHeadingRegex.matches(it) }
    if (headingIndex == -1) {
        return AiTextResult(mainText = rawResponse.trim(), clarifications = emptyList())
    }
    val mainText = lines.subList(0, headingIndex).joinToString("\n").trim()
    val clarifications = lines.subList(headingIndex + 1, lines.size)
        .map { it.trim().replace(listMarkerRegex, "").trim() }
        .filter { it.isNotEmpty() }
    return AiTextResult(mainText = mainText, clarifications = clarifications)
}
