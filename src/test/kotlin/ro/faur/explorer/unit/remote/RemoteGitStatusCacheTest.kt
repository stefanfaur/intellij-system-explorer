package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.git.RemoteGitStatusCache
import ro.faur.explorer.remote.git.GitFileStatus

class RemoteGitStatusCacheTest {

    @BeforeEach
    fun setup() {
        RemoteGitStatusCache.clear()
    }

    @Test
    fun `returns null for uncached path`() {
        assertNull(RemoteGitStatusCache.getStatus("conn", "/some/file"))
    }

    @Test
    fun `returns status after update`() {
        RemoteGitStatusCache.update("conn", "/repo", listOf(
            "1 .M N... 100644 100644 100644 abc def src/Main.kt"
        ))
        assertEquals(GitFileStatus.MODIFIED, RemoteGitStatusCache.getStatus("conn", "/repo/src/Main.kt"))
    }

    @Test
    fun `returns untracked status`() {
        RemoteGitStatusCache.update("conn", "/repo", listOf(
            "? newfile.txt"
        ))
        assertEquals(GitFileStatus.UNTRACKED, RemoteGitStatusCache.getStatus("conn", "/repo/newfile.txt"))
    }

    @Test
    fun `invalidate clears connection status`() {
        RemoteGitStatusCache.update("conn", "/repo", listOf(
            "1 .M N... 100644 100644 100644 abc def file.kt"
        ))
        RemoteGitStatusCache.invalidate("conn")
        assertNull(RemoteGitStatusCache.getStatus("conn", "/repo/file.kt"))
    }

    @Test
    fun `different connections have separate caches`() {
        RemoteGitStatusCache.update("conn1", "/repo", listOf(
            "1 .M N... 100644 100644 100644 abc def file.kt"
        ))
        assertNull(RemoteGitStatusCache.getStatus("conn2", "/repo/file.kt"))
    }
}
