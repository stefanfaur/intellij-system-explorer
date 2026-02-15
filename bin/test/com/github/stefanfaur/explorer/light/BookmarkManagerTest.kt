package com.github.stefanfaur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.model.BookmarkManager
import com.github.stefanfaur.explorer.model.Bookmark

class BookmarkManagerTest : BasePlatformTestCase() {

    private lateinit var manager: BookmarkManager

    override fun setUp() {
        super.setUp()
        manager = BookmarkManager.getInstance()
        // Clear any state from previous tests
        manager.clearAll()
    }

    fun `test initial bookmarks contain default locations`() {
        manager.initDefaults()
        val bookmarks = manager.getBookmarks()

        assertTrue(bookmarks.any { it.name == "Home" })
        assertTrue(bookmarks.any { it.name == "Desktop" })
        assertTrue(bookmarks.any { it.name == "Downloads" })
    }

    fun `test add bookmark`() {
        manager.addBookmark(Bookmark("Custom", "/custom/path"))

        val bookmarks = manager.getBookmarks()
        assertEquals(1, bookmarks.size)
        assertEquals("Custom", bookmarks[0].name)
        assertEquals("/custom/path", bookmarks[0].path)
    }

    fun `test remove bookmark by path`() {
        manager.addBookmark(Bookmark("A", "/path/a"))
        manager.addBookmark(Bookmark("B", "/path/b"))

        manager.removeBookmark("/path/a")

        val bookmarks = manager.getBookmarks()
        assertEquals(1, bookmarks.size)
        assertEquals("B", bookmarks[0].name)
    }

    fun `test remove nonexistent bookmark is no-op`() {
        manager.addBookmark(Bookmark("A", "/path/a"))
        manager.removeBookmark("/nonexistent")

        assertEquals(1, manager.getBookmarks().size)
    }

    fun `test reorder bookmarks`() {
        manager.addBookmark(Bookmark("A", "/a"))
        manager.addBookmark(Bookmark("B", "/b"))
        manager.addBookmark(Bookmark("C", "/c"))

        manager.moveBookmark(fromIndex = 2, toIndex = 0)

        val names = manager.getBookmarks().map { it.name }
        assertEquals(listOf("C", "A", "B"), names)
    }

    fun `test duplicate path not added twice`() {
        manager.addBookmark(Bookmark("First", "/same/path"))
        manager.addBookmark(Bookmark("Second", "/same/path"))

        assertEquals(1, manager.getBookmarks().size)
        assertEquals("First", manager.getBookmarks()[0].name)
    }

    fun `test bookmarks persist via state serialization`() {
        manager.addBookmark(Bookmark("Persisted", "/persist/path"))

        val state = manager.state

        val fresh = BookmarkManager()
        fresh.loadState(state)

        val bookmarks = fresh.getBookmarks()
        assertEquals(1, bookmarks.size)
        assertEquals("Persisted", bookmarks[0].name)
        assertEquals("/persist/path", bookmarks[0].path)
    }

    fun `test clearAll removes all bookmarks`() {
        manager.addBookmark(Bookmark("A", "/a"))
        manager.addBookmark(Bookmark("B", "/b"))

        manager.clearAll()

        assertTrue(manager.getBookmarks().isEmpty())
    }
}
