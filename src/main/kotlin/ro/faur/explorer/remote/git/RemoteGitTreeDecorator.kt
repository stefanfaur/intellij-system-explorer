package ro.faur.explorer.remote.git

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Maps [GitFileStatus] values to display colours for the remote file tree.
 *
 * The colour conventions intentionally match those used by IntelliJ's own Local
 * Changes / Project view so that the UI feels familiar:
 *
 * - [GitFileStatus.MODIFIED]  → blue  (tracked file with uncommitted changes)
 * - [GitFileStatus.ADDED]     → green (staged new file)
 * - [GitFileStatus.DELETED]   → red   (staged or working-copy deletion)
 * - [GitFileStatus.RENAMED]   → blue  (treated the same as modified for colour)
 * - [GitFileStatus.COPIED]    → blue
 * - [GitFileStatus.UNTRACKED] → brown / olive (file not tracked by git)
 * - [GitFileStatus.IGNORED]   → gray  (explicitly ignored via .gitignore)
 * - [GitFileStatus.UNMERGED]  → red   (merge conflict)
 * - `null` (no status)        → `null` (use default tree foreground)
 *
 * This is a pure utility object; it holds no mutable state.
 */
object RemoteGitTreeDecorator {

    // ── Colour constants ──────────────────────────────────────────────────────

    /** Tracked file modified in the working copy or index. */
    private val MODIFIED_COLOR = JBColor(Color(0x00, 0x5C, 0xC8), Color(0x58, 0x9D, 0xF6))

    /** File staged as newly added (not previously tracked). */
    private val ADDED_COLOR = JBColor(Color(0x00, 0x8C, 0x00), Color(0x59, 0xA8, 0x69))

    /** File deleted in the working copy or index. */
    private val DELETED_COLOR = JBColor(Color(0xCC, 0x00, 0x00), Color(0xFF, 0x5C, 0x5C))

    /**
     * File present on disk but not tracked by git.
     * Brown / olive tones are used by standard git GUIs for untracked files.
     */
    private val UNTRACKED_COLOR = JBColor(Color(0x80, 0x60, 0x00), Color(0xC4, 0xA0, 0x00))

    /** File listed in .gitignore. Rendered in muted gray to de-emphasise it. */
    private val IGNORED_COLOR = JBColor(Color(0x80, 0x80, 0x80), Color(0x62, 0x62, 0x62))

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the foreground [Color] to use when rendering a tree node whose
     * git status is [status], or `null` if no special decoration should be applied
     * (i.e. the node should be rendered with the tree's default foreground colour).
     */
    fun colorFor(status: GitFileStatus?): Color? = when (status) {
        GitFileStatus.MODIFIED  -> MODIFIED_COLOR
        GitFileStatus.ADDED     -> ADDED_COLOR
        GitFileStatus.DELETED   -> DELETED_COLOR
        GitFileStatus.RENAMED   -> MODIFIED_COLOR   // rename ≈ modify for display
        GitFileStatus.COPIED    -> MODIFIED_COLOR
        GitFileStatus.UNTRACKED -> UNTRACKED_COLOR
        GitFileStatus.IGNORED   -> IGNORED_COLOR
        GitFileStatus.UNMERGED  -> DELETED_COLOR    // conflict — highlight in red
        null                    -> null
    }

    /**
     * Convenience overload that looks up the status for [filePath] in
     * [RemoteGitStatusCache] and delegates to [colorFor].
     *
     * Returns `null` when the file has no cached status or when it is not
     * inside any known repository for [connectionName].
     */
    fun colorFor(connectionName: String, filePath: String): Color? {
        val status = RemoteGitStatusCache.getStatus(connectionName, filePath)
        return colorFor(status)
    }
}
