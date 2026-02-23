package ro.faur.explorer.remote.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import ro.faur.explorer.remote.SftpConnectionManager
import java.time.Duration
import java.util.concurrent.atomic.AtomicReference

// ── Data carrier ───────────────────────────────────────────────────────────────

/**
 * Holds the identifying information for the currently active remote connection.
 * Passed to [ActiveConnectionRegistry] when the user connects to a remote host.
 */
data class ActiveConnectionInfo(
    val connectionManager: SftpConnectionManager,
    val connectionName: String,
    val repoPath: String,
)

// ── Active-connection registry ─────────────────────────────────────────────────

/**
 * A lightweight, thread-safe registry that remembers the currently active
 * remote connection so that [RemoteGitBranchWidget] can display the branch
 * name without needing a direct reference to the tree panel or connection
 * manager.
 *
 * Usage:
 * ```kotlin
 * // when the user focuses / connects a panel:
 * ActiveConnectionRegistry.set(ActiveConnectionInfo(connectionManager, connectionName, repoPath))
 *
 * // when the user disconnects:
 * ActiveConnectionRegistry.clear(connectionName)
 * ```
 */
object ActiveConnectionRegistry {

    /** The single active (focused) connection, or `null` when none. */
    private val active = AtomicReference<ActiveConnectionInfo?>(null)

    /**
     * Registers [info] as the currently active remote connection.
     * Replaces any previously registered entry.
     */
    fun set(info: ActiveConnectionInfo) {
        active.set(info)
    }

    /**
     * Clears the registry if [connectionName] is currently the registered entry.
     * Ignores the call if a different connection is active (avoids race conditions
     * where a new connection was set between the disconnect being initiated and
     * this call arriving).
     */
    fun clear(connectionName: String) {
        active.updateAndGet { current ->
            if (current?.connectionName == connectionName) null else current
        }
    }

    /** Clears the registry unconditionally. */
    fun clearAll() {
        active.set(null)
    }

    /** Returns the currently active connection info, or `null` if none. */
    fun getActive(): ActiveConnectionInfo? = active.get()
}

// ── Widget factory ─────────────────────────────────────────────────────────────

/**
 * Factory for the status-bar widget that shows the current remote git branch.
 *
 * The displayed text follows the pattern:
 *   `🌐 connectionName: branchName`
 *
 * or is empty when no remote connection is active.
 */
class RemoteGitBranchWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID
    override fun getDisplayName(): String = "Remote Git Branch"
    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget =
        RemoteGitBranchWidget(project)

    companion object {
        const val WIDGET_ID = "RemoteGitBranch"
    }
}

// ── Widget implementation ──────────────────────────────────────────────────────

private class RemoteGitBranchWidget(
    @Suppress("UNUSED_PARAMETER") private val project: Project,
) : StatusBarWidget, StatusBarWidget.TextPresentation {

    override fun ID(): String = RemoteGitBranchWidgetFactory.WIDGET_ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this
    override fun install(statusBar: StatusBar) {}
    override fun dispose() {}
    override fun getTooltipText(): String = "Remote Git branch (click to refresh)"
    override fun getAlignment(): Float = 0f

    /**
     * Returns the branch label for the active remote connection, or an empty
     * string when no connection is active or the branch cannot be determined.
     *
     * This method is called on the EDT by IntelliJ's status-bar update cycle.
     * Git I/O uses a short 5-second timeout to avoid blocking the UI thread
     * noticeably.  [lastKnownText] acts as a simple cache: if the command
     * fails transiently (e.g. the SSH multiplexer is busy), the last good
     * value is returned so the widget does not flicker.
     */
    override fun getText(): String {
        val info = ActiveConnectionRegistry.getActive() ?: return ""
        return fetchBranchText(info)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @Volatile private var lastKnownText: String = ""

    /**
     * Runs `git rev-parse --abbrev-ref HEAD` on the remote host described by
     * [info] and returns a formatted label.  Falls back to [lastKnownText] on
     * transient errors so the widget does not flicker during brief SSH hiccups.
     */
    private fun fetchBranchText(info: ActiveConnectionInfo): String {
        return try {
            val executor = RemoteGitCommandExecutor(info.connectionManager, info.connectionName)
            val result = executor.executeBlocking(
                info.repoPath,
                timeout = Duration.ofSeconds(5),
                args = arrayOf("rev-parse", "--abbrev-ref", "HEAD")
            )
            if (!result.isSuccess || result.stdout.isBlank()) return lastKnownText
            val branch = result.stdout.trim()
            val text = "\uD83C\uDF10 ${info.connectionName}: $branch"   // 🌐
            lastKnownText = text
            text
        } catch (_: Exception) {
            lastKnownText   // return stale value on transient errors
        }
    }
}
