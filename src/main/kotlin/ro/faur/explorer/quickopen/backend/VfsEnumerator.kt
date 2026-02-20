package ro.faur.explorer.quickopen.backend

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Enumerates files using standard Java I/O (File.walkTopDown).
 * IntelliJ's ProjectFileIndex is not used here because Quick Open may navigate
 * to external paths outside the open project.
 * For project-internal paths, this is still fast enough for our 50k cap.
 */
class VfsEnumerator : EnumeratorBackend {
    override val name = "VfsEnumerator"
    override fun isAvailable() = true

    override fun enumerate(root: String, maxResults: Int): Flow<String> = flow {
        val rootFile = File(root)
        if (!rootFile.exists() || !rootFile.isDirectory) return@flow

        var count = 0
        rootFile.walkTopDown()
            .onFail { _, _ -> /* skip inaccessible dirs */ }
            .forEach { file ->
                if (count >= maxResults) return@flow
                emit(file.absolutePath)
                count++
            }
    }
}
