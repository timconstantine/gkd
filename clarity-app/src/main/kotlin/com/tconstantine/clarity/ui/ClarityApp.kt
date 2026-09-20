package com.tconstantine.clarity.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tconstantine.clarity.viewmodel.ClarityViewModel
import com.tconstantine.clarity.viewmodel.DictationTarget
import com.tconstantine.clarity.viewmodel.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClarityApp(
    viewModel: ClarityViewModel,
    onRequestDictation: (DictationTarget) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(topBarTitle(state.screen)) },
                actions = {
                    if (state.screen != Screen.Settings) {
                        IconButton(onClick = { viewModel.navigateTo(Screen.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (state.screen) {
            Screen.Record -> RecordScreen(
                modifier = Modifier.padding(padding),
                state = state,
                onStartDictation = { onRequestDictation(DictationTarget.RawText) },
                onStopDictation = viewModel::stopDictation,
                onTextChange = viewModel::updateRawText,
                onClear = viewModel::clearRawText,
                onCleanUp = viewModel::cleanUp,
            )

            Screen.Result -> ResultScreen(
                modifier = Modifier.padding(padding),
                state = state,
                onRevisionTextChange = viewModel::updateRevisionInput,
                onStartRevisionDictation = { onRequestDictation(DictationTarget.RevisionInput) },
                onStopDictation = viewModel::stopDictation,
                onApplyRevision = viewModel::applyRevision,
                onStartOver = viewModel::startOver,
            )

            Screen.Settings -> SettingsScreen(
                modifier = Modifier.padding(padding),
                state = state,
                onApiKeyChange = viewModel::setApiKey,
                onModelChange = viewModel::setModel,
                onDone = {
                    viewModel.navigateTo(if (state.cleanedText.isBlank()) Screen.Record else Screen.Result)
                },
            )
        }
    }
}

private fun topBarTitle(screen: Screen) = when (screen) {
    Screen.Record -> "Clarity"
    Screen.Result -> "Cleaned up"
    Screen.Settings -> "Settings"
}
