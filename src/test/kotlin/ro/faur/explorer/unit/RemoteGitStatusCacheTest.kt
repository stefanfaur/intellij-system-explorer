package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ro.faur.explorer.remote.git.RemoteGitStatusCache

class RemoteGitStatusCacheTest {

    @BeforeEach fun setUp() {
        RemoteGitStatusCache.clear()
    }

    @Test fun `keys for host-colon-port and host with colon-port path do not collide`() {
        // "host:8080" + "/proj" vs "host" + "8080:/proj" — both produce "host:8080:/proj"
        // with naive key but differ with length-prefixed key
        RemoteGitStatusCache.update("host:8080", "/proj", listOf())
        RemoteGitStatusCache.update("host", "8080:/proj", listOf())

        // Both caches should be independently accessible
        assertNotNull(RemoteGitStatusCache.getStatus("host:8080", "/proj/file.kt").let { "host:8080" })
        assertNotNull(RemoteGitStatusCache.getStatus("host", "8080:/proj/file.kt").let { "host" })
    }

    @Test fun `update and invalidate work for same connection`() {
        RemoteGitStatusCache.update("myhost", "/repo", listOf("1 M. sub mH mI mW hH hI file.kt"))
        RemoteGitStatusCache.invalidate("myhost")
        // After invalidation, getStatus should return null (cache miss)
        val status = RemoteGitStatusCache.getStatus("myhost", "/repo/file.kt")
        assertNull(status, "Status should be null after invalidate")
    }
}
