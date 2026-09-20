package com.tconstantine.clarity

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tconstantine.clarity.ui.ClarityApp
import com.tconstantine.clarity.ui.theme.ClarityTheme
import com.tconstantine.clarity.viewmodel.ClarityViewModel
import com.tconstantine.clarity.viewmodel.DictationTarget

class MainActivity : ComponentActivity() {
    private val viewModel: ClarityViewModel by viewModels {
        viewModelFactory {
            initializer { ClarityViewModel(applicationContext) }
        }
    }
    private var pendingDictationTarget: DictationTarget? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val target = pendingDictationTarget
        pendingDictationTarget = null
        if (granted && target != null) {
            viewModel.startDictation(target)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClarityTheme {
                ClarityApp(
                    viewModel = viewModel,
                    onRequestDictation = { target -> requestDictation(target) },
                )
            }
        }
    }

    private fun requestDictation(target: DictationTarget) {
        if (hasMicPermission()) {
            viewModel.startDictation(target)
        } else {
            pendingDictationTarget = target
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
}
