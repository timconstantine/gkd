package com.tconstantine.clarity.viewmodel

import com.tconstantine.clarity.ai.AnthropicModel

enum class Screen { Record, Result, Settings }

enum class AiStage { Idle, CleaningUp, Revising }

enum class DictationTarget { RawText, RevisionInput }

data class ClarityUiState(
    val screen: Screen = Screen.Record,
    val isListening: Boolean = false,
    val dictationTarget: DictationTarget = DictationTarget.RawText,
    val partialText: String = "",
    val rawText: String = "",
    val cleanedText: String = "",
    val clarifications: List<String> = emptyList(),
    val revisionInput: String = "",
    val aiStage: AiStage = AiStage.Idle,
    val errorMessage: String? = null,
    val apiKey: String = "",
    val model: AnthropicModel = AnthropicModel.default,
    val speechAvailable: Boolean = true,
) {
    val hasApiKey get() = apiKey.isNotBlank()
    val isBusy get() = aiStage != AiStage.Idle
}
