package ro.faur.explorer.sftp

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
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.session.ClientSession
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Phase 2 — Spec: getFileAtRevision correct commit range (Human Verification Items 2, 5)
 *
 * Item 5 — History-mode Show Diff correct commit range:
 *   The plan (02-04-PLAN.md) requires that in history mode, doShowFullDiff()
 *   calls backend.getFileAtRevision("$commitHash^", path) for the left/before
 *   pane, and backend.getFileAtRevision(commitHash, path) for the right/after
 *   pane. This tests that getFileAtRevision correctly fetches file content at
 *   a specific commit, and that the parent-commit syntax (HASH^) works.
 *
 * Item 2 — Show Diff FRAME popup with correct content:
 *   Verifies that getFileAtRevision returns the exact bytes that git stored at
 *   that revision. When these bytes are used to build a SimpleDiffRequest, the
 *   diff viewer shows the correct before/after content.
 *
 * Run with: ./gradlew test -PincludeSftpTests=true --tests "ro.faur.explorer.sftp.Phase2*"
 *
 * Requires `git` installed on the test machine (commands run via embedded SSH
 * server delegating to local ProcessBuilder, same as RemoteGitBackendSshTest).
 */
class Phase2GetFileAtRevisionTest : EmbeddedSshTestBase() {

    private lateinit var repoPath: String
    private lateinit var session: ClientSession
    private lateinit var sshClient: SshClient
    private lateinit var tempKnownHosts: Path

    // Commits created in setUp — recorded for assertions
    private lateinit var commit1Hash: String
    private lateinit var commit2Hash: String

