package com.tconstantine.clarity.ai

enum class AnthropicModel(val apiId: String, val displayName: String) {
    HAIKU("claude-haiku-4-5-20251001", "Fast (Haiku 4.5)"),
    SONNET("claude-sonnet-5", "Balanced (Sonnet 5)"),
    OPUS("claude-opus-5", "Most capable (Opus 5)");

    companion object {
        val default get() = HAIKU

        fun fromApiId(apiId: String?): AnthropicModel =
            entries.firstOrNull { it.apiId == apiId } ?: default
    }
}
