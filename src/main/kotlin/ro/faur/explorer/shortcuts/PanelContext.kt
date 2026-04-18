package ro.faur.explorer.shortcuts

/**
 * Represents the active context/panel in the System Explorer.
 */
enum class PanelContext {
    /** Local file browser panel */
    LOCAL_BROWSER,
    /** Remote SFTP browser panel (tabs 1-4) */
    REMOTE_BROWSER,
    /** Git panel */
    GIT_PANEL,
    /** Quick Open popup */
    QUICK_OPEN,
    /** No recognized panel is active */
    UNKNOWN;

    /** Returns true if this context is a file browser (local or remote) */
    fun isFileBrowser() = this == LOCAL_BROWSER || this == REMOTE_BROWSER
}
