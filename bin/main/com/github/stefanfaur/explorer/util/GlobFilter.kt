package com.github.stefanfaur.explorer.util

/**
 * Filters filenames against glob patterns.
 *
 * Supports:
 * - `*` matches any sequence of characters
 * - `?` matches exactly one character
 * - Multiple patterns separated by `;`
 * - Case-insensitive matching
 * - Whitespace trimming around patterns
 * - Empty/blank pattern matches everything
 */
class GlobFilter(pattern: String) {

    private val regexes: List<Regex>

    /**
     * Whether this filter is active (has a non-blank pattern).
     * An inactive filter matches everything.
     */
    val isActive: Boolean

    init {
        val trimmed = pattern.trim()
        isActive = trimmed.isNotEmpty()

        regexes = if (!isActive) {
            emptyList()
        } else {
            trimmed.split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { globToRegex(it) }
        }
    }

    /**
     * Returns true if the given filename matches this filter's pattern(s).
     * An inactive filter (empty/blank pattern) matches everything.
     */
    fun matches(filename: String): Boolean {
        if (!isActive) return true
        return regexes.any { it.matches(filename) }
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder("^")
        for (ch in glob) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.' -> sb.append("\\.")
                '\\' -> sb.append("\\\\")
                '^', '$', '|', '+', '(', ')', '{', '}', '[', ']' -> {
                    sb.append("\\").append(ch)
                }
                else -> sb.append(ch)
            }
        }
        sb.append("$")
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }
}
