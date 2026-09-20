package com.tconstantine.clarity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.tconstantine.clarity.viewmodel.AiStage
import com.tconstantine.clarity.viewmodel.ClarityUiState

@Composable
fun RecordScreen(
    modifier: Modifier = Modifier,
    state: ClarityUiState,
    onStartDictation: () -> Unit,
    onStopDictation: () -> Unit,
    onTextChange: (String) -> Unit,
    onClear: () -> Unit,
    onCleanUp: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val displayedText = if (state.isListening && state.partialText.isNotBlank()) {
        if (state.rawText.isBlank()) state.partialText else "${state.rawText} ${state.partialText}"
    } else {
        state.rawText
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Say what's on your mind. Pauses are fine — tap the mic again when you're done.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = displayedText,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            placeholder = { Text("Your dictation will appear here…") },
            readOnly = state.isListening,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { clipboard.setText(AnnotatedString(state.rawText)) },
                enabled = state.rawText.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy")
            }
            OutlinedButton(
                onClick = { shareText(context, state.rawText) },
                enabled = state.rawText.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }
            TextButton(
                onClick = onClear,
                enabled = state.rawText.isNotBlank() && !state.isListening,
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Clear")
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onCleanUp,
            enabled = state.rawText.isNotBlank() && !state.isListening && !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.aiStage == AiStage.CleaningUp) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("Cleaning up…")
            } else {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Clean up with AI")
            }
        }
        if (!state.hasApiKey) {
            Text(
                text = "Add an Anthropic API key in Settings to enable AI cleanup.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        if (!state.speechAvailable) {
            Text(
                text = "Speech recognition isn't available on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            ExtendedFloatingActionButton(
                onClick = {
                    if (state.speechAvailable) {
                        if (state.isListening) onStopDictation() else onStartDictation()
                    }
                },
                icon = {
                    Icon(if (state.isListening) Icons.Filled.Stop else Icons.Filled.Mic, contentDescription = null)
                },
                text = { Text(if (state.isListening) "Stop" else "Start dictation") },
            )
        }
    }
}
