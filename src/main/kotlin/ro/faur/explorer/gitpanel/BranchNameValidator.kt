package ro.faur.explorer.gitpanel

/**
 * Validates git branch names against the rules enforced by `git check-ref-format`.
 * Pure Kotlin — no IntelliJ platform dependency.
 */
object BranchNameValidator {

    private val INVALID_CHARS = Regex("""[\s~^:?*\[\\]""")

    /**
     * Returns null if [name] is a valid git branch name, or a user-readable error
     * message describing the first violation found.
     */
    fun validate(name: String): String? {
        if (name.isBlank()) return "Branch name cannot be empty"
        if (name.startsWith(".")) return "Branch name cannot start with '.'"
        if (name.endsWith(".")) return "Branch name cannot end with '.'"
        if (name.startsWith("/")) return "Branch name cannot start with '/'"
        if (name.endsWith("/")) return "Branch name cannot end with '/'"
        if (name.contains("//")) return "Branch name cannot contain '//'"
        if (name.contains("..")) return "Branch name cannot contain '..'"
        if (INVALID_CHARS.containsMatchIn(name)) return "Branch name contains an invalid character"
        if (name.contains("@{")) return "Branch name cannot contain '@{'"
        if (name == "@") return "Branch name cannot be '@'"
        if (name.endsWith(".lock")) return "Branch name cannot end with '.lock'"
        return null
    }
}
