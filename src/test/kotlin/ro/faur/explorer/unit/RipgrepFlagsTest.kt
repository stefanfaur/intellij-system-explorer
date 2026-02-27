package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RipgrepFlagsTest {

    private fun buildFlags(
        searchHidden: Boolean = false,
        followSymlinks: Boolean = false,
        respectIgnore: Boolean = true,
        maxDepth: Int = 0,
        extraFlags: String = ""
    ): List<String> {
        val cmd = mutableListOf("rg", "--files", "--color=never")
        if (searchHidden) cmd.add("--hidden")
        if (followSymlinks) cmd.add("--follow")
        if (!respectIgnore) cmd.add("--no-ignore")
        if (maxDepth > 0) { cmd.add("--max-depth"); cmd.add(maxDepth.toString()) }
        val extra = extraFlags.trim()
        if (extra.isNotBlank()) cmd.addAll(extra.split("\\s+".toRegex()))
        cmd.add("/some/root")
        return cmd
    }

    @Test fun `defaults produce no extra flags`() {
        val flags = buildFlags()
        assertFalse(flags.contains("--hidden"))
        assertFalse(flags.contains("--follow"))
        assertFalse(flags.contains("--no-ignore"))
        assertFalse(flags.contains("--max-depth"))
    }

    @Test fun `hidden flag added when enabled`() {
        assertTrue(buildFlags(searchHidden = true).contains("--hidden"))
    }

    @Test fun `follow flag added when enabled`() {
        assertTrue(buildFlags(followSymlinks = true).contains("--follow"))
    }

    @Test fun `no-ignore added when respectIgnore is false`() {
        assertTrue(buildFlags(respectIgnore = false).contains("--no-ignore"))
    }

    @Test fun `max-depth added with correct value`() {
        val flags = buildFlags(maxDepth = 5)
        val idx = flags.indexOf("--max-depth")
        assertTrue(idx >= 0)
        assertEquals("5", flags[idx + 1])
    }

    @Test fun `extra flags are appended`() {
        val flags = buildFlags(extraFlags = "--glob --type kotlin")
        assertTrue(flags.contains("--glob"))
    }
}
