package ro.faur.explorer.remote.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.concurrency.AppExecutorUtil
import ro.faur.explorer.remote.SftpConnectionManager
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
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

    @Volatile private var cachedBranchText: String = ""
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("BranchWidget", 1)
    private var refreshFuture: ScheduledFuture<*>? = null

    private var statusBar: StatusBar? = null

    override fun ID(): String = RemoteGitBranchWidgetFactory.WIDGET_ID
    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        refreshFuture = scheduler.scheduleWithFixedDelay({
            val newText = fetchBranchTextOrEmpty()
            if (newText != cachedBranchText) {
                cachedBranchText = newText
                statusBar.updateWidget(ID())
            }
        }, 0, 30, TimeUnit.SECONDS)
    }

    override fun dispose() {
        refreshFuture?.cancel(false)
        scheduler.shutdownNow()
    }

    override fun getTooltipText(): String = "Remote Git branch (click to refresh)"
    override fun getAlignment(): Float = 0f

    /** Returns the cached branch label; updated in the background every 30 s. */
    override fun getText(): String = cachedBranchText

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Runs `git rev-parse --abbrev-ref HEAD` on the remote host described by
     * the active connection and returns a formatted label, or `""` on any failure.
     */
    private fun fetchBranchTextOrEmpty(): String {
        return try {
            val info = ActiveConnectionRegistry.getActive() ?: return ""
            val executor = RemoteGitCommandExecutor(info.connectionManager, info.connectionName)
            val result = executor.executeBlocking(
                info.repoPath,
                timeout = Duration.ofSeconds(5),
                args = arrayOf("rev-parse", "--abbrev-ref", "HEAD")
            )
            if (!result.isSuccess || result.stdout.isBlank()) return ""
            val branch = result.stdout.trim()
            "\uD83C\uDF10 ${info.connectionName}: $branch"   // 🌐
        } catch (_: Exception) {
            ""
        }
    }
}
