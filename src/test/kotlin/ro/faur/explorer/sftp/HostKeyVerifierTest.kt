package ro.faur.explorer.sftp

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import ro.faur.explorer.remote.security.HostKeyVerifier
import java.nio.file.Files
import java.nio.file.Path

class HostKeyVerifierTest {

    private lateinit var tempDir: Path
    private lateinit var knownHostsFile: Path
    private lateinit var server: SshServer
    private var port: Int = 0

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("hostkey-test")
        knownHostsFile = tempDir.resolve("known_hosts")

        server = SshServer.setUpDefaultServer().apply {
            this.port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(tempDir.resolve("server_key"))
            passwordAuthenticator = org.apache.sshd.server.auth.password.PasswordAuthenticator { u, p, _ -> true }
        }
        server.start()
        port = server.port
    }

    @AfterEach
    fun teardown() {
        server.stop(true)
        Thread.sleep(500)
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `accepts known host with matching key`() {
        val verifier = HostKeyVerifier(knownHostsFile, autoAcceptUnknown = true)
        connectAndVerify(verifier)
        val verifier2 = HostKeyVerifier(knownHostsFile, autoAcceptUnknown = false)
        val result = connectAndVerify(verifier2)
        assertTrue(result)
    }

    @Test
    fun `rejects host with changed key`() {
        val verifier = HostKeyVerifier(knownHostsFile, autoAcceptUnknown = true)
        connectAndVerify(verifier)

        server.stop(true)
        Thread.sleep(500)
        Files.delete(tempDir.resolve("server_key"))
        server = SshServer.setUpDefaultServer().apply {
            this.port = this@HostKeyVerifierTest.port
            keyPairProvider = SimpleGeneratorHostKeyProvider(tempDir.resolve("server_key_new"))
            passwordAuthenticator = org.apache.sshd.server.auth.password.PasswordAuthenticator { _, _, _ -> true }
        }
        server.start()

        val verifier2 = HostKeyVerifier(knownHostsFile, autoAcceptUnknown = false)
        assertThrows(Exception::class.java) {
            connectAndVerify(verifier2)
        }
    }

    @Test
    fun `unknown host is flagged for TOFU decision`() {
        val decisions = mutableListOf<String>()
        val verifier = HostKeyVerifier(
            knownHostsFile,
            autoAcceptUnknown = false,
            tofuCallback = { host, fingerprint ->
                decisions.add("$host:$fingerprint")
                false
            }
        )
        assertThrows(Exception::class.java) {
            connectAndVerify(verifier)
        }
        assertTrue(decisions.isNotEmpty())
        assertTrue(decisions[0].startsWith("localhost:"))
    }

    @Test
    fun `TOFU accept writes to known_hosts`() {
        val verifier = HostKeyVerifier(
            knownHostsFile,
            autoAcceptUnknown = false,
            tofuCallback = { _, _ -> true }
        )
        connectAndVerify(verifier)
        assertTrue(Files.exists(knownHostsFile))
        val content = Files.readString(knownHostsFile)
        assertTrue(content.isNotEmpty())
    }

    private fun connectAndVerify(verifier: HostKeyVerifier): Boolean {
        val client = SshClient.setUpDefaultClient()
        client.serverKeyVerifier = verifier.asServerKeyVerifier()
        client.start()
        try {
            val session: ClientSession = client.connect("user", "localhost", port)
                .verify(10_000).session
            session.addPasswordIdentity("pass")
            session.auth().verify(8_000)
            val result = session.isAuthenticated
            session.close()
            return result
        } finally {
            client.stop()
        }
    }
}
