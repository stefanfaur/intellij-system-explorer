package ro.faur.explorer.remote

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.channel.ChannelExec
import org.apache.sshd.client.channel.ClientChannelEvent
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages `tail -f` sessions over SSH exec channels.
 *
 * Each [startTail] call opens a dedicated exec channel for the given remote path
 * and pumps its stdout into [onLine] callbacks on a background thread.
 * Active channels are tracked in [activeChannels] keyed by remote path.
 *
 * Usage:
 * ```
 * val svc = RemoteTailService.create("host", 22, "user", "pass")
 * svc.startTail("/var/log/app.log", initialLines = 100) { line -> println(line) }
 * // ...
 * svc.stopTail("/var/log/app.log")
 * svc.close()
 * ```
 */
class RemoteTailService private constructor(
    private val client: SshClient,
    private val session: ClientSession,
) : Closeable {

    private data class TailSession(val channel: ChannelExec, val pipedOut: PipedOutputStream, val pipedIn: PipedInputStream)

    private val activeChannels = ConcurrentHashMap<String, ChannelExec>()
    private val activeSessions = ConcurrentHashMap<String, TailSession>()

    /**
     * Opens an exec channel running `tail -f -n $initialLines $remotePath` and
     * delivers each output line to [onLine] on a background daemon thread.
     *
     * If a tail for [remotePath] is already active it is stopped first.
     */
    fun startTail(remotePath: String, initialLines: Int = 1000, onLine: (String) -> Unit) {
        // Stop any existing tail for this path before opening a new one.
        stopTail(remotePath)

        val command = "tail -f -n $initialLines $remotePath"
        val channel: ChannelExec = session.createExecChannel(command)

        val pipedIn = PipedInputStream()
        val pipedOut = PipedOutputStream(pipedIn)
        channel.out = pipedOut

        channel.open().verify(10_000)
        activeChannels[remotePath] = channel
        activeSessions[remotePath] = TailSession(channel, pipedOut, pipedIn)

        val readerThread = Thread {
            try {
                BufferedReader(InputStreamReader(pipedIn)).use { reader ->
                    reader.forEachLine { line ->
                        // Stop delivering lines if the channel has been removed.
                        if (activeChannels.containsKey(remotePath)) {
                            onLine(line)
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream closed on stopTail/stopAll/close — expected, swallow silently.
            }
        }
        readerThread.isDaemon = true
        readerThread.name = "RemoteTail[$remotePath]"
        readerThread.start()
    }

    /**
     * Stops the tail session for [remotePath] and closes its exec channel.
     * Does nothing if no tail is active for that path.
     */
    fun stopTail(remotePath: String) {
        val channel = activeChannels.remove(remotePath) ?: return
        val session = activeSessions.remove(remotePath)
        closeChannel(channel)
        session?.let {
            runCatching { it.pipedOut.close() }
            runCatching { it.pipedIn.close() }
        }
    }

    /**
     * Returns `true` if a tail session is currently active for [remotePath].
     */
    fun isActive(remotePath: String): Boolean = activeChannels.containsKey(remotePath)

    /**
     * Stops all active tail sessions.
     */
    fun stopAll() {
        val paths = activeChannels.keys.toList()
        paths.forEach { stopTail(it) }
    }

    /**
     * Stops all tails, closes the SSH session and shuts down the client.
     */
    override fun close() {
        stopAll()
        runCatching { session.close(false).await(5_000) }
        runCatching { client.stop() }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun closeChannel(channel: ChannelExec) {
        runCatching { channel.close(false).await(3_000) }
    }

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    companion object {
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val AUTH_TIMEOUT_MS = 8_000L

        /**
         * Creates a [RemoteTailService] connected to the given SSH server using
         * password authentication.
         */
        fun create(
            host: String,
            port: Int,
            username: String,
            password: String,
        ): RemoteTailService {
            val client = SshClient.setUpDefaultClient()
            client.start()

            val session: ClientSession = client
                .connect(username, host, port)
                .verify(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .session

            session.addPasswordIdentity(password)
            session.auth().verify(AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS)

            return RemoteTailService(client, session)
        }
    }
}
