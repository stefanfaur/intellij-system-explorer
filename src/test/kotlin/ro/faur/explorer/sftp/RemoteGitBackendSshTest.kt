package ro.faur.explorer.sftp

import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.channel.ChannelSession
import org.apache.sshd.server.command.Command
import org.apache.sshd.server.command.CommandFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import ro.faur.explorer.gitpanel.RemoteGitBackend
import ro.faur.explorer.remote.SftpConnectionManager
import ro.faur.explorer.remote.git.RemoteGitCommandExecutor
import ro.faur.explorer.remote.security.HostKeyVerifier
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Integration tests for [RemoteGitBackend] over a real embedded SSH server.
 *
 * These tests reproduce the bug where git repos show up in the Git Panel
 * dropdown but no commits are visible. Two root-cause scenarios are covered:
 *
 *  1. Happy path — git is available on the remote server and a repo with
 *     commits exists. [RemoteGitBackend.getLog] must return all commits.
 *
 *  2. Silent-failure path — git is not available on the remote server (exit 127).
 *     The bug: the current code silently returns an empty list with no user
 *     feedback, so the user sees "no commits" without knowing why.
 *     After the fix, a LOG.warn is emitted and a user notification is shown.
 *
 * Run with: ./gradlew test -PincludeSftpTests=true --tests "ro.faur.explorer.sftp.*"
 *
 * NOTE: Requires `git` to be installed on the machine running the tests (for
 * the happy-path cases). The embedded SSH server delegates exec commands to the
 * local shell via ProcessBuilder, so the system PATH applies.
 */
class RemoteGitBackendSshTest : EmbeddedSshTestBase() {

    private lateinit var repoPath: String
    private lateinit var session: ClientSession
    private lateinit var sshClient: SshClient
    private lateinit var tempKnownHosts: Path

    @BeforeEach
    fun setupGitRepoAndSshSession() {
        repoPath = serverRoot.resolve("testrepo").toAbsolutePath().toString()
        createRepoWithCommits(repoPath, commitCount = 3)

        // Connect using a direct SshClient with auto-accept so no IntelliJ
        // ApplicationManager / dialog is needed in this test tier.
        tempKnownHosts = Files.createTempFile("remote-git-test-known-hosts", "")
        sshClient = SshClient.setUpDefaultClient()
        sshClient.serverKeyVerifier =
            HostKeyVerifier(tempKnownHosts, autoAcceptUnknown = true).asServerKeyVerifier()
        sshClient.start()

        session = sshClient.connect(TEST_USER, "localhost", serverPort)
            .verify(10_000L)
            .session
        session.addPasswordIdentity(TEST_PASSWORD)
        session.auth().verify(8_000L)
    }

