package ro.faur.explorer.unit

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import ro.faur.explorer.quickopen.backend.SshFindEnumerator
import ro.faur.explorer.remote.SftpConnectionManager
import org.apache.sshd.client.session.ClientSession

/**
 * Unit tests for SshFindEnumerator
 *
 * Tests the SSH find-based file enumerator that enables Quick Open to work over
 * remote SSH connections using `find` command via exec channel.
 */
class SshFindEnumeratorTest {

    private lateinit var mockSession: ClientSession
    private lateinit var mockConnectionManager: SftpConnectionManager

    @BeforeEach
    fun setUp() {
        mockSession = mock(ClientSession::class.java)
        mockConnectionManager = mock(SftpConnectionManager::class.java)
    }

    @Test
    fun `name returns SshFindEnumerator`() {
        val enumerator = SshFindEnumerator(mockConnectionManager, "test-conn", "/home/user")
        assertEquals("SshFindEnumerator", enumerator.name)
    }

    @Test
    fun `isAvailable returns false when not connected`() {
        `when`(mockConnectionManager.isConnected("test-conn")).thenReturn(false)
        val enumerator = SshFindEnumerator(mockConnectionManager, "test-conn", "/home/user")
        assertFalse(enumerator.isAvailable())
    }

    @Test
    fun `isAvailable returns true when connected`() {
        `when`(mockConnectionManager.isConnected("test-conn")).thenReturn(true)
        val enumerator = SshFindEnumerator(mockConnectionManager, "test-conn", "/home/user")
        assertTrue(enumerator.isAvailable())
    }

    @Test
    fun `enumerate returns empty when no SSH session`() = runBlocking {
        `when`(mockConnectionManager.getSession("test-conn")).thenReturn(null)
        val enumerator = SshFindEnumerator(mockConnectionManager, "test-conn", "/home/user")
        val paths = enumerator.enumerate("/home/user", 100).toList()
        assertTrue(paths.isEmpty())
    }
}
