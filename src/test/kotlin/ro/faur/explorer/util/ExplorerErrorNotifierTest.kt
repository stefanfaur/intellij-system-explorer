package ro.faur.explorer.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExplorerErrorNotifierTest {

    @Test
    fun `formatMessage includes message and stack trace`() {
        val throwable = RuntimeException("boom")
        val result = ExplorerErrorNotifier.formatDetails("Something failed", throwable)
        assertTrue(result.contains("Something failed"))
        assertTrue(result.contains("RuntimeException"))
        assertTrue(result.contains("boom"))
    }

    @Test
    fun `formatMessage without throwable returns just message`() {
        val result = ExplorerErrorNotifier.formatDetails("Only a message", null)
        assertEquals("Only a message", result)
    }
}
