package li.gkd.app.snapshot

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

// The file rename and database publish must be completed as a non-cancellable commit phase; the time-consuming write should still respond to cancellation.
suspend fun commitSnapshotDirectory(
    layout: SnapshotFileLayout,
    id: Long,
    write: (SnapshotFileLayout.Files) -> Unit,
    publish: suspend () -> Unit,
) {
    currentCoroutineContext().ensureActive()
    val target = layout.committed(id)
    val staging = layout.staging(id)
    if (target.directory.exists()) {
        throw IOException("Target directory already exists: ${target.directory.name}")
    }
    staging.directory.deleteIfExists()
    if (!staging.directory.mkdirs()) {
        throw IOException("Failed to create staging directory: ${staging.directory.name}")
    }
    try {
        write(staging)
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            if (!staging.directory.renameTo(target.directory)) {
                throw IOException("Failed to commit directory: ${target.directory.name}")
            }
            try {
                publish()
            } catch (e: Throwable) {
                try {
                    target.directory.deleteIfExists()
                } catch (cleanupError: IOException) {
                    e.addSuppressed(cleanupError)
                }
                throw e
            }
        }
    } catch (e: Throwable) {
        try {
            withContext(NonCancellable) {
                staging.directory.deleteIfExists()
            }
        } catch (cleanupError: IOException) {
            e.addSuppressed(cleanupError)
        }
        throw e
    }
}

private fun File.deleteIfExists() {
    if (exists() && !deleteRecursively()) {
        throw IOException("Failed to delete directory: $name")
    }
}
