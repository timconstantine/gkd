package li.gkd.app.ui.component

import android.webkit.URLUtil
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

/** What [SubsLinkDialogState.requestNew] resolved to. */
sealed interface SubsAddInput {
    data class Url(val value: String) : SubsAddInput
    data class Name(val value: String) : SubsAddInput
}

private enum class SubsAddMode { Url, Name }

private data class SubsLinkDialogRequest(
    val initialValue: String,
    val existingUrls: Set<String>,
    val value: String,
    // Only "Add subscription" (requestNew) offers a Name mode — editing an
    // existing (necessarily remote) subscription's link always stays in
    // Url mode, so this is false there.
    val allowNameMode: Boolean,
    val mode: SubsAddMode,
)

class SubsLinkDialogState(
    private val onOpenHelp: () -> Unit,
    private val requestLocalNetworkPermission: suspend () -> Boolean,
) {
    private val requestFlow = MutableStateFlow<SubsLinkDialogRequest?>(null)
    private val requestMutex = Mutex()
    private var currentContinuation: CancellableContinuation<SubsAddInput?>? = null

    private fun complete(value: SubsAddInput?) {
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

    private fun setMode(mode: SubsAddMode) {
        val request = requestFlow.value ?: return
        if (!request.allowNameMode) return
        requestFlow.value = request.copy(mode = mode, value = "")
    }

    private fun submit(request: SubsLinkDialogRequest) {
        if (request.mode == SubsAddMode.Name) {
            val name = request.value
            if (name.isEmpty()) {
                toast("A name is required")
                return
            }
            complete(SubsAddInput.Name(name))
            return
        }
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
        complete(SubsAddInput.Url(value))
    }

    private fun cancel() = complete(null)

    private fun openHelp() {
        cancel()
        onOpenHelp()
    }

    private suspend fun requestInput(
        initialValue: String,
        allowNameMode: Boolean,
    ): SubsAddInput? {
        val existingUrls = withContext(Dispatchers.IO) {
            Db.subsItemDao.queryAll().mapNotNullTo(mutableSetOf()) { it.updateUrl }
        }
        return withContext(Dispatchers.Main.immediate) {
            requestMutex.withLock {
                try {
                    requestFlow.value = SubsLinkDialogRequest(
                        initialValue = initialValue,
                        existingUrls = existingUrls,
                        value = initialValue,
                        allowNameMode = allowNameMode,
                        mode = SubsAddMode.Url,
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
    }

    /** Edits an existing (always remote) subscription's update link. */
    suspend fun request(initialValue: String = ""): String? {
        val value = (requestInput(initialValue, allowNameMode = false) as? SubsAddInput.Url)?.value
        if (value != null && isLocalNetworkUrl(value) && !requestLocalNetworkPermission()) {
            return null
        }
        return value
    }

    /**
     * The "Add subscription" flow: a fresh subscription either by URL (an
     * existing remote one) or by name (a new, empty, locally-editable one —
     * see [li.gkd.app.util.SubscriptionStore.createLocalSubscription]).
     */
    suspend fun requestNew(): SubsAddInput? {
        val result = requestInput("", allowNameMode = true)
        val url = (result as? SubsAddInput.Url)?.value
        if (url != null && isLocalNetworkUrl(url) && !requestLocalNetworkPermission()) {
            return null
        }
        return result
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
                    Column {
                        if (currentRequest.allowNameMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                FilterChip(
                                    selected = currentRequest.mode == SubsAddMode.Url,
                                    onClick = { setMode(SubsAddMode.Url) },
                                    label = { Text(text = "From a link") },
                                )
                                FilterChip(
                                    selected = currentRequest.mode == SubsAddMode.Name,
                                    onClick = { setMode(SubsAddMode.Name) },
                                    label = { Text(text = "Create new") },
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        OutlinedTextField(
                            value = currentRequest.value,
                            onValueChange = ::updateValue,
                            maxLines = 8,
                            modifier = Modifier
                                .fillMaxWidth()
                                .autoFocus(),
                            placeholder = {
                                Text(
                                    text = if (currentRequest.mode == SubsAddMode.Name) {
                                        "Enter a name for your new rule collection"
                                    } else {
                                        "Enter a subscription link"
                                    },
                                )
                            },
                            isError = currentRequest.mode == SubsAddMode.Url &&
                                    currentRequest.value.isNotEmpty() &&
                                    !URLUtil.isNetworkUrl(currentRequest.value),
                        )
                    }
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
