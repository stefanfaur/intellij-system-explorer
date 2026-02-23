package ro.faur.explorer.remote.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.TextFieldWithAutoCompletionListProvider
import ro.faur.explorer.remote.RemotePathUtils
import ro.faur.explorer.remote.SftpFileOperations

/**
 * Completion provider that fetches directory listings from the remote host
 * to offer path suggestions as the user types in the path bar.
 *
 * When the user presses TAB or types a `/`, the provider lists the contents
 * of the directory corresponding to the current partial path and returns
 * matching entries as completions.
 */
class RemotePathCompletionProvider(
    private val getFileOps: () -> SftpFileOperations?,
) : TextFieldWithAutoCompletionListProvider<String>(emptyList()) {

    override fun getLookupString(item: String): String = item

    override fun getItems(prefix: String?, cached: Boolean, parameters: com.intellij.codeInsight.completion.CompletionParameters?): Collection<String> {
        val ops = getFileOps() ?: return emptyList()
        val text = prefix ?: return emptyList()

        // Determine which directory to list and what prefix to filter by
        val parentDir: String
        val namePrefix: String
        if (text.endsWith("/")) {
            parentDir = text.trimEnd('/')
            namePrefix = ""
        } else {
            parentDir = RemotePathUtils.parentPath(text)
            namePrefix = RemotePathUtils.fileName(text).lowercase()
        }

        val dirToList = if (parentDir.isEmpty()) "/" else parentDir

        return try {
            val entries = ops.listDirectory(dirToList, includeHidden = true)
            entries
                .filter { it.name.lowercase().startsWith(namePrefix) }
                .map {
                    val fullPath = RemotePathUtils.join(dirToList, it.name)
                    if (it.isDirectory) "$fullPath/" else fullPath
                }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * A path bar text field with remote directory auto-completion.
 *
 * Replaces the plain [javax.swing.JTextField] in [RemoteTreePanel] to provide
 * TAB-triggered completion of remote paths.
 */
class RemotePathField(
    project: Project,
    getFileOps: () -> SftpFileOperations?,
) : TextFieldWithAutoCompletion<String>(
    project,
    RemotePathCompletionProvider(getFileOps),
    true,
    ""
)
