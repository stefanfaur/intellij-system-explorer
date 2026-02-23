package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.SftpEntry
import ro.faur.explorer.remote.git.RemoteGitRootDetector
import ro.faur.explorer.remote.git.RemoteGitVcsManager

class RemoteGitRootDetectorTest {

    private fun makeEntries(vararg names: Pair<String, Boolean>): List<SftpEntry> =
        names.map { (name, isDir) ->
            SftpEntry(name = name, isDirectory = isDir, size = 0, path = "/$name")
        }

    @Test
    fun `isGitRoot returns true when entries contain dot-git directory`() {
        val detector = RemoteGitRootDetector(RemoteGitVcsManager())
        val entries = makeEntries(
            "src" to true,
            ".git" to true,
            "README.md" to false,
        )
        assertTrue(detector.isGitRoot(entries))
    }

    @Test
    fun `isGitRoot returns true when entries contain dot-git file (worktree)`() {
        val detector = RemoteGitRootDetector(RemoteGitVcsManager())
        val entries = makeEntries(
            "src" to true,
            ".git" to false,  // worktree: .git is a file
            "README.md" to false,
        )
        assertTrue(detector.isGitRoot(entries))
    }

    @Test
    fun `isGitRoot returns false when no dot-git entry`() {
        val detector = RemoteGitRootDetector(RemoteGitVcsManager())
        val entries = makeEntries(
            "src" to true,
            "README.md" to false,
            ".gitignore" to false,
        )
        assertFalse(detector.isGitRoot(entries))
    }

    @Test
    fun `isGitRoot returns false for empty entries`() {
        val detector = RemoteGitRootDetector(RemoteGitVcsManager())
        assertFalse(detector.isGitRoot(emptyList()))
    }
}
