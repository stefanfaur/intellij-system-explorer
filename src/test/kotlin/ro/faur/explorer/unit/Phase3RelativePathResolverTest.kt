package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.query.RelativePathResolver

/**
 * Phase 3 — Relative Path Resolver
 *
 * Covers every case listed under Phase 3 Automated Verification:
 *   - `../sibling` from `/a/b` → `/a/sibling`
 *   - `~/Desktop` → user's home + `/Desktop`
 *   - `$HOME/...` → user's home expansion
 *   - plain text that is not a relative path → null
 *
 * All tests fail to compile until RelativePathResolver exists.
 */
class Phase3RelativePathResolverTest {

    private val home = System.getProperty("user.home") ?: "/home/user"

    // -------------------------------------------------------------------------
    // resolve() — relative paths
    // -------------------------------------------------------------------------

    @Test
    fun `parent-relative path resolves to sibling directory`() {
        val result = RelativePathResolver.resolve("../sibling", "/a/b")
        assertEquals("/a/sibling", result)
    }

    @Test
    fun `double parent traversal resolves correctly`() {
        val result = RelativePathResolver.resolve("../../other", "/a/b/c")
        assertEquals("/a/other", result)
    }

    @Test
    fun `dot-slash path resolves to child of current`() {
        val result = RelativePathResolver.resolve("./child", "/a/b")
        assertEquals("/a/b/child", result)
    }

    @Test
    fun `dot-dot alone resolves to parent`() {
        val result = RelativePathResolver.resolve("..", "/a/b")
        assertEquals("/a", result)
    }

    @Test
    fun `plain text is not treated as relative path`() {
        val result = RelativePathResolver.resolve("readme", "/a/b")
        assertNull(result)
    }

    @Test
    fun `absolute path is not treated as relative path`() {
        val result = RelativePathResolver.resolve("/absolute/path", "/a/b")
        assertNull(result)
    }

    @Test
    fun `empty text is not treated as relative path`() {
        val result = RelativePathResolver.resolve("", "/a/b")
        assertNull(result)
    }

    @Test
    fun `resolve normalizes the result removing redundant segments`() {
        // /a/b/../c should normalize to /a/c
        val result = RelativePathResolver.resolve("../c", "/a/b")
        assertNotNull(result)
        assertFalse(result!!.contains(".."), "Normalized path should not contain ..")
    }

    // -------------------------------------------------------------------------
    // expandAliases()
    // -------------------------------------------------------------------------

    @Test
    fun `tilde expands to user home`() {
        val result = RelativePathResolver.expandAliases("~/Desktop")
        assertEquals("$home/Desktop", result)
    }

    @Test
    fun `tilde alone expands to user home`() {
        val result = RelativePathResolver.expandAliases("~")
        assertEquals(home, result)
    }

    @Test
    fun `dollar-HOME expands to user home`() {
        val result = RelativePathResolver.expandAliases("\$HOME/Projects")
        assertEquals("$home/Projects", result)
    }

    @Test
    fun `tilde in middle of string is not expanded`() {
        // Only leading ~ should be treated as alias
        val result = RelativePathResolver.expandAliases("/some/path~with/tilde")
        assertEquals("/some/path~with/tilde", result)
    }

    @Test
    fun `plain path without alias is returned unchanged`() {
        val result = RelativePathResolver.expandAliases("/usr/local/bin")
        assertEquals("/usr/local/bin", result)
    }
}
