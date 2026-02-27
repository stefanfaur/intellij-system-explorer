package ro.faur.explorer.gitpanel.exec

data class GitCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val isSuccess: Boolean get() = exitCode == 0
    val stdoutLines: List<String> get() = stdout.lines().filter { it.isNotEmpty() }
}
