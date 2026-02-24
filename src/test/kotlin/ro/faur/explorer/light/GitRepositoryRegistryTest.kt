package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.gitpanel.BackendType
import ro.faur.explorer.gitpanel.CommitFile
import ro.faur.explorer.gitpanel.CommitInfo
import ro.faur.explorer.gitpanel.GitBackend
import ro.faur.explorer.gitpanel.GitBackendId
import ro.faur.explorer.gitpanel.GitRepositoryRegistry
import ro.faur.explorer.gitpanel.exec.GitCommandResult
import ro.faur.explorer.remote.git.GitLogEntry
import java.time.Instant
import java.util.Date

class GitRepositoryRegistryTest : BasePlatformTestCase() {

    private fun makeBackend(type: BackendType, conn: String?, path: String): GitBackend {
        val id = GitBackendId(type, conn, path)
        return object : GitBackend {
            override val id = id
            override val displayName = id.key
            override val repoPath = path
            override fun getCurrentBranch(): String? = null
            override fun getLog(maxCount: Int): List<GitLogEntry> = emptyList()
            override fun getWorkingTreeStatus(): List<CommitFile> = emptyList()
            override fun getCommitFiles(hash: String): List<CommitFile> = emptyList()
            override fun getCommitInfo(hash: String): CommitInfo? = null
            override fun stageFiles(paths: List<String>): GitCommandResult = GitCommandResult(0, "", "")
            override fun commit(message: String): GitCommandResult = GitCommandResult(0, "", "")
            override fun push(): GitCommandResult = GitCommandResult(0, "", "")
            override fun dispose() {}
        }
    }

    fun `test register and getAll`() {
        val registry = GitRepositoryRegistry.getInstance(project)
        val backend = makeBackend(BackendType.LOCAL, null, "/tmp/repo")
        registry.register(backend)
        val all = registry.getAll()
        assertTrue(all.any { it.id.key == "local:/tmp/repo" })
    }

    fun `test unregister removes backend`() {
        val registry = GitRepositoryRegistry.getInstance(project)
        val backend = makeBackend(BackendType.LOCAL, null, "/tmp/repo2")
        registry.register(backend)
        registry.unregister(backend.id)
        assertTrue(registry.getAll().none { it.id.key == "local:/tmp/repo2" })
    }

    fun `test unregisterByConnection removes only matching remotes`() {
        val registry = GitRepositoryRegistry.getInstance(project)
        val r1 = makeBackend(BackendType.REMOTE, "server1", "/opt/a")
        val r2 = makeBackend(BackendType.REMOTE, "server1", "/opt/b")
        val r3 = makeBackend(BackendType.REMOTE, "server2", "/opt/c")
        registry.register(r1)
        registry.register(r2)
        registry.register(r3)

        registry.unregisterByConnection("server1")

        val all = registry.getAll()
        assertFalse(all.any { it.id.connectionName == "server1" })
        assertTrue(all.any { it.id.key == "remote:server2:/opt/c" })
    }

    fun `test listener is called on register`() {
        val registry = GitRepositoryRegistry.getInstance(project)
        var callCount = 0
        val listener: () -> Unit = { callCount += 1 }
        registry.addListener(listener)
        registry.register(makeBackend(BackendType.LOCAL, null, "/tmp/listener-test"))
        assertEquals(1, callCount)
        registry.removeListener(listener)
    }

    fun `test listener is called on unregister`() {
        val registry = GitRepositoryRegistry.getInstance(project)
        val backend = makeBackend(BackendType.LOCAL, null, "/tmp/unregister-listener")
        registry.register(backend)

        var callCount = 0
        val listener: () -> Unit = { callCount += 1 }
        registry.addListener(listener)
        registry.unregister(backend.id)
        assertEquals(1, callCount)
        registry.removeListener(listener)
    }

    fun `test dispose clears listeners and backends`() {
        val registry = GitRepositoryRegistry()
        val backend = makeBackend(BackendType.LOCAL, null, "/tmp/dispose-test")
        registry.register(backend)
        var called = false
        registry.addListener { called = true }
        registry.dispose()
        assertFalse("listener should not fire after dispose", called)
        assertEquals(0, registry.getAll().size)
    }

    override fun tearDown() {
        // Clean up registry state
        val registry = GitRepositoryRegistry.getInstance(project)
        registry.getAll().forEach { registry.unregister(it.id) }
        super.tearDown()
    }
}
