package ro.faur.explorer.model

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.Logger
import ro.faur.explorer.quickopen.ranking.FrecencyStore

@State(
    name = "ro.faur.explorer.model.BookmarkManager",
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
        try {
            FrecencyStore.getInstance().setBookmarked(bookmark.path, true)
        } catch (e: Exception) {
            LOG.warn("FrecencyStore.setBookmarked failed for ${bookmark.path}", e)
        }
    }

    fun removeBookmark(path: String) {
        myState.bookmarks.removeAll { it.path == path }
        try {
            FrecencyStore.getInstance().setBookmarked(path, false)
        } catch (e: Exception) {
            LOG.warn("FrecencyStore.setBookmarked(false) failed for $path", e)
        }
    }

    fun moveBookmark(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex) return
        val bookmarks = myState.bookmarks
        if (fromIndex < 0 || fromIndex >= bookmarks.size) return
        if (toIndex < 0 || toIndex >= bookmarks.size) return
        val entry = bookmarks.removeAt(fromIndex)
        // After removal the list is one shorter; clamp to avoid IOOBE on edge cases
        val insertAt = toIndex.coerceAtMost(bookmarks.size)
        bookmarks.add(insertAt, entry)
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
        private val LOG = Logger.getInstance(BookmarkManager::class.java)

        fun getInstance(): BookmarkManager =
            ApplicationManager.getApplication().getService(BookmarkManager::class.java)
    }
}
