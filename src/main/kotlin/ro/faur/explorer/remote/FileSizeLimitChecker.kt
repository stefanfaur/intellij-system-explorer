package ro.faur.explorer.remote

class FileSizeLimitChecker(private val maxEditorSizeMb: Int) {

    enum class Action {
        OPEN_NORMALLY,
        WARN_LARGE,
        BLOCK_TOO_LARGE
    }

    private val maxEditorSizeBytes = maxEditorSizeMb.toLong() * 1024 * 1024
    private val blockThresholdBytes = 100L * 1024 * 1024 // 100 MB

    fun check(sizeBytes: Long): Action = when {
        sizeBytes <= maxEditorSizeBytes -> Action.OPEN_NORMALLY
        sizeBytes <= blockThresholdBytes -> Action.WARN_LARGE
        else -> Action.BLOCK_TOO_LARGE
    }
}
