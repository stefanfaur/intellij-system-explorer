package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.util.GlobFilter

class GlobFilterTest {

    @Test
    fun `empty pattern matches everything`() {
        val filter = GlobFilter("")
        assertTrue(filter.matches("anything.txt"))
        assertTrue(filter.matches("file.kt"))
    }

    @Test
    fun `star matches any filename`() {
        val filter = GlobFilter("*")
        assertTrue(filter.matches("anything.txt"))
        assertTrue(filter.matches("no-extension"))
        assertTrue(filter.matches(".dotfile"))
    }

    @Test
    fun `star-dot-ext matches files with that extension`() {
        val filter = GlobFilter("*.kt")
        assertTrue(filter.matches("Main.kt"))
        assertTrue(filter.matches("test.kt"))
        assertFalse(filter.matches("Main.java"))
        assertFalse(filter.matches("kt"))
        assertFalse(filter.matches("file.kts"))
    }

    @Test
    fun `prefix-star matches files starting with prefix`() {
        val filter = GlobFilter("test*")
        assertTrue(filter.matches("test.kt"))
        assertTrue(filter.matches("testFile.java"))
        assertTrue(filter.matches("test"))
        assertFalse(filter.matches("mytest.kt"))
    }

    @Test
    fun `question mark matches single character`() {
        val filter = GlobFilter("file?.txt")
        assertTrue(filter.matches("file1.txt"))
        assertTrue(filter.matches("fileA.txt"))
        assertFalse(filter.matches("file10.txt"))
        assertFalse(filter.matches("file.txt"))
    }

    @Test
    fun `multiple patterns separated by semicolon`() {
        val filter = GlobFilter("*.kt;*.java")
        assertTrue(filter.matches("Main.kt"))
        assertTrue(filter.matches("Main.java"))
        assertFalse(filter.matches("Main.py"))
    }

    @Test
    fun `pattern is case-insensitive`() {
        val filter = GlobFilter("*.TXT")
        assertTrue(filter.matches("readme.txt"))
        assertTrue(filter.matches("README.TXT"))
        assertTrue(filter.matches("File.Txt"))
    }

    @Test
    fun `whitespace in pattern is trimmed`() {
        val filter = GlobFilter("  *.kt  ;  *.java  ")
        assertTrue(filter.matches("Main.kt"))
        assertTrue(filter.matches("Main.java"))
    }

    @Test
    fun `isActive returns false for empty pattern`() {
        assertFalse(GlobFilter("").isActive)
        assertFalse(GlobFilter("   ").isActive)
    }

    @Test
    fun `isActive returns true for non-empty pattern`() {
        assertTrue(GlobFilter("*.kt").isActive)
    }
}
