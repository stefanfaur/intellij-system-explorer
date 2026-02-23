package ro.faur.explorer.sftp

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.ConnectionProfile

class ConnectionLifecycleTest : EmbeddedSshTestBase() {

    private val managers = mutableListOf<SftpConnectionManager>()

    private fun newManager(): SftpConnectionManager {
        return SftpConnectionManager().also { managers.add(it) }
    }

    @AfterEach
    fun shutdownManagers() {
        managers.forEach { runCatching { it.shutdown() } }
    }

    @Test
    fun `reconnect after disconnect succeeds`() {
        val manager = newManager()
        val profile = testProfile()
        manager.connect(profile, TEST_PASSWORD)
        manager.disconnect(profile.name)
        val session = manager.connect(profile, TEST_PASSWORD)
        assertTrue(session.isOpen)
        manager.disconnect(profile.name)
    }

    @Test
    fun `reconnect after server restart succeeds`() {
        val manager = newManager()
        val profile = testProfile()
        manager.connect(profile, TEST_PASSWORD)
        manager.disconnect(profile.name)

        // MINA SSHD cannot restart a stopped server — create a new instance
        sshServer.stop(true)
        Thread.sleep(500)
        sshServer = SshServer.setUpDefaultServer().apply {
            port = serverPort // reuse the same port
            keyPairProvider = SimpleGeneratorHostKeyProvider(serverRoot.resolve(".hostkey"))
            passwordAuthenticator = PasswordAuthenticator { u, p, _ ->
                u == TEST_USER && p == TEST_PASSWORD
            }
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(serverRoot)
        }
        sshServer.start()

        val session = manager.connect(profile, TEST_PASSWORD)
        assertTrue(session.isOpen)
        manager.disconnect(profile.name)
    }

    @Test
    fun `session detects server shutdown`() {
        val manager = newManager()
        val profile = testProfile()
        manager.connect(profile, TEST_PASSWORD)
        assertTrue(manager.isConnected(profile.name))
        sshServer.stop(true)
        Thread.sleep(1000)
        // After server shutdown, SFTP operations should throw
        assertThrows(Exception::class.java) {
            val client = manager.getSftpClient(profile.name)
            client?.readDir("/")
        }
    }

    @Test
    fun `multiple connections to same host coexist`() {
        val manager = newManager()
        val profile1 = testProfile("conn1")
        val profile2 = testProfile("conn2")
        manager.connect(profile1, TEST_PASSWORD)
        manager.connect(profile2, TEST_PASSWORD)
        assertTrue(manager.isConnected("conn1"))
        assertTrue(manager.isConnected("conn2"))
        manager.disconnect("conn1")
        assertFalse(manager.isConnected("conn1"))
        assertTrue(manager.isConnected("conn2"))
        manager.disconnect("conn2")
    }

    private fun testProfile(name: String = "test") = ConnectionProfile(
        name = name,
        host = "localhost",
        port = serverPort,
        username = TEST_USER,
        authMethod = ConnectionProfile.AuthMethod.PASSWORD,
    )
}
