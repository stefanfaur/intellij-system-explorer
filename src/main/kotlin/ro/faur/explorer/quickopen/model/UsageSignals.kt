package ro.faur.explorer.quickopen.model

data class UsageSignals(
    val lastUsedMs: Long = 0L,
    val useCount: Int = 0,
    val isBookmarked: Boolean = false,
    val isOpenInEditor: Boolean = false,
    val currentBranch: String? = null
)
