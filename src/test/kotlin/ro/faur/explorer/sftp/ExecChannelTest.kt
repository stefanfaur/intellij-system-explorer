package ro.faur.explorer.sftp

import org.apache.sshd.server.command.CommandFactory
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import ro.faur.explorer.remote.RemoteTailService
import java.io.OutputStream
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ExecChannelTest : EmbeddedSshTestBase() {

    @BeforeEach
    fun configureTailCommand() {
        sshServer.commandFactory = CommandFactory { _, command ->
            if (command.startsWith("tail")) {
                FakeTailCommand()
            } else {
                null
            }
        }
    }

    @Test
    fun `tail receives streamed output`() {
        val tailService = RemoteTailService.create(
            "localhost", serverPort, TEST_USER, TEST_PASSWORD
        )
        val lines = mutableListOf<String>()
        val receivedLatch = CountDownLatch(3)
        tailService.startTail("/var/log/app.log", initialLines = 100) { line ->
            lines.add(line)
            receivedLatch.countDown()
        }
        assertTrue(receivedLatch.await(5, TimeUnit.SECONDS), "Should receive at least 3 lines")
        assertTrue(lines.size >= 3)
        tailService.stopTail("/var/log/app.log")
        tailService.close()
    }

    @Test
    fun `stop tail closes the channel`() {
        val tailService = RemoteTailService.create(
            "localhost", serverPort, TEST_USER, TEST_PASSWORD
        )
        tailService.startTail("/var/log/test.log", initialLines = 10) { _ -> }
        assertTrue(tailService.isActive("/var/log/test.log"))
        tailService.stopTail("/var/log/test.log")
        assertFalse(tailService.isActive("/var/log/test.log"))
        tailService.close()
    }

    @Test
    fun `multiple concurrent tails work independently`() {
        val tailService = RemoteTailService.create(
            "localhost", serverPort, TEST_USER, TEST_PASSWORD
        )
        val lines1 = mutableListOf<String>()
        val lines2 = mutableListOf<String>()
        tailService.startTail("/log/a.log", 10) { lines1.add(it) }
        tailService.startTail("/log/b.log", 10) { lines2.add(it) }
        Thread.sleep(1000)
        assertTrue(tailService.isActive("/log/a.log"))
        assertTrue(tailService.isActive("/log/b.log"))
        tailService.stopAll()
        tailService.close()
    }

    private class FakeTailCommand : Command {
        private lateinit var out: OutputStream
        private lateinit var exitCallback: ExitCallback
        @Volatile private var running = true

        override fun setInputStream(input: InputStream?) {}
        override fun setOutputStream(out: OutputStream) { this.out = out }
        override fun setErrorStream(err: OutputStream?) {}
        override fun setExitCallback(callback: ExitCallback) { this.exitCallback = callback }

        override fun start(channel: ChannelSession, env: Environment) {
            Thread {
                var i = 0
                try {
                    while (running) {
                        out.write("log line ${i++}\n".toByteArray())
                        out.flush()
                        Thread.sleep(100)
                    }
                } catch (_: Exception) {}
                exitCallback.onExit(0)
            }.apply { isDaemon = true }.start()
        }

        override fun destroy(channel: ChannelSession) {
            running = false
        }
    }
}
