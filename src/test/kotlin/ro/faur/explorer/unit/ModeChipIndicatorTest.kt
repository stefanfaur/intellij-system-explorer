package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.query.QueryMode
import ro.faur.explorer.quickopen.query.QueryParser

class ModeChipIndicatorTest {

    @Test
    fun `modeChipText returns empty string for UNIFIED mode`() {
        val parsed = QueryParser.parse("hello")
        assertEquals("", modeChipText(parsed.mode))
    }

    @Test
    fun `modeChipText returns d colon for DIRS_ONLY`() {
        val parsed = QueryParser.parse("d: src")
        assertEquals("[d:]", modeChipText(parsed.mode))
    }

    @Test
    fun `modeChipText returns f colon for FILES_ONLY`() {
        val parsed = QueryParser.parse("f: kt")
        assertEquals("[f:]", modeChipText(parsed.mode))
    }

    @Test
    fun `modeChipText returns command indicator for COMMAND mode`() {
        val parsed = QueryParser.parse("> toggle")
        assertEquals("[>]", modeChipText(parsed.mode))
    }

    @Test
    fun `modeChipText returns regex indicator for REGEX mode`() {
        val parsed = QueryParser.parse("~src.*ui")
        assertEquals("[~]", modeChipText(parsed.mode))
    }

    @Test
    fun `modeChipText returns content search indicator for CONTENT_SEARCH`() {
        val parsed = QueryParser.parse("/: fun navigateTo")
        assertEquals("[/:]", modeChipText(parsed.mode))
    }

    private fun modeChipText(mode: QueryMode): String = when (mode) {
        QueryMode.DIRS_ONLY -> "[d:]"
        QueryMode.FILES_ONLY -> "[f:]"
        QueryMode.BOOKMARKS_ONLY -> "[b:]"
        QueryMode.RECENT_ONLY -> "[r:]"
        QueryMode.COMMAND -> "[>]"
        QueryMode.REGEX -> "[~]"
        QueryMode.CONTENT_SEARCH -> "[/:]"
        QueryMode.UNIFIED -> ""
    }
}
