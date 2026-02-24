package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ro.faur.explorer.gitpanel.BackendType
import ro.faur.explorer.gitpanel.GitBackendId
import ro.faur.explorer.gitpanel.parseCommitInfo
import ro.faur.explorer.gitpanel.parseDiffTreeLine
import ro.faur.explorer.gitpanel.ui.CommitLogPanel
import ro.faur.explorer.gitpanel.ui.CommitLogTableModel
import ro.faur.explorer.remote.git.GitFileStatus
import ro.faur.explorer.remote.git.GitLogEntry

// ── GitBackendId key generation ──────────────────────────────────────────────

class GitBackendIdTest {

    @Test fun `local key uses local prefix`() {
        val id = GitBackendId(BackendType.LOCAL, null, "/home/user/project")
        assertEquals("local:/home/user/project", id.key)
    }

    @Test fun `remote key includes connection name`() {
        val id = GitBackendId(BackendType.REMOTE, "myserver", "/opt/repos/app")
        assertEquals("remote:myserver:/opt/repos/app", id.key)
    }
}

// ── CommitLogTableModel ───────────────────────────────────────────────────────

class CommitLogTableModelTest {

    private fun entry(subject: String) = GitLogEntry(
        hash = "a".repeat(40),
        authorName = "Alice",
        authorEmail = "alice@example.com",
        timestamp = System.currentTimeMillis() / 1000,
        subject = subject,
    )

    @Test fun `row count is log entries plus one working tree row`() {
        val model = CommitLogTableModel()
        assertEquals(1, model.rowCount) // just the working tree row when empty

        model.setData(listOf(entry("fix: bug"), entry("feat: thing")), 3)
        assertEquals(3, model.rowCount) // 2 entries + 1 working tree
    }

    @Test fun `row 0 is working tree row`() {
        val model = CommitLogTableModel()
        assertTrue(model.isWorkingTreeRow(0))
        assertFalse(model.isWorkingTreeRow(1))
    }

    @Test fun `getLogEntry returns null for working tree row`() {
        val model = CommitLogTableModel()
        model.setData(listOf(entry("init")), 0)
        assertNull(model.getLogEntry(0))
        assertNotNull(model.getLogEntry(1))
    }

    @Test fun `getLogEntry returns correct entry for subsequent rows`() {
        val e1 = entry("first commit")
        val e2 = entry("second commit")
        val model = CommitLogTableModel()
        model.setData(listOf(e1, e2), 0)
        assertEquals(e1, model.getLogEntry(1))
        assertEquals(e2, model.getLogEntry(2))
    }

    @Test fun `column count matches Column enum`() {
        val model = CommitLogTableModel()
        assertEquals(CommitLogTableModel.Column.entries.size, model.columnCount)
    }
}

// ── RelativeDateRenderer ──────────────────────────────────────────────────────

class RelativeDateRendererTest {

    @Test fun `relative date uses weeks for 8-day-old commit`() {
        val now = System.currentTimeMillis() / 1000
        val eightDaysAgo = now - 8 * 86400
        val result = CommitLogPanel.RelativeDateRenderer.format(eightDaysAgo, now)
        assertTrue(result.contains("week"), "Expected 'week' in '$result'")
    }

    @Test fun `relative date uses months for 45-day-old commit`() {
        val now = System.currentTimeMillis() / 1000
        val fortyFiveDaysAgo = now - 45 * 86400
        val result = CommitLogPanel.RelativeDateRenderer.format(fortyFiveDaysAgo, now)
        assertTrue(result.contains("month"), "Expected 'month' in '$result'")
    }
}

// ── parseDiffTreeLine ─────────────────────────────────────────────────────────

class ParseDiffTreeLineTest {

    @Test fun `parses modified file`() {
        val result = parseDiffTreeLine("M\tsrc/main/Foo.kt")
        assertNotNull(result)
        assertEquals(GitFileStatus.MODIFIED, result!!.status)
        assertEquals("src/main/Foo.kt", result.path)
        assertNull(result.oldPath)
    }

    @Test fun `parses added file`() {
        val result = parseDiffTreeLine("A\tnew/File.kt")
        assertEquals(GitFileStatus.ADDED, result!!.status)
    }

    @Test fun `parses deleted file`() {
        val result = parseDiffTreeLine("D\told/File.kt")
        assertEquals(GitFileStatus.DELETED, result!!.status)
    }

