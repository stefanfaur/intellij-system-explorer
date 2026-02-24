package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests the pure segmentation logic independently of IntelliJ UI classes.
 * The actual renderer calls this logic; here we verify the algorithm.
 */
class SnippetHighlightSegmenterTest {

    data class Segment(val text: String, val isMatch: Boolean)

    private fun segment(text: String, ranges: List<IntRange>): List<Segment> {
        val result = mutableListOf<Segment>()
        var pos = 0
        for (range in ranges.sortedBy { it.first }) {
            val start = range.first.coerceIn(0, text.length)
            val end = (range.last + 1).coerceIn(0, text.length)
            if (start > pos) result.add(Segment(text.substring(pos, start), false))
            if (start < end) result.add(Segment(text.substring(start, end), true))
            pos = end
            if (pos >= text.length) break
        }
        if (pos < text.length) result.add(Segment(text.substring(pos), false))
        return result
    }

    @Test
    fun `no ranges returns single non-match segment`() {
        val segs = segment("hello world", emptyList())
        assertEquals(listOf(Segment("hello world", false)), segs)
    }

    @Test
    fun `single match at start`() {
        val segs = segment("hello world", listOf(0 until 5))
        assertEquals(listOf(
            Segment("hello", true),
            Segment(" world", false)
        ), segs)
    }

    @Test
    fun `single match in middle`() {
        val segs = segment("say hello there", listOf(4 until 9))
        assertEquals(listOf(
            Segment("say ", false),
            Segment("hello", true),
            Segment(" there", false)
        ), segs)
    }

    @Test
    fun `multiple non-adjacent matches`() {
        val segs = segment("aXbXc", listOf(1 until 2, 3 until 4))
        assertEquals(listOf(
            Segment("a", false),
            Segment("X", true),
            Segment("b", false),
            Segment("X", true),
            Segment("c", false)
        ), segs)
    }

    @Test
    fun `range clamped when exceeds text length`() {
        val segs = segment("hi", listOf(0 until 100))
        assertEquals(listOf(Segment("hi", true)), segs)
    }
}
