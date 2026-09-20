package com.tconstantine.clarity.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tconstantine.clarity.ai.AiCallResult
import com.tconstantine.clarity.ai.AnthropicClient
import com.tconstantine.clarity.ai.AnthropicModel
import com.tconstantine.clarity.ai.buildCleanupPrompt
import com.tconstantine.clarity.ai.buildRevisionPrompt
import com.tconstantine.clarity.ai.parseAiTextResult
import com.tconstantine.clarity.data.SecureSettingsStore
import com.tconstantine.clarity.speech.SpeechRecognizerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClarityViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val settingsStore = SecureSettingsStore(appContext)
    private val speechController = SpeechRecognizerController(appContext)
    private val anthropicClient = AnthropicClient()

    private val _uiState = MutableStateFlow(
        ClarityUiState(
            apiKey = settingsStore.apiKey,
            model = settingsStore.model,
            speechAvailable = speechController.isAvailable(),
        ),
    )
    val uiState: StateFlow<ClarityUiState> = _uiState

    init {
        viewModelScope.launch {
            speechController.state.collect { speech ->
                _uiState.update { current ->
                    val target = current.dictationTarget
                    current.copy(
                        isListening = speech.isListening,
                        partialText = speech.partialText,
                        rawText = if (target == DictationTarget.RawText && speech.committedText.isNotBlank()) {
                            appendText(current.rawText, speech.committedText)
                        } else {
                            current.rawText
                        },
                        revisionInput = if (target == DictationTarget.RevisionInput && speech.committedText.isNotBlank()) {
                            appendText(current.revisionInput, speech.committedText)
                        } else {
                            current.revisionInput
                        },
                        errorMessage = speech.errorMessage ?: current.errorMessage,
                    )
                }
                if (speech.committedText.isNotBlank()) {
                    speechController.clearText()
                }
            }
        }
    }

    fun navigateTo(screen: Screen) {
        _uiState.update { it.copy(screen = screen, errorMessage = null) }
    }

    fun startDictation(target: DictationTarget) {
        _uiState.update { it.copy(dictationTarget = target) }
        speechController.start()
    }

    fun stopDictation() {
        speechController.stop()
    }

    fun updateRawText(text: String) {
        _uiState.update { it.copy(rawText = text) }
    }

    fun updateRevisionInput(text: String) {
        _uiState.update { it.copy(revisionInput = text) }
    }

    fun updateCleanedText(text: String) {
        _uiState.update { it.copy(cleanedText = text) }
    }

    fun clearRawText() {
        _uiState.update { it.copy(rawText = "") }
    }

    fun setApiKey(key: String) {
        settingsStore.apiKey = key
        _uiState.update { it.copy(apiKey = key) }
    }

    fun setModel(model: AnthropicModel) {
        settingsStore.model = model
        _uiState.update { it.copy(model = model) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun cleanUp() {
        val state = _uiState.value
        if (state.rawText.isBlank()) return
        speechController.stop()
        _uiState.update { it.copy(aiStage = AiStage.CleaningUp, errorMessage = null) }
        viewModelScope.launch {
            val prompt = buildCleanupPrompt(state.rawText)
            when (val result = anthropicClient.sendPrompt(state.apiKey, state.model, prompt)) {
                is AiCallResult.Success -> {
                    val parsed = parseAiTextResult(result.text)
                    _uiState.update {
                        it.copy(
                            aiStage = AiStage.Idle,
                            cleanedText = parsed.mainText,
                            clarifications = parsed.clarifications,
                            screen = Screen.Result,
                        )
                    }
                }

                is AiCallResult.Failure -> {
                    _uiState.update { it.copy(aiStage = AiStage.Idle, errorMessage = result.message) }
                }
            }
        }
    }

    fun applyRevision() {
        val state = _uiState.value
        if (state.revisionInput.isBlank()) return
        speechController.stop()
        _uiState.update { it.copy(aiStage = AiStage.Revising, errorMessage = null) }
        viewModelScope.launch {
            val prompt = buildRevisionPrompt(state.cleanedText, state.revisionInput)
            when (val result = anthropicClient.sendPrompt(state.apiKey, state.model, prompt)) {
                is AiCallResult.Success -> {
                    val parsed = parseAiTextResult(result.text)
                    _uiState.update {
                        it.copy(
                            aiStage = AiStage.Idle,
                            cleanedText = parsed.mainText,
                            clarifications = parsed.clarifications,
                            revisionInput = "",
                        )
                    }
                }

                is AiCallResult.Failure -> {
                    _uiState.update { it.copy(aiStage = AiStage.Idle, errorMessage = result.message) }
                }
            }
        }
    }

    fun startOver() {
        speechController.stop()
        speechController.clearText()
        _uiState.update {
            ClarityUiState(
                apiKey = it.apiKey,
                model = it.model,
                speechAvailable = it.speechAvailable,
                screen = Screen.Record,
            )
        }
    }

    private fun appendText(existing: String, addition: String): String =
        if (existing.isBlank()) addition else "$existing $addition"

    override fun onCleared() {
        speechController.destroy()
        anthropicClient.close()
        super.onCleared()
    }
}
