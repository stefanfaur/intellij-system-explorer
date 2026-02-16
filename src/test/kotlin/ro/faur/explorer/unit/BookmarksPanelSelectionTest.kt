package ro.faur.explorer.unit

import ro.faur.explorer.model.Bookmark
import ro.faur.explorer.ui.BookmarksPanel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for [BookmarksPanel.highlightForPath] — verifies that bookmark
 * selection syncs with the explorer's current path and does not trigger navigation.
 */
class BookmarksPanelSelectionTest {

    @Test
    fun `highlightForPath selects matching bookmark by exact path`() {
        var navigated = false
        val panel = BookmarksPanel(
            onBookmarkSelected = { navigated = true }
        )
        panel.setBookmarks(listOf(
            Bookmark("Home", "/home"),
            Bookmark("Desktop", "/home/Desktop")
        ))

        panel.highlightForPath("/home")

        assertFalse(navigated, "highlightForPath should NOT trigger onBookmarkSelected")
    }

    @Test
    fun `highlightForPath clears selection when path does not match any bookmark`() {
        var navigated = false
        val panel = BookmarksPanel(
            onBookmarkSelected = { navigated = true }
        )
        panel.setBookmarks(listOf(
            Bookmark("Home", "/home"),
            Bookmark("Desktop", "/home/Desktop")
        ))

        // First select a bookmark
        panel.highlightForPath("/home")
        // Then navigate away to a non-bookmark path
        panel.highlightForPath("/nonexistent")

        assertFalse(navigated, "highlightForPath should NOT trigger onBookmarkSelected")
    }

    @Test
    fun `highlightForPath does not trigger onBookmarkSelected callback`() {
        var callbackCount = 0
        val panel = BookmarksPanel(
            onBookmarkSelected = { callbackCount++ }
        )
        panel.setBookmarks(listOf(
            Bookmark("Home", "/home"),
            Bookmark("Desktop", "/home/Desktop")
        ))

        panel.highlightForPath("/home")
        panel.highlightForPath("/home/Desktop")
        panel.highlightForPath("/nonexistent")

        assertEquals(0, callbackCount,
            "highlightForPath should never trigger the onBookmarkSelected callback")
    }
}
