package ro.faur.explorer.light.remote

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.remote.RemoteBookmarkManager
import ro.faur.explorer.remote.RemoteBookmark

class RemoteBookmarkManagerTest : BasePlatformTestCase() {

    private lateinit var manager: RemoteBookmarkManager

    override fun setUp() {
        super.setUp()
        manager = RemoteBookmarkManager.getInstance(project)
        manager.clearAll()
    }

    fun `test add bookmark under connection`() {
        manager.addBookmark("prod-server", RemoteBookmark("/var/www", "Web Root"))
        val bookmarks = manager.getBookmarks("prod-server")
        assertEquals(1, bookmarks.size)
        assertEquals("/var/www", bookmarks[0].path)
        assertEquals("Web Root", bookmarks[0].label)
    }

    fun `test bookmarks are per-connection`() {
        manager.addBookmark("prod", RemoteBookmark("/var/www", "Web"))
        manager.addBookmark("uat", RemoteBookmark("/home/app", "App"))
        assertEquals(1, manager.getBookmarks("prod").size)
        assertEquals(1, manager.getBookmarks("uat").size)
        assertEquals("/var/www", manager.getBookmarks("prod")[0].path)
        assertEquals("/home/app", manager.getBookmarks("uat")[0].path)
    }

    fun `test remove bookmark`() {
        manager.addBookmark("prod", RemoteBookmark("/a", "A"))
        manager.addBookmark("prod", RemoteBookmark("/b", "B"))
        manager.removeBookmark("prod", "/a")
        assertEquals(1, manager.getBookmarks("prod").size)
        assertEquals("/b", manager.getBookmarks("prod")[0].path)
    }

    fun `test getConnectionNames returns connections with bookmarks`() {
        manager.addBookmark("alpha", RemoteBookmark("/x", "X"))
        manager.addBookmark("beta", RemoteBookmark("/y", "Y"))
        val names = manager.getConnectionNames()
        assertTrue("alpha" in names)
        assertTrue("beta" in names)
    }

    fun `test state persists across serialization`() {
        manager.addBookmark("server", RemoteBookmark("/etc/nginx", "Nginx"))
        val state = manager.state
        val fresh = RemoteBookmarkManager()
        fresh.loadState(state)
        val bookmarks = fresh.getBookmarks("server")
        assertEquals(1, bookmarks.size)
        assertEquals("/etc/nginx", bookmarks[0].path)
    }

    fun `test clearConnection removes bookmarks for one connection only`() {
        manager.addBookmark("prod", RemoteBookmark("/a", "A"))
        manager.addBookmark("uat", RemoteBookmark("/b", "B"))
        manager.clearConnection("prod")
        assertTrue(manager.getBookmarks("prod").isEmpty())
        assertEquals(1, manager.getBookmarks("uat").size)
    }
}
