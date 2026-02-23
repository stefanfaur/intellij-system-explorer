package ro.faur.explorer.sftp

import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator
import org.apache.sshd.common.config.keys.KeyUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.SftpFileOperations
import java.nio.file.Files
import java.security.KeyPairGenerator

class SftpFileOperationsKeyAuthTest : EmbeddedSshTestBase() {

    private lateinit var keyFilePath: String

    @BeforeEach
    fun setupKeyAuth() {
        // Generate RSA key pair for test
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val keyPair = gen.generateKeyPair()

        // Write private key to temp file in OpenSSH PEM format
        val keyFile = Files.createTempFile("test-key", ".pem")
        keyFile.toFile().writeText(encodePemPrivateKey(keyPair.private))
        keyFilePath = keyFile.toAbsolutePath().toString()

        // Configure server to accept this public key for TEST_USER
        sshServer.publickeyAuthenticator = PublickeyAuthenticator { username, key, _ ->
            username == TEST_USER && KeyUtils.compareKeys(key, keyPair.public)
        }
    }

    @AfterEach
    fun cleanupKey() {
        Files.deleteIfExists(java.nio.file.Paths.get(keyFilePath))
    }

    @Test
    fun `createWithKeyFile connects and lists root directory`() {
        val ops = SftpFileOperations.createWithKeyFile(
            "localhost", serverPort, TEST_USER, keyFilePath
        )
        ops.use {
            val entries = it.listDirectory("/")
            assertTrue(entries.any { e -> e.name == "readme.txt" })
        }
    }

    @Test
    fun `createWithKeyFile throws on wrong key file path`() {
        assertThrows(Exception::class.java) {
            SftpFileOperations.createWithKeyFile(
                "localhost", serverPort, TEST_USER, "/nonexistent/key"
            )
        }
    }

    // Minimal PEM encoder — only needed for test key generation
    private fun encodePemPrivateKey(key: java.security.PrivateKey): String {
        val encoded = key.encoded
        val b64 = java.util.Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(encoded)
        return "-----BEGIN PRIVATE KEY-----\n$b64\n-----END PRIVATE KEY-----\n"
    }
}
