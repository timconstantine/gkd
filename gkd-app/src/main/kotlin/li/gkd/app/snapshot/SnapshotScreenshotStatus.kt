package li.gkd.app.snapshot

enum class SnapshotScreenshotStatus {
    Captured,
    Unavailable,
    LikelyProtected,
    ;

    fun detailText(): String? = when (this) {
        Captured -> null
        Unavailable -> "Could not capture the screen"
        LikelyProtected -> "The current screen may be screenshot-protected"
    }
}
