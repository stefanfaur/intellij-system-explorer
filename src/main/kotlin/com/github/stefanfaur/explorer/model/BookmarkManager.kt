package com.github.stefanfaur.explorer.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "com.github.stefanfaur.explorer.model.BookmarkManager",
    storages = [Storage("explorerBookmarks.xml")]
)
class BookmarkManager : PersistentStateComponent<BookmarkManager.State> {

    /**
     * XML-serializable state class. Uses mutable properties with defaults
     * so the IntelliJ serializer can instantiate and populate it.
     */
    class BookmarkEntry {
        var name: String = ""
        var path: String = ""

        // No-arg constructor required for XML serialization
        constructor()

        constructor(name: String, path: String) {
            this.name = name
            this.path = path
        }
    }

    class State {
        var bookmarks: MutableList<BookmarkEntry> = mutableListOf()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    fun getBookmarks(): List<Bookmark> =
        myState.bookmarks.map { Bookmark(it.name, it.path) }

    fun addBookmark(bookmark: Bookmark) {
        if (myState.bookmarks.any { it.path == bookmark.path }) {
            return
        }
        myState.bookmarks.add(BookmarkEntry(bookmark.name, bookmark.path))
    }

    fun removeBookmark(path: String) {
        myState.bookmarks.removeAll { it.path == path }
    }

    fun moveBookmark(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || fromIndex >= myState.bookmarks.size) return
        if (toIndex < 0 || toIndex >= myState.bookmarks.size) return

        val entry = myState.bookmarks.removeAt(fromIndex)
        myState.bookmarks.add(toIndex, entry)
    }

    fun clearAll() {
        myState.bookmarks.clear()
    }

    fun initDefaults() {
        val home = System.getProperty("user.home") ?: "/"
        addBookmark(Bookmark("Home", home))
        addBookmark(Bookmark("Desktop", "$home/Desktop"))
        addBookmark(Bookmark("Downloads", "$home/Downloads"))
    }

    companion object {
        fun getInstance(): BookmarkManager =
            ApplicationManager.getApplication().getService(BookmarkManager::class.java)
    }
}
