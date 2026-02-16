package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import ro.faur.explorer.util.PathUtils

class PathUtilsTest {

    @Test
    fun `userHome returns non-empty path`() {
        val home = PathUtils.userHome()
        assertTrue(home.isNotEmpty())
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `desktopPath ends with Desktop on Unix`() {
        val desktop = PathUtils.desktopPath()
        assertTrue(desktop.endsWith("Desktop"))
    }

    @Test
    @EnabledOnOs(OS.MAC, OS.LINUX)
    fun `downloadsPath ends with Downloads on Unix`() {
        val downloads = PathUtils.downloadsPath()
        assertTrue(downloads.endsWith("Downloads"))
    }

    @Test
    fun `parentPath of root returns root`() {
        assertEquals("/", PathUtils.parentPath("/"))
    }

    @Test
    fun `parentPath of nested path returns parent`() {
        assertEquals("/usr/local", PathUtils.parentPath("/usr/local/bin"))
    }

    @Test
    fun `breadcrumbs splits path into segments`() {
        val crumbs = PathUtils.breadcrumbs("/usr/local/bin")
        assertEquals(listOf("/", "usr", "local", "bin"), crumbs.map { it.name })
    }

    @Test
    fun `breadcrumbs of root returns single root`() {
        val crumbs = PathUtils.breadcrumbs("/")
        assertEquals(1, crumbs.size)
        assertEquals("/", crumbs[0].name)
    }

    @Test
    fun `isHidden returns true for dotfiles`() {
        assertTrue(PathUtils.isHidden(".gitignore"))
        assertTrue(PathUtils.isHidden(".hidden"))
    }

    @Test
    fun `isHidden returns false for normal files`() {
        assertFalse(PathUtils.isHidden("readme.md"))
        assertFalse(PathUtils.isHidden("src"))
    }

    @Test
    fun `isHidden returns false for current and parent directory references`() {
        assertFalse(PathUtils.isHidden("."))
        assertFalse(PathUtils.isHidden(".."))
    }
}
