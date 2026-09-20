package com.tconstantine.clarity.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

data class SpeechState(
    val isListening: Boolean = false,
    val committedText: String = "",
    val partialText: String = "",
    val errorMessage: String? = null,
)

/**
 * Android's [SpeechRecognizer] stops after each utterance, which cuts people off mid-thought.
 * This restarts it on every result/recoverable error while [shouldKeepListening] holds, so
 * dictation continues across natural pauses until the user explicitly stops it.
 */
class SpeechRecognizerController(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null
    private var shouldKeepListening = false

    private val _state = MutableStateFlow(SpeechState())
    val state: StateFlow<SpeechState> = _state

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (_state.value.isListening) return
        shouldKeepListening = true
        _state.update { it.copy(errorMessage = null) }
        startListeningInternal()
    }

    fun stop() {
        shouldKeepListening = false
        recognizer?.stopListening()
        _state.update { it.copy(isListening = false, partialText = "") }
    }

    fun clearText() {
        _state.update { it.copy(committedText = "", partialText = "") }
    }

    fun destroy() {
        shouldKeepListening = false
        recognizer?.destroy()
        recognizer = null
    }

    private fun startListeningInternal() {
        recognizer?.destroy()
        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        newRecognizer.setRecognitionListener(createListener())
        recognizer = newRecognizer
        newRecognizer.startListening(buildRecognizerIntent())
        _state.update { it.copy(isListening = true, partialText = "") }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            val recoverable = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                error == SpeechRecognizer.ERROR_CLIENT
            if (shouldKeepListening && recoverable) {
                startListeningInternal()
            } else {
                shouldKeepListening = false
                _state.update {
                    it.copy(isListening = false, partialText = "", errorMessage = describeError(error))
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) {
                _state.update { it.copy(committedText = joinText(it.committedText, text), partialText = "") }
            }
            if (shouldKeepListening) {
                startListeningInternal()
            } else {
                _state.update { it.copy(isListening = false, partialText = "") }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            _state.update { it.copy(partialText = text) }
        }
    }

    private fun joinText(existing: String, addition: String): String =
        if (existing.isBlank()) addition else "$existing $addition"

    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
        else -> "Speech recognition stopped unexpectedly."
    }

    private fun buildRecognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Longer than the system default so people who pause mid-thought aren't cut off.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        }
}
