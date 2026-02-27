package ro.faur.explorer.remote.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.annotate.AnnotationProvider
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vcs.annotate.LineAnnotationAspectAdapter
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Implements [AnnotationProvider] for the Remote Git VCS integration.
 *
 * When the user invokes "Annotate" on a remote file, IntelliJ calls [annotate].
 * This implementation runs `git blame --porcelain` on the remote host, parses
 * the output with [GitBlameParser], and returns a [RemoteGitAnnotation] that
 * provides per-line author, date, and commit information for the gutter display.
 *
 * @param project        The current project (required by FileAnnotation).
 * @param executor       Executes git commands on the remote host.
 * @param connectionName Key identifying the remote connection (for display).
 * @param repoPath       Absolute path to the git repository root on the remote.
 */
class RemoteGitAnnotationProvider(
    private val project: Project,
    private val executor: RemoteGitCommandExecutor,
    private val connectionName: String,
    private val repoPath: String,
) : AnnotationProvider {

    @Throws(VcsException::class)
    override fun annotate(file: VirtualFile): FileAnnotation {
        val relPath = relativize(file.path)
            ?: throw VcsException("File ${file.path} is outside repository $repoPath")

        val maxLines = try { RemoteGitSettings.getInstance().state.maxBlameLines } catch (_: Exception) { 5000 }
        val blameLines = runBlame(relPath)
        if (blameLines.size > maxLines) {
            throw VcsException("File too large for blame (${blameLines.size} lines > maxBlameLines=$maxLines). Increase the limit in Settings > System Explorer > Remote Git.")
        }
        return RemoteGitAnnotation(project, file, blameLines)
    }

    @Throws(VcsException::class)
    override fun annotate(file: VirtualFile, revision: VcsFileRevision): FileAnnotation {
        val relPath = relativize(file.path)
            ?: throw VcsException("File ${file.path} is outside repository $repoPath")

        val maxLines = try { RemoteGitSettings.getInstance().state.maxBlameLines } catch (_: Exception) { 5000 }
        val revHash = revision.revisionNumber.asString()
        val blameLines = runBlame(relPath, revHash)
        if (blameLines.size > maxLines) {
            throw VcsException("File too large for blame (${blameLines.size} lines > maxBlameLines=$maxLines). Increase the limit in Settings > System Explorer > Remote Git.")
        }
        return RemoteGitAnnotation(project, file, blameLines)
    }

    override fun isAnnotationValid(rev: VcsFileRevision): Boolean = true

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun relativize(path: String): String? {
        if (path == repoPath) return "."
        val prefix = "$repoPath/"
        return if (path.startsWith(prefix)) path.removePrefix(prefix) else null
    }

    @Throws(VcsException::class)
    private fun runBlame(relPath: String, revHash: String? = null): Map<Int, BlameLine> {
        val args = if (revHash != null) {
            arrayOf("blame", "--porcelain", revHash, "--", relPath)
        } else {
            arrayOf("blame", "--porcelain", "--", relPath)
        }

        val porcelainOutput = try {
            val result = executor.executeBlocking(repoPath, args = args)
            if (!result.isSuccess) {
                throw VcsException(
                    "git blame returned exit ${result.exitCode} for $relPath: ${result.stderr.trim()}"
                )
            }
            result.stdout
        } catch (e: IllegalStateException) {
            throw VcsException("git blame failed for $relPath: ${e.message}", e)
        }

        return GitBlameParser.parse(porcelainOutput)
    }

    // ── Inner class ───────────────────────────────────────────────────────────

    /**
     * A [FileAnnotation] backed by the list of [BlameLine]s produced by [GitBlameParser].
     *
     * IntelliJ renders three [LineAnnotationAspect]s in the gutter:
     * - Abbreviated commit hash
     * - Author name
     * - Commit date (yyyy-MM-dd)
     */
    private class RemoteGitAnnotation(
        project: Project,
        private val file: VirtualFile,
        private val blameLines: Map<Int, BlameLine>,
    ) : FileAnnotation(project) {

        companion object {
            private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
            private val DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
        }

        // ── LineAnnotationAspect definitions ──────────────────────────────────

        private val revisionAspect = object : LineAnnotationAspectAdapter(
            "Revision", "Revision", true
        ) {
            override fun getValue(lineNumber: Int): String {
                val line = blameLines[lineNumber] ?: return ""
                return if (line.isUncommitted) "(uncommitted)" else line.commitHash.take(8)
            }

            override fun showAffectedPaths(lineNumber: Int) {
                // No-op: remote files have no local affected paths to show.
            }
        }

        private val authorAspect = object : LineAnnotationAspectAdapter(
            "Author", "Author", false
        ) {
            override fun getValue(lineNumber: Int): String {
                return blameLines[lineNumber]?.author ?: ""
            }

            override fun showAffectedPaths(lineNumber: Int) {
                // No-op: remote files have no local affected paths to show.
            }
        }

        private val dateAspect = object : LineAnnotationAspectAdapter(
            "Date", "Date", false
        ) {
            override fun getValue(lineNumber: Int): String {
                val ts = blameLines[lineNumber]?.timestamp ?: return ""
                return DATE_FORMAT.format(Instant.ofEpochSecond(ts))
            }

            override fun showAffectedPaths(lineNumber: Int) {
                // No-op: remote files have no local affected paths to show.
            }
        }

        // ── FileAnnotation API ────────────────────────────────────────────────

        override fun getLineCount(): Int = (blameLines.keys.maxOrNull() ?: -1) + 1

        override fun getAspects(): Array<LineAnnotationAspect> =
            arrayOf(revisionAspect, authorAspect, dateAspect)

        override fun getToolTip(lineNumber: Int): String? {
            val line = blameLines[lineNumber] ?: return null
            val date = DATETIME_FORMAT.format(Instant.ofEpochSecond(line.timestamp))
            return "${line.commitHash.take(8)} — ${line.author} <${line.authorEmail}> on $date\n${line.summary}"
        }

        override fun getCurrentRevision(): VcsRevisionNumber? {
            val hash = blameLines.values.firstOrNull()?.commitHash ?: return null
            return RemoteGitRevisionNumber(hash)
        }

        override fun getRevisions(): List<VcsFileRevision>? = null

        override fun getLineRevisionNumber(lineNumber: Int): VcsRevisionNumber? {
            val hash = blameLines[lineNumber]?.commitHash ?: return null
            return RemoteGitRevisionNumber(hash)
        }

        override fun getLineDate(lineNumber: Int): Date? {
            val ts = blameLines[lineNumber]?.timestamp ?: return null
            return Date(ts * 1_000L)
        }

        override fun getAnnotatedContent(): String =
            blameLines.values.joinToString("\n") { it.content }

        override fun getFile(): VirtualFile = file

        @Suppress("OVERRIDE_DEPRECATION")
        override fun dispose() {
            // Nothing to release.
        }
    }
}
