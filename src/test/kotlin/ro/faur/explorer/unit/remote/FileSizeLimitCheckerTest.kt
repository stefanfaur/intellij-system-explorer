package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.FileSizeLimitChecker
import ro.faur.explorer.remote.FileSizeLimitChecker.Action

class FileSizeLimitCheckerTest {

    @Test
    fun `small file returns OPEN_NORMALLY`() {
        val checker = FileSizeLimitChecker(maxEditorSizeMb = 10)
        assertEquals(Action.OPEN_NORMALLY, checker.check(5 * 1024 * 1024))
    }

    @Test
    fun `file at exact limit returns OPEN_NORMALLY`() {
        val checker = FileSizeLimitChecker(maxEditorSizeMb = 10)
        assertEquals(Action.OPEN_NORMALLY, checker.check(10L * 1024 * 1024))
    }

    @Test
    fun `file between limit and 100MB returns WARN_LARGE`() {
        val checker = FileSizeLimitChecker(maxEditorSizeMb = 10)
        assertEquals(Action.WARN_LARGE, checker.check(50L * 1024 * 1024))
    }

    @Test
    fun `file over 100MB returns BLOCK_TOO_LARGE`() {
        val checker = FileSizeLimitChecker(maxEditorSizeMb = 10)
        assertEquals(Action.BLOCK_TOO_LARGE, checker.check(150L * 1024 * 1024))
    }

    @Test
    fun `zero byte file returns OPEN_NORMALLY`() {
        val checker = FileSizeLimitChecker(maxEditorSizeMb = 10)
        assertEquals(Action.OPEN_NORMALLY, checker.check(0))
    }

    @Test
    fun `custom max editor size is respected`() {
        val checker = FileSizeLimitChecker(maxEditorSizeMb = 1)
        assertEquals(Action.WARN_LARGE, checker.check(5 * 1024 * 1024))
    }
}
