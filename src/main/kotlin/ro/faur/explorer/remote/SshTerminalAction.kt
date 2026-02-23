package ro.faur.explorer.remote

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.terminal.TerminalToolWindowManager

/**
 * Opens an SSH terminal session at the specified remote directory.
 *
 * Uses IntelliJ's Terminal plugin to create a new terminal tab running
 * an SSH command to the given host.
 */
object SshTerminalAction {

    /**
     * Opens an SSH terminal at [remotePath] on the host described by [profile].
     *
     * @param project The current IntelliJ project (needed for the terminal tool window).
     * @param profile The connection profile with host, port, and username.
     * @param remotePath The remote directory to `cd` into after connecting.
     */
    fun openTerminal(
        project: Project,
        profile: ConnectionProfile,
        remotePath: String,
    ) {
        val sshCommand = buildSshCommand(profile, remotePath)
        val terminalManager = TerminalToolWindowManager.getInstance(project)
        terminalManager.createLocalShellWidget(
            null,
            "SSH: ${profile.name}",
            true,
            true,
        ).executeCommand(sshCommand)
    }

    private fun buildSshCommand(profile: ConnectionProfile, remotePath: String): String {
        val portFlag = if (profile.port != 22) " -p ${profile.port}" else ""
        val keyFlag = if (profile.authMethod == ConnectionProfile.AuthMethod.KEY_FILE && profile.keyFilePath != null) {
            " -i ${profile.keyFilePath}"
        } else ""
        val escapedPath = remotePath.replace("'", "'\\''")
        return "ssh$portFlag$keyFlag ${profile.username}@${profile.host} -t 'cd '\\''$escapedPath'\\'' && exec \$SHELL -l'"
    }
}
