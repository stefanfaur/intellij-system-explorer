package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ro.faur.explorer.actions.NavigationActions

/**
 * Unit tests for [NavigationActions.NavigationHistory].
 *
 * Covers the history semantics relied upon by [BrowserPanel.navigateTo],
 * [BrowserPanel.navigateBack], and [BrowserPanel.navigateForward].
 */
class BrowserNavigationHistoryTest {

    private lateinit var history: NavigationActions.NavigationHistory

    @BeforeEach
    fun setup() {
        history = NavigationActions.NavigationHistory()
    }

    @Test
    fun `canGoBack is false on empty history`() {
        assertFalse(history.canGoBack)
    }

    @Test
    fun `canGoForward is false on empty history`() {
        assertFalse(history.canGoForward)
    }

    @Test
    fun `back returns null on empty history`() {
        assertNull(history.back())
    }

    @Test
    fun `forward returns null on empty history`() {
        assertNull(history.forward())
    }

    @Test
    fun `canGoBack is false after single push`() {
        history.push("/home")
        assertFalse(history.canGoBack)
    }

    @Test
    fun `canGoBack is true after two pushes`() {
        history.push("/home")
        history.push("/tmp")
        assertTrue(history.canGoBack)
    }

    @Test
    fun `back returns previous path after two pushes`() {
        history.push("/home")
        history.push("/tmp")
        assertEquals("/home", history.back())
    }

    @Test
    fun `canGoForward is true after navigating back`() {
        history.push("/home")
        history.push("/tmp")
        history.back()
        assertTrue(history.canGoForward)
    }

    @Test
    fun `forward returns the path we went back from`() {
        history.push("/home")
        history.push("/tmp")
        history.back()
        assertEquals("/tmp", history.forward())
    }

    @Test
    fun `push truncates forward history`() {
        history.push("/a")
        history.push("/b")
        history.push("/c")
        history.back()         // at /b, can go forward to /c
        history.push("/d")     // should clear /c from forward stack
        assertFalse(history.canGoForward)
    }

    @Test
    fun `getRecentPaths returns most recent first`() {
        history.push("/a")
        history.push("/b")
        history.push("/c")
        val recent = history.getRecentPaths(10)
        assertEquals(listOf("/c", "/b", "/a"), recent)
    }

    @Test
    fun `getRecentPaths respects limit`() {
        history.push("/a")
        history.push("/b")
        history.push("/c")
        val recent = history.getRecentPaths(2)
        assertEquals(2, recent.size)
        assertEquals("/c", recent[0])
        assertEquals("/b", recent[1])
    }

    @Test
    fun `getRecentPaths excludes forward history after back`() {
        history.push("/a")
        history.push("/b")
        history.push("/c")
        history.back()         // at /b, /c is forward history
        val recent = history.getRecentPaths(10)
        assertFalse(recent.contains("/c"))
        assertTrue(recent.contains("/b"))
        assertTrue(recent.contains("/a"))
    }

    @Test
    fun `multiple back calls work correctly`() {
        history.push("/a")
        history.push("/b")
        history.push("/c")
        assertEquals("/b", history.back())
        assertEquals("/a", history.back())
        assertFalse(history.canGoBack)
    }

    @Test
    fun `multiple forward calls work correctly`() {
        history.push("/a")
        history.push("/b")
        history.push("/c")
        history.back()
        history.back()
        assertEquals("/b", history.forward())
        assertEquals("/c", history.forward())
        assertFalse(history.canGoForward)
    }
}
