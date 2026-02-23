package ro.faur.explorer.sftp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.ConnectionProfile

class SftpConnectionManagerTest : EmbeddedSshTestBase() {

    private val managers = mutableListOf<SftpConnectionManager>()

    private fun newManager(connectTimeoutMs: Long = 10_000L): SftpConnectionManager {
        return SftpConnectionManager(connectTimeoutMs).also { managers.add(it) }
    }

    @AfterEach
    fun shutdownManagers() {
        managers.forEach { runCatching { it.shutdown() } }
    }

    @Test
    fun `connect with valid credentials succeeds`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "test", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        val session = manager.connect(profile, TEST_PASSWORD)
        assertTrue(session.isOpen)
        assertTrue(session.isAuthenticated)
        manager.disconnect(profile.name)
    }

    @Test
    fun `connect with wrong password fails`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "test", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        assertThrows(Exception::class.java) {
            manager.connect(profile, "wrongpassword")
        }
    }

    @Test
    fun `connect to non-existent host fails with timeout`() {
        val manager = newManager(connectTimeoutMs = 2000)
        val profile = ConnectionProfile(
            name = "bad", host = "192.0.2.1", port = 22,
            username = "nobody", authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        assertThrows(Exception::class.java) {
            manager.connect(profile, "pass")
        }
    }

    @Test
    fun `disconnect closes session`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "test", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        val session = manager.connect(profile, TEST_PASSWORD)
        assertTrue(session.isOpen)
        manager.disconnect("test")
        assertFalse(session.isOpen)
    }

    @Test
    fun `isConnected returns correct state`() {
        val manager = newManager()
        assertFalse(manager.isConnected("test"))
        val profile = ConnectionProfile(
            name = "test", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        manager.connect(profile, TEST_PASSWORD)
        assertTrue(manager.isConnected("test"))
        manager.disconnect("test")
        assertFalse(manager.isConnected("test"))
    }

    @Test
    fun `getSftpClient returns functional client after connect`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "test", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        manager.connect(profile, TEST_PASSWORD)
        val sftpClient = manager.getSftpClient("test")
        assertNotNull(sftpClient)
        val entries = sftpClient!!.readDir("/").toList()
        assertTrue(entries.isNotEmpty())
        manager.disconnect("test")
    }

    @Test
    fun `getSession returns open ClientSession after connect`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "test", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        manager.connect(profile, TEST_PASSWORD)
        val session = manager.getSession("test")
        assertNotNull(session)
        assertTrue(session!!.isOpen)
        manager.disconnect("test")
    }

    @Test
    fun `getSession returns null for unknown connection`() {
        val manager = newManager()
        assertNull(manager.getSession("nonexistent"))
    }

    @Test
    fun `getSession returns null after disconnect`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "test", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        manager.connect(profile, TEST_PASSWORD)
        manager.disconnect("test")
        assertNull(manager.getSession("test"))
    }
}
