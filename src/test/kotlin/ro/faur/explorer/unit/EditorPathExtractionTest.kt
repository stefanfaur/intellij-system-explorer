package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EditorPathExtractionTest {

    @Test
    fun `extractPathToken returns token at offset in middle of word`() {
        val text = "navigateTo(\"/home/user/project\")"
        val offset = text.indexOf("project")
        assertEquals("/home/user/project", extractPathToken(text, offset))
    }

    @Test
    fun `extractPathToken returns token at offset for a simple filename`() {
        val text = "  val file = ExplorerPanel.kt"
        val offset = text.indexOf("ExplorerPanel")
        assertEquals("ExplorerPanel.kt", extractPathToken(text, offset))
    }

    @Test
    fun `extractPathToken returns empty for whitespace position`() {
        val text = "  val file = Foo.kt"
        assertEquals("", extractPathToken(text, 0))
    }

    @Test
    fun `extractPathToken handles offset at end of string`() {
        val text = "src/main/Foo.kt"
        assertEquals("src/main/Foo.kt", extractPathToken(text, text.length - 1))
    }

    private fun extractPathToken(text: CharSequence, offset: Int): String {
        if (offset < 0 || offset >= text.length) return ""
        val pathChars = Regex("[a-zA-Z0-9/._\\-~\$]")
        if (!pathChars.matches(text[offset].toString())) return ""
        var start = offset
        var end = offset
        while (start > 0 && pathChars.matches(text[start - 1].toString())) start--
        while (end < text.length - 1 && pathChars.matches(text[end + 1].toString())) end++
        return text.substring(start, end + 1)
    }
}
