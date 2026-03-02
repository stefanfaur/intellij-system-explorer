package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ro.faur.explorer.gitpanel.BranchInfo
import ro.faur.explorer.gitpanel.LocalGitBackend
import ro.faur.explorer.gitpanel.StashEntry
import ro.faur.explorer.gitpanel.parseBranchLine
import ro.faur.explorer.gitpanel.parseStashLine
import ro.faur.explorer.gitpanel.exec.GitCommandExecutor
import ro.faur.explorer.gitpanel.exec.GitCommandResult
import java.time.Duration

class GitBackendExtensionsTest {

    // --- parseBranchLine tests ---

    @Test
    fun `parseBranchLine returns current branch when marked with star`() {
        val result = parseBranchLine("main\t*")
        assertEquals(BranchInfo("main", isCurrent = true), result)
    }

    @Test
    fun `parseBranchLine returns non-current branch when marked with space`() {
        val result = parseBranchLine("feature-x\t ")
        assertEquals(BranchInfo("feature-x", isCurrent = false), result)
    }

    @Test
    fun `parseBranchLine returns null for empty string`() {
        val result = parseBranchLine("")
        assertNull(result)
    }

    @Test
    fun `parseBranchLine returns null for line without tab`() {
        val result = parseBranchLine("main")
        assertNull(result)
    }

    @Test
    fun `parseBranchLine returns non-current branch for space marker`() {
        val result = parseBranchLine("develop\t ")
        assertEquals(BranchInfo("develop", isCurrent = false), result)
    }

    // --- parseStashLine tests ---

    @Test
    fun `parseStashLine parses stash at index 0`() {
        val result = parseStashLine("stash@{0}\tWIP on main: abc1234 fix bug")
        assertEquals(StashEntry(0, "WIP on main: abc1234 fix bug"), result)
    }

    @Test
    fun `parseStashLine parses stash at higher index`() {
        val result = parseStashLine("stash@{2}\tcustom message")
        assertEquals(StashEntry(2, "custom message"), result)
    }

    @Test
    fun `parseStashLine returns null for line without tab`() {
        val result = parseStashLine("stash@{0}WIP on main: abc1234 fix bug")
        assertNull(result)
    }

    @Test
    fun `parseStashLine returns null for non-integer index`() {
        val result = parseStashLine("stash@{abc}\tsome message")
        assertNull(result)
    }

    @Test
    fun `parseStashLine parses stash at index 1`() {
        val result = parseStashLine("stash@{1}\tanother WIP message")
        assertEquals(StashEntry(1, "another WIP message"), result)
    }

    // --- stashApply / stashDrop argument array tests ---

    private fun capturingExecutor(capture: (Array<out String>) -> Unit): GitCommandExecutor =
        object : GitCommandExecutor {
            override fun executeBlocking(repoPath: String, timeout: Duration, vararg args: String): GitCommandResult {
                capture(args)
                return GitCommandResult(0, "", "")
            }
        }

    @Test
    fun `stashApply passes correct git args for index 0`() {
        var capturedArgs: Array<out String> = emptyArray()
        val backend = LocalGitBackend("/repo", capturingExecutor { capturedArgs = it })
        backend.stashApply(0)
        assertArrayEquals(arrayOf("stash", "apply", "stash@{0}"), capturedArgs)
    }

    @Test
    fun `stashApply passes correct git args for arbitrary index`() {
        var capturedArgs: Array<out String> = emptyArray()
        val backend = LocalGitBackend("/repo", capturingExecutor { capturedArgs = it })
        backend.stashApply(3)
        assertArrayEquals(arrayOf("stash", "apply", "stash@{3}"), capturedArgs)
    }

    @Test
    fun `stashDrop passes correct git args for index 0`() {
        var capturedArgs: Array<out String> = emptyArray()
        val backend = LocalGitBackend("/repo", capturingExecutor { capturedArgs = it })
        backend.stashDrop(0)
        assertArrayEquals(arrayOf("stash", "drop", "stash@{0}"), capturedArgs)
    }

    @Test
    fun `stashDrop passes correct git args for arbitrary index`() {
        var capturedArgs: Array<out String> = emptyArray()
        val backend = LocalGitBackend("/repo", capturingExecutor { capturedArgs = it })
        backend.stashDrop(2)
        assertArrayEquals(arrayOf("stash", "drop", "stash@{2}"), capturedArgs)
    }
}