    @AfterEach
    fun teardownSsh() {
        runCatching { session.close() }
        runCatching { sshClient.stop() }
        Files.deleteIfExists(tempKnownHosts)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a [RemoteGitBackend] wired to [session] via a mocked
     * [SftpConnectionManager]. Only [SftpConnectionManager.getSession] is
     * stubbed — nothing else in the connection lifecycle is exercised.
     */
    private fun makeBackend(path: String = repoPath): RemoteGitBackend {
        val connManager = mock<SftpConnectionManager>()
        whenever(connManager.getSession("test")).thenReturn(session)
        val executor = RemoteGitCommandExecutor(connManager, "test")
        return RemoteGitBackend("test", path, executor)
    }

    private fun createRepoWithCommits(path: String, commitCount: Int) {
        val dir = File(path).also { it.mkdirs() }
        fun git(vararg args: String) =
            ProcessBuilder("git", *args).directory(dir).start().waitFor()

        git("init")
        git("config", "user.email", "test@example.com")
        git("config", "user.name", "Test User")

        repeat(commitCount) { i ->
            File(dir, "file${i + 1}.txt").writeText("content ${i + 1}")
            git("add", ".")
            git("commit", "-m", "commit ${i + 1}")
        }
    }

    // ── Happy-path tests ──────────────────────────────────────────────────────

    @Test
    fun `getLog returns all commits in reverse chronological order`() {
        val log = makeBackend().getLog(100)

        assertEquals(3, log.size, "Expected 3 commits but got: ${log.map { it.subject }}")
        assertEquals("commit 3", log[0].subject, "Most-recent commit should be first")
        assertEquals("commit 2", log[1].subject)
        assertEquals("commit 1", log[2].subject, "Oldest commit should be last")
    }

    @Test
    fun `getLog entries have valid 40-char hashes`() {
        val log = makeBackend().getLog(100)
        assertTrue(log.isNotEmpty())
        log.forEach { entry ->
            assertEquals(40, entry.hash.length, "Hash '${entry.hash}' should be 40 chars")
        }
    }

    @Test
    fun `getLog entries have non-blank author and subject`() {
        val log = makeBackend().getLog(100)
        assertTrue(log.isNotEmpty())
        log.forEach { entry ->
            assertTrue(entry.authorName.isNotBlank(), "Author name should not be blank")
            assertTrue(entry.subject.isNotBlank(), "Subject should not be blank")
        }
    }

    @Test
    fun `getCurrentBranch returns a non-blank branch name`() {
        val branch = makeBackend().getCurrentBranch()
        assertNotNull(branch)
        assertTrue(branch!!.isNotBlank())
    }

    @Test
    fun `getWorkingTreeStatus is empty for a clean committed repo`() {
        val status = makeBackend().getWorkingTreeStatus()
        assertTrue(status.isEmpty(), "Clean repo should have no uncommitted changes: $status")
    }

    @Test
    fun `getCommitFiles returns files changed in the most-recent commit`() {
        val backend = makeBackend()
        val log = backend.getLog(1)
        assertEquals(1, log.size)

        val files = backend.getCommitFiles(log[0].hash)
        assertFalse(files.isEmpty(), "Commit should reference at least one changed file")
    }

    @Test
    fun `getCommitInfo returns metadata for the most-recent commit`() {
        val backend = makeBackend()
        val log = backend.getLog(1)
        assertEquals(1, log.size)

        val info = backend.getCommitInfo(log[0].hash)
        assertNotNull(info)
        assertEquals("commit 3", info!!.subject)
        assertEquals("Test User", info.author)
    }

    // ── Silent-failure tests (the bug) ────────────────────────────────────────

    /**
     * Reproduces the core bug: when `git` is not installed on the remote
     * server, every SSH command returns exit 127 with stderr "command not found".
     *
     * BUG: [RemoteGitBackend.getLog] silently returns an empty list — identical
     * to the output of a legitimate empty repository — so the user sees no
     * commits and has no way to tell whether the repo is truly empty or whether
     * git is broken.
     *
     * EXPECTED after fix: a LOG.warn is emitted with the stderr output so the
     * problem is visible in the IDE's diagnostic log.
     */
    @Test
    fun `getLog silently returns empty list when git is not found on remote server`() {
        sshServer.commandFactory = CommandFactory { _, _ ->
            FailCommand(exitCode = 127, stderr = "sh: git: command not found")
        }

        val log = makeBackend().getLog(100)

        // The list is empty — same as a new repo with zero commits.
        // There is currently NO way for the caller to distinguish these two cases.
        assertTrue(log.isEmpty(), "getLog should return empty list when git is unavailable")
    }

    @Test
    fun `getCurrentBranch returns null when git is not found on remote server`() {
        sshServer.commandFactory = CommandFactory { _, _ ->
            FailCommand(exitCode = 127, stderr = "sh: git: command not found")
        }

        val branch = makeBackend().getCurrentBranch()

        // null branch is the signal used by GitPanelComponent.reloadData()
        // to detect total git failure and fire a user notification (after fix).
        assertNull(branch, "getCurrentBranch should return null when git fails")
    }

    @Test
    fun `getLog returns empty when the repo path does not exist on the remote server`() {
        val nonExistentPath = serverRoot.resolve("does-not-exist").toAbsolutePath().toString()

        val log = makeBackend(nonExistentPath).getLog(100)

        assertTrue(log.isEmpty(), "getLog for a non-existent path should return empty")
    }

    /**
     * Regression test for the git 2.35.2+ "dubious ownership" failure (exit 128).
     *
     * This occurs when the repo is owned by a different OS user than the SSH user
     * running git — a common production setup (e.g. repo owned by "tomcat", SSH
     * user is "deploy"). Git refuses to operate without an explicit safe.directory
     * exception.
     *
     * Fix: [RemoteGitCommandExecutor] now passes `-c safe.directory=<repoPath>` on
     * every invocation, which grants the required permission per-command without
     * modifying any persistent global git config.
     *
     * This test verifies the fix by having the server check that the command it
     * receives actually contains the safe.directory flag.
     */
    @Test
    fun `git commands include safe-directory flag to bypass dubious ownership check`() {
        val receivedCommands = mutableListOf<String>()

        // Intercept commands and delegate to the real shell, but record what arrived.
        sshServer.commandFactory = CommandFactory { _, command ->
            receivedCommands += command
            // Still delegate to ProcessBuilder so the command actually runs.
            object : Command {
                private lateinit var input: java.io.InputStream
                private lateinit var output: OutputStream
                private lateinit var error: OutputStream
                private lateinit var exitCallback: ExitCallback

                override fun setInputStream(i: java.io.InputStream) { input = i }
                override fun setOutputStream(o: OutputStream) { output = o }
                override fun setErrorStream(e: OutputStream) { error = e }
                override fun setExitCallback(c: ExitCallback) { exitCallback = c }

                override fun start(channel: ChannelSession, env: Environment) {
                    Thread {
                        try {
                            val proc = ProcessBuilder("sh", "-c", command)
                                .directory(serverRoot.toFile())
                                .redirectErrorStream(false)
                                .start()
                            proc.inputStream.copyTo(output)
                            proc.errorStream.copyTo(error)
                            val exit = proc.waitFor()
                            output.flush(); error.flush()
                            exitCallback.onExit(exit)
                        } catch (e: Exception) {
                            error.write(e.message?.toByteArray() ?: byteArrayOf())
                            error.flush()
                            exitCallback.onExit(1)
                        }
                    }.apply { isDaemon = true }.start()
                }

                override fun destroy(channel: ChannelSession) {}
            }
        }

        val backend = makeBackend()
        backend.getLog(10)

        assertTrue(
            receivedCommands.any { it.contains("-c 'safe.directory=") },
            "Expected git command to contain -c 'safe.directory=...' but got:\n${receivedCommands.joinToString("\n")}"
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A minimal SSH [Command] that immediately exits with [exitCode] and writes to stderr. */
    private class FailCommand(
        private val exitCode: Int,
        private val stderr: String,
    ) : Command {
        private lateinit var err: OutputStream
        private lateinit var callback: ExitCallback

        override fun setInputStream(i: InputStream) {}
        override fun setOutputStream(o: OutputStream) {}
        override fun setErrorStream(e: OutputStream) { err = e }
        override fun setExitCallback(c: ExitCallback) { callback = c }

        override fun start(channel: ChannelSession, env: Environment) {
            Thread {
                err.write("$stderr\n".toByteArray())
                err.flush()
                callback.onExit(exitCode)
            }.apply { isDaemon = true }.start()
        }

        override fun destroy(channel: ChannelSession) {}
    }
}
