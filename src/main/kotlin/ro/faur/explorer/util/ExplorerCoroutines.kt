package ro.faur.explorer.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineExceptionHandler

private val LOG = logger<ExplorerErrorNotifier>()

fun explorerExceptionHandler(project: Project?, context: String): CoroutineExceptionHandler =
    CoroutineExceptionHandler { _, throwable ->
        LOG.error("[$context] Unhandled coroutine exception", throwable)
        ExplorerErrorNotifier.notify(
            project = project,
            title = "Explorer: $context failed",
            message = throwable.message ?: "An unexpected error occurred.",
            throwable = throwable
        )
    }
