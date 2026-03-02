package ro.faur.explorer.sftp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.ConnectionProfile
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator

class AuthenticationTest : EmbeddedSshTestBase() {

    private val managers = mutableListOf<SftpConnectionManager>()
    private lateinit var keyFile: Path

    private fun newManager(): SftpConnectionManager {
        return SftpConnectionManager(CONNECT_TIMEOUT_MS, testVerifier()).also { managers.add(it) }
    }

    @BeforeEach
    fun generateTestKey() {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val kp = kpg.generateKeyPair()

        // Write private key in PEM format for the SSH client
        keyFile = Files.createTempFile("test-key", ".pem")
        val encoded = java.util.Base64.getMimeEncoder(64, "\n".toByteArray())
            .encodeToString(kp.private.encoded)
        Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n$encoded\n-----END PRIVATE KEY-----\n")

        // Register public key with the server
        sshServer.publickeyAuthenticator = PublickeyAuthenticator { _, key, _ ->
            key.encoded.contentEquals(kp.public.encoded)
        }
    }

    @AfterEach
    fun shutdownManagers() {
        managers.forEach { runCatching { it.shutdown() } }
        Files.deleteIfExists(keyFile)
    }

    @Test
    fun `password auth with valid credentials succeeds`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "pass-ok", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        val session = manager.connect(profile, TEST_PASSWORD)
        assertTrue(session.isOpen)
    }

    @Test
    fun `password auth with null password throws`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "pass-null", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        assertThrows(IllegalArgumentException::class.java) {
            manager.connect(profile, null)
        }
    }

    @Test
    fun `password auth with wrong password fails`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "pass-wrong", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        )
        assertThrows(Exception::class.java) {
            manager.connect(profile, "wrong-password")
        }
    }

    @Test
    fun `key file auth with valid key succeeds`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "key-ok", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.KEY_FILE,
            keyFilePath = keyFile.toAbsolutePath().toString(),
        )
        val session = manager.connect(profile, null)
        assertTrue(session.isOpen)
    }

    @Test
    fun `key file auth with non-existent path throws IOException`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "key-missing", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.KEY_FILE,
            keyFilePath = "/nonexistent/path/id_rsa",
        )
        assertThrows(Exception::class.java) {
            manager.connect(profile, null)
        }
    }

    @Test
    fun `agent auth with no agent fails gracefully`() {
        val manager = newManager()
        val profile = ConnectionProfile(
            name = "agent-none", host = "localhost", port = serverPort,
            username = TEST_USER, authMethod = ConnectionProfile.AuthMethod.AGENT,
        )
        // Agent auth should fail (no agent running in test environment) but not crash
        assertThrows(Exception::class.java) {
            manager.connect(profile, null)
        }
    }
}
