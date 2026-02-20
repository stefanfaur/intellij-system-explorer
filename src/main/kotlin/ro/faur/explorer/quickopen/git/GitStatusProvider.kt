package ro.faur.explorer.quickopen.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * Provides git status characters for file paths.
 * M = modified, ? = untracked, ✓ = clean, "" = not in VCS
 */
object GitStatusProvider {
    fun getStatus(project: Project, path: String): String {
        return try {
            val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return ""
            // Only report status for files under VCS
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            if (vcsManager.getVcsFor(vf) == null) return ""
            val clm = ChangeListManager.getInstance(project)
            when (clm.getStatus(vf)) {
                FileStatus.MODIFIED -> "M"
                FileStatus.UNKNOWN -> "?"
                FileStatus.NOT_CHANGED -> "✓"
                else -> ""
            }
        } catch (_: Exception) { "" }
    }

    fun getCurrentBranch(project: Project): String? {
        return try {
            // Use reflection to access git4idea if available, without hard-coding the dependency
            val clazz = Class.forName("git4idea.repo.GitRepositoryManager")
            val getInstance = clazz.getMethod("getInstance", Project::class.java)
            val manager = getInstance.invoke(null, project)
            val getRepositories = clazz.getMethod("getRepositories")
            @Suppress("UNCHECKED_CAST")
            val repos = getRepositories.invoke(manager) as? List<*>
            val repo = repos?.firstOrNull() ?: return null
            val getBranch = repo.javaClass.getMethod("getCurrentBranchName")
            getBranch.invoke(repo) as? String
        } catch (_: Exception) { null }
    }
}