    @BeforeEach
    fun setupRepoAndSsh() {
        repoPath = serverRoot.resolve("difftest").toAbsolutePath().toString()
        val hashes = createRepoWithTwoCommits(repoPath)
        commit1Hash = hashes.first
        commit2Hash = hashes.second

        tempKnownHosts = Files.createTempFile("phase2-known-hosts", "")
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

    // ── getFileAtRevision: exact commit content ───────────────────────────────

    @Test
    fun `getFileAtRevision returns file content at a specific commit`() {
        val backend = makeBackend()

        val bytes = backend.getFileAtRevision(commit2Hash, "tracked.txt")

        assertNotNull(bytes, "getFileAtRevision must return non-null for a file that exists at $commit2Hash")
        val content = bytes!!.toString(Charsets.UTF_8)
        assertTrue(
            content.contains("v2"),
            "getFileAtRevision($commit2Hash, tracked.txt) should return commit-2 content (contains 'v2'). Got: '$content'"
        )
    }

    @Test
    fun `getFileAtRevision at parent commit returns previous file content`() {
        val backend = makeBackend()

        // commit2Hash^ is the parent of commit2, which is commit1
        val bytes = backend.getFileAtRevision("$commit2Hash^", "tracked.txt")

        assertNotNull(bytes, "getFileAtRevision must return non-null for commit2^ (parent = commit1)")
        val content = bytes!!.toString(Charsets.UTF_8)
        assertTrue(
            content.contains("v1"),
            "getFileAtRevision($commit2Hash^, tracked.txt) should return commit-1 content (contains 'v1'). Got: '$content'"
        )
    }

    @Test
    fun `getFileAtRevision at commit1 returns initial file content`() {
        val backend = makeBackend()

        val bytes = backend.getFileAtRevision(commit1Hash, "tracked.txt")

        assertNotNull(bytes)
        val content = bytes!!.toString(Charsets.UTF_8)
        assertTrue(
            content.contains("v1"),
            "getFileAtRevision at first commit should return v1 content. Got: '$content'"
        )
    }

    // ── Diff range: before vs after ───────────────────────────────────────────

    @Test
    fun `commit parent and commit itself return different content for the same file`() {
        val backend = makeBackend()

        val before = backend.getFileAtRevision("$commit2Hash^", "tracked.txt")
        val after  = backend.getFileAtRevision(commit2Hash, "tracked.txt")

        assertNotNull(before, "Content at parent commit must not be null")
        assertNotNull(after, "Content at target commit must not be null")
        assertFalse(
            before.contentEquals(after),
            "Content at COMMIT^ and COMMIT must differ — they are the before/after sides of the diff"
        )
    }

    @Test
    fun `history mode diff range produces correct before and after content`() {
        val backend = makeBackend()

        // Simulate what doShowFullDiff() does in history mode (02-04-PLAN.md):
        //   val beforeBytes = backend.getFileAtRevision("$commitHash^", file.path)
        //   val afterBytes  = backend.getFileAtRevision(commitHash, file.path)
        val beforeBytes = backend.getFileAtRevision("$commit2Hash^", "tracked.txt")
        val afterBytes  = backend.getFileAtRevision(commit2Hash, "tracked.txt")

        assertNotNull(beforeBytes, "Before bytes (parent commit) must not be null")
        assertNotNull(afterBytes, "After bytes (selected commit) must not be null")

        val beforeStr = beforeBytes!!.toString(Charsets.UTF_8)
        val afterStr  = afterBytes!!.toString(Charsets.UTF_8)

        // The before side must have v1 content, after side must have v2 content
        assertTrue(beforeStr.contains("v1"), "Before (COMMIT^) must contain v1. Got: '$beforeStr'")
        assertTrue(afterStr.contains("v2"),  "After  (COMMIT)  must contain v2. Got: '$afterStr'")
    }

    // ── Null-safety: missing file / wrong revision ────────────────────────────

    @Test
    fun `getFileAtRevision returns null for a path that does not exist at the given revision`() {
        val backend = makeBackend()

        // "nonexistent.txt" was never added to the repo
        val bytes = backend.getFileAtRevision(commit1Hash, "nonexistent.txt")

        assertNull(bytes, "getFileAtRevision must return null for a path that never existed at that revision")
    }

    @Test
    fun `getFileAtRevision for added file at parent commit returns null`() {
        val backend = makeBackend()

        // "added-in-commit2.txt" was added in commit2, so it doesn't exist at commit1 (commit2^)
        val bytes = backend.getFileAtRevision("$commit2Hash^", "added-in-commit2.txt")

        assertNull(
            bytes,
            "getFileAtRevision must return null for a file added in commit2 when asked for commit2^ (parent)"
        )
    }

    @Test
    fun `getFileAtRevision for added file at its own commit returns content`() {
        val backend = makeBackend()

        val bytes = backend.getFileAtRevision(commit2Hash, "added-in-commit2.txt")

        assertNotNull(bytes, "getFileAtRevision must return content for 'added-in-commit2.txt' at the commit that added it")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makeBackend(): RemoteGitBackend {
        val connManager = mock<SftpConnectionManager>()
        whenever(connManager.getSession("test")).thenReturn(session)
        val executor = RemoteGitCommandExecutor(connManager, "test")
        return RemoteGitBackend("test", repoPath, executor)
    }

    /**
     * Creates a two-commit git repo:
     *
     *   Commit 1: tracked.txt = "v1\n"
     *   Commit 2: tracked.txt = "v2\n", added-in-commit2.txt = "new file\n"
     *
     * Returns (commit1Hash, commit2Hash).
     */
    private fun createRepoWithTwoCommits(path: String): Pair<String, String> {
        val dir = File(path).also { it.mkdirs() }
        fun git(vararg args: String): String {
            val proc = ProcessBuilder("git", *args)
                .directory(dir)
                .redirectErrorStream(true)
                .start()
            val out = proc.inputStream.readBytes().toString(Charsets.UTF_8).trim()
            proc.waitFor()
            return out
        }

        git("init")
        git("config", "user.email", "test@phase2.test")
        git("config", "user.name", "Phase2 Test")

        // Commit 1
        File(dir, "tracked.txt").writeText("v1\n")
        git("add", "tracked.txt")
        git("commit", "-m", "commit 1: add tracked.txt at v1")
        val hash1 = git("rev-parse", "HEAD")

        // Commit 2
        File(dir, "tracked.txt").writeText("v2\n")
        File(dir, "added-in-commit2.txt").writeText("new file\n")
        git("add", "tracked.txt", "added-in-commit2.txt")
        git("commit", "-m", "commit 2: update tracked.txt to v2, add new file")
        val hash2 = git("rev-parse", "HEAD")

        return Pair(hash1, hash2)
    }
}
