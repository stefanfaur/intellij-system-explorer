package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ro.faur.explorer.gitpanel.BranchInfo
import ro.faur.explorer.gitpanel.StashEntry
import ro.faur.explorer.gitpanel.parseBranchLine
import ro.faur.explorer.gitpanel.parseStashLine

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
}
