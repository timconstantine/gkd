package li.gkd.app.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import li.gkd.db.Db
import li.gkd.app.util.isLocalNetworkUrl
import li.gkd.app.util.throttle
import li.gkd.app.util.toast
import kotlin.coroutines.resume

private data class SubsLinkDialogRequest(
    val initialValue: String,
    val existingUrls: Set<String>,
    val value: String,
)

class SubsLinkDialogState(
    private val onOpenHelp: () -> Unit,
    private val requestLocalNetworkPermission: suspend () -> Boolean,
) {
    private val requestFlow = MutableStateFlow<SubsLinkDialogRequest?>(null)
    private val requestMutex = Mutex()
    private var currentContinuation: CancellableContinuation<String?>? = null

    private fun complete(value: String?) {
        val continuation = currentContinuation ?: return
        if (continuation.isActive) {
            requestFlow.value = null
            continuation.resume(value)
        }
    }

    private fun updateValue(value: String) {
        val request = requestFlow.value ?: return
        requestFlow.value = request.copy(value = value.trim())
    }

    private fun submit(request: SubsLinkDialogRequest) {
        val value = request.value
        if (!URLUtil.isNetworkUrl(value)) {
            toast("Invalid link")
            return
        }
        if (request.initialValue.isNotEmpty() && request.initialValue == value) {
            toast("No changes")
            complete(null)
            return
        }
        if (value in request.existingUrls) {
            toast("A subscription with the same link already exists")
            return
        }
        complete(value)
    }

    private fun cancel() = complete(null)

    private fun openHelp() {
        cancel()
        onOpenHelp()
    }

    suspend fun request(initialValue: String = ""): String? {
        val existingUrls = withContext(Dispatchers.IO) {
            Db.subsItemDao.queryAll().mapNotNullTo(mutableSetOf()) { it.updateUrl }
        }
        val value = withContext(Dispatchers.Main.immediate) {
            requestMutex.withLock {
                try {
                    requestFlow.value = SubsLinkDialogRequest(
                        initialValue = initialValue,
                        existingUrls = existingUrls,
                        value = initialValue,
                    )
                    suspendCancellableCoroutine { continuation ->
                        currentContinuation = continuation
                    }
                } finally {
                    currentContinuation = null
                    requestFlow.value = null
                }
            }
        }
        if (value != null && isLocalNetworkUrl(value) && !requestLocalNetworkPermission()) {
            return null
        }
        return value
    }

    @Composable
    fun Render() {
        val request by requestFlow.collectAsStateWithLifecycle()
        val currentRequest = request
        if (currentRequest != null) {
            AppAlertDialog(
                properties = DialogProperties(dismissOnClickOutside = false),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (currentRequest.initialValue.isNotEmpty()) {
                                "Edit subscription"
                            } else {
                                "Add subscription"
                            },
                        )
                        PerfIconButton(
                            imageVector = PerfIcon.HelpOutline,
                            contentDescription = "Subscription help",
                            onClick = throttle(::openHelp),
                        )
                    }
                },
                text = {
                    OutlinedTextField(
                        value = currentRequest.value,
                        onValueChange = ::updateValue,
                        maxLines = 8,
                        modifier = Modifier
                            .fillMaxWidth()
                            .autoFocus(),
                        placeholder = {
                            Text(text = "Enter a subscription link")
                        },
                        isError = currentRequest.value.isNotEmpty() &&
                                !URLUtil.isNetworkUrl(currentRequest.value),
                    )
                },
                onDismissRequest = ::cancel,
                confirmButton = {
                    TextButton(
                        enabled = currentRequest.value.isNotEmpty(),
                        onClick = throttle {
                            submit(currentRequest)
                        },
                    ) {
                        Text(text = "Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = ::cancel) {
                        Text(text = "Cancel")
                    }
                },
            )
        }
    }
}
