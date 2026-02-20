package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PopPathSegmentTest {

    @Test
    fun `popPathSegment removes last segment from slash-delimited query`() {
        assertEquals("src/main", popPathSegment("src/main/kotlin"))
    }

    @Test
    fun `popPathSegment removes trailing slash before popping`() {
        assertEquals("src/main", popPathSegment("src/main/kotlin/"))
    }

    @Test
    fun `popPathSegment returns empty string for single segment`() {
        assertEquals("", popPathSegment("src"))
    }

    @Test
    fun `popPathSegment returns empty string for empty input`() {
        assertEquals("", popPathSegment(""))
    }

    @Test
    fun `popPathSegment handles absolute path`() {
        assertEquals("/home/user", popPathSegment("/home/user/projects"))
    }

    @Test
    fun `popPathSegment returns slash for root-level path`() {
        assertEquals("/", popPathSegment("/home"))
    }

    private fun popPathSegment(query: String): String {
        val trimmed = query.trimEnd('/')
        if (trimmed.isEmpty()) return ""
        val lastSlash = trimmed.lastIndexOf('/')
        return when {
            lastSlash < 0 -> ""
            lastSlash == 0 -> "/"
            else -> trimmed.substring(0, lastSlash)
        }
    }
}
