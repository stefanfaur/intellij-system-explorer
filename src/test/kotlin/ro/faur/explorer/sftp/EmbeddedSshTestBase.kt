package ro.faur.explorer.sftp

import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.server.auth.password.PasswordAuthenticator
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Base class for tests that need an embedded SSH/SFTP server.
 *
 * NOTE on test tier: This class lives in the `sftp/` top-level test package,
 * which is excluded from `./gradlew test` by default. Run with:
 *   ./gradlew test -PincludeSftpTests=true --tests "ro.faur.explorer.sftp.*"
 *
 * NOTE on JUnit style: This is a JUnit 5 base class using @BeforeEach/@AfterEach,
 * NOT a BasePlatformTestCase. It does NOT require the IntelliJ platform.
 * Light/heavy tests that need BOTH the embedded server AND the IntelliJ platform
 * should compose this via delegation rather than inheritance.
 */
abstract class EmbeddedSshTestBase {

    protected lateinit var sshServer: SshServer
    protected lateinit var serverRoot: Path
    protected var serverPort: Int = 0

    companion object {
        const val TEST_USER = "testuser"
        const val TEST_PASSWORD = "testpass"
        const val CONNECT_TIMEOUT_MS = 10_000L
        const val AUTH_TIMEOUT_MS = 8_000L
    }

    @BeforeEach
    fun startServer() {
        serverRoot = Files.createTempDirectory("sftp-test")
        createTestFileStructure()

        sshServer = SshServer.setUpDefaultServer().apply {
            port = 0 // OS-assigned port
            keyPairProvider = SimpleGeneratorHostKeyProvider(
                serverRoot.resolve(".hostkey")
            )
            passwordAuthenticator = PasswordAuthenticator { username, password, _ ->
                username == TEST_USER && password == TEST_PASSWORD
            }
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(serverRoot)
            commandFactory = ProcessShellCommandFactory(serverRoot)
        }
        sshServer.start()
        serverPort = sshServer.port
    }

    @AfterEach
    fun stopServer() {
        sshServer.stop(true)
        // Allow SSH server threads to fully drain before ThreadLeakTracker runs.
        Thread.sleep(500)
        serverRoot.toFile().deleteRecursively()
    }

    private fun createTestFileStructure() {
        // Create standard test directories and files
        Files.createDirectories(serverRoot.resolve("config"))
        Files.createDirectories(serverRoot.resolve("logs"))
        Files.createDirectories(serverRoot.resolve("data"))
        Files.createDirectories(serverRoot.resolve(".hidden"))

        Files.writeString(serverRoot.resolve("config/app.yml"), "server:\n  port: 8080\n")
        Files.writeString(serverRoot.resolve("config/.env"), "SECRET=abc123\n")
        Files.writeString(serverRoot.resolve("logs/access.log"), "127.0.0.1 - GET /\n")
        Files.writeString(serverRoot.resolve("readme.txt"), "Hello World\n")

        // Symlink
        Files.createSymbolicLink(
            serverRoot.resolve("config-link"),
            serverRoot.resolve("config")
        )
    }

    protected fun createLargeFile(name: String, sizeMb: Int): Path {
        val file = serverRoot.resolve(name)
        val chunk = ByteArray(1024 * 1024) // 1 MB
        Files.newOutputStream(file).use { out ->
            repeat(sizeMb) { out.write(chunk) }
        }
        return file
    }
}

/**
 * Custom CommandFactory for the embedded test server.
 * MINA SSHD 2.17.x does NOT have ProcessShellCommandFactory.
 * This implementation delegates commands to ProcessBuilder, which is
 * sufficient for testing exec channels (tail, git commands, etc.).
 */
private class ProcessShellCommandFactory(private val workDir: Path) : CommandFactory {
    override fun createCommand(channel: ChannelSession, command: String): Command {
        return object : Command {
            private lateinit var input: InputStream
            private lateinit var output: OutputStream
            private lateinit var error: OutputStream
            private lateinit var exitCallback: ExitCallback

            override fun setInputStream(input: InputStream) { this.input = input }
            override fun setOutputStream(out: OutputStream) { this.output = out }
            override fun setErrorStream(err: OutputStream) { this.error = err }
            override fun setExitCallback(callback: ExitCallback) { this.exitCallback = callback }

            override fun start(channel: ChannelSession, env: Environment) {
                Thread {
                    try {
                        val process = ProcessBuilder("sh", "-c", command)
                            .directory(workDir.toFile())
                            .redirectErrorStream(false)
                            .start()
                        process.inputStream.copyTo(output)
                        process.errorStream.copyTo(error)
                        val exitCode = process.waitFor()
                        output.flush()
                        error.flush()
                        exitCallback.onExit(exitCode)
                    } catch (e: Exception) {
                        error.write(e.message?.toByteArray() ?: byteArrayOf())
                        error.flush()
                        exitCallback.onExit(1, e.message)
                    }
                }.apply { isDaemon = true }.start()
            }

            override fun destroy(channel: ChannelSession) {}
        }
    }
}
