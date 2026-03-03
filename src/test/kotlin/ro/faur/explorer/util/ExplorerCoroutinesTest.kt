package ro.faur.explorer.util

import kotlinx.coroutines.CoroutineExceptionHandler
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class ExplorerCoroutinesTest {

    @Test
    fun `explorerExceptionHandler returns a CoroutineExceptionHandler`() {
        val handler = explorerExceptionHandler(project = null, context = "test context")
        assertNotNull(handler[CoroutineExceptionHandler.Key])
    }
}
