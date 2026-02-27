package ro.faur.explorer.remote

import ro.faur.explorer.remote.settings.RemoteExplorerSettings

class FileSizeLimitChecker(private val maxEditorSizeMb: Int) {

    enum class Action {
        OPEN_NORMALLY,
        WARN_LARGE,
        BLOCK_TOO_LARGE
    }

    private val maxEditorSizeBytes = maxEditorSizeMb.toLong() * 1024 * 1024
    private val blockThresholdBytes: Long
        get() = try {
            RemoteExplorerSettings.getInstance().state.maxTransferSizeMb * 1024 * 1024
        } catch (_: Exception) {
            100L * 1024 * 1024
        }

    fun check(sizeBytes: Long): Action = when {
        sizeBytes <= maxEditorSizeBytes -> Action.OPEN_NORMALLY
        sizeBytes <= blockThresholdBytes -> Action.WARN_LARGE
        else -> Action.BLOCK_TOO_LARGE
    }
}