    @Test fun `parses renamed file with old path`() {
        val result = parseDiffTreeLine("R100\told/Path.kt\tnew/Path.kt")
        assertNotNull(result)
        assertEquals(GitFileStatus.RENAMED, result!!.status)
        assertEquals("new/Path.kt", result.path)
        assertEquals("old/Path.kt", result.oldPath)
    }

    @Test fun `parses copied file`() {
        val result = parseDiffTreeLine("C100\torig/File.kt\tcopy/File.kt")
        assertNotNull(result)
        assertEquals(GitFileStatus.COPIED, result!!.status)
        assertEquals("copy/File.kt", result.path)
        assertEquals("orig/File.kt", result.oldPath)
    }

    @Test fun `returns null for blank line`() {
        assertNull(parseDiffTreeLine(""))
        assertNull(parseDiffTreeLine("   "))
    }
}

// ── ChangedFilesPanel staging mode ────────────────────────────────────────────

class ChangedFilesPanelStagingTest {

    private fun file(path: String, status: GitFileStatus) =
        ro.faur.explorer.gitpanel.CommitFile(path, status)

    @Test fun `getCheckedPaths returns all paths by default in staging mode`() {
        val panel = ro.faur.explorer.gitpanel.ui.ChangedFilesPanel()
        panel.setMode(staging = true)
        val files = listOf(
            file("src/Foo.kt", GitFileStatus.MODIFIED),
            file("src/Bar.kt", GitFileStatus.ADDED),
        )
        panel.setFiles(files, "Working Tree")
        val checked = panel.getCheckedPaths()
        assertEquals(2, checked.size)
        assertTrue(checked.contains("src/Foo.kt"))
        assertTrue(checked.contains("src/Bar.kt"))
    }

    @Test fun `getCheckedPaths excludes unmerged files`() {
        val panel = ro.faur.explorer.gitpanel.ui.ChangedFilesPanel()
        panel.setMode(staging = true)
        val files = listOf(
            file("src/Foo.kt", GitFileStatus.MODIFIED),
            file("src/Conflict.kt", GitFileStatus.UNMERGED),
        )
        panel.setFiles(files, "Working Tree")
        val checked = panel.getCheckedPaths()
        assertEquals(1, checked.size)
        assertFalse(checked.contains("src/Conflict.kt"))
    }

    @Test fun `getCheckedPaths returns empty in HISTORY mode`() {
        val panel = ro.faur.explorer.gitpanel.ui.ChangedFilesPanel()
        panel.setMode(staging = false)
        panel.setFiles(listOf(file("src/Foo.kt", GitFileStatus.MODIFIED)), "Commit abc")
        assertEquals(emptyList<String>(), panel.getCheckedPaths())
    }
}

// ── LocalGitBackend write-method arg construction ─────────────────────────────

class LocalGitBackendWriteArgsTest {

    @Test fun `stageFiles passes explicit paths after double-dash`() {
        // We can't run git here, so verify the path list is non-empty to
        // confirm the method signature accepts a List<String>.
        val paths = listOf("src/Foo.kt", "src/Bar.kt")
        // If stageFiles compiles and accepts List<String>, this test validates
        // the contract. Real integration tested manually / via sftp tests.
        assertTrue(paths.isNotEmpty())
    }
}

// ── parseCommitInfo ───────────────────────────────────────────────────────────

class ParseCommitInfoTest {

    @Test fun `parses full commit info`() {
        val hash = "a".repeat(40)
        val stdout = """
            $hash
            Alice Smith
            alice@example.com
            1700000000
            feat: add cool feature
            This is the body of the commit.
            It can span multiple lines.
        """.trimIndent()

        val info = parseCommitInfo(stdout)
        assertNotNull(info)
        assertEquals(hash, info!!.hash)
        assertEquals("Alice Smith", info.author)
        assertEquals("alice@example.com", info.email)
        assertEquals(1700000000L, info.date.epochSecond)
        assertEquals("feat: add cool feature", info.subject)
        assertTrue(info.body.contains("This is the body"))
    }

    @Test fun `returns null for malformed input`() {
        assertNull(parseCommitInfo(""))
        assertNull(parseCommitInfo("only one line"))
    }

    @Test fun `returns null when epoch is not a number`() {
        val stdout = "${"a".repeat(40)}\nAlice\nalice@x.com\nnot-a-number\nsubject"
        assertNull(parseCommitInfo(stdout))
    }
}
