package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.model.FileComparator
import ro.faur.explorer.model.FileEntry

class FileComparatorTest {

    // FileEntry is a simple data class: name: String, isDirectory: Boolean
    // The comparator must NOT depend on VirtualFile — it works on our own model.

    @Test
    fun `folders sort before files when foldersFirst is true`() {
        val entries = listOf(
            FileEntry("readme.md", isDirectory = false),
            FileEntry("src", isDirectory = true),
            FileEntry("build.gradle", isDirectory = false),
            FileEntry("docs", isDirectory = true),
        )

        val sorted = entries.sortedWith(FileComparator(foldersFirst = true))

        // Folders first, then files
        assertTrue(sorted[0].isDirectory)
        assertTrue(sorted[1].isDirectory)
        assertFalse(sorted[2].isDirectory)
        assertFalse(sorted[3].isDirectory)
    }

    @Test
    fun `folders and files intermixed when foldersFirst is false`() {
        val entries = listOf(
            FileEntry("readme.md", isDirectory = false),
            FileEntry("src", isDirectory = true),
            FileEntry("build.gradle", isDirectory = false),
            FileEntry("docs", isDirectory = true),
        )

        val sorted = entries.sortedWith(FileComparator(foldersFirst = false))

        // Purely alphabetical: build.gradle, docs, readme.md, src
        assertEquals("build.gradle", sorted[0].name)
        assertEquals("docs", sorted[1].name)
        assertEquals("readme.md", sorted[2].name)
        assertEquals("src", sorted[3].name)
    }

    @Test
    fun `alphabetical sort is case-insensitive`() {
        val entries = listOf(
            FileEntry("Charlie", isDirectory = true),
            FileEntry("alpha", isDirectory = true),
            FileEntry("Beta", isDirectory = true),
        )

        val sorted = entries.sortedWith(FileComparator(foldersFirst = true))

        assertEquals("alpha", sorted[0].name)
        assertEquals("Beta", sorted[1].name)
        assertEquals("Charlie", sorted[2].name)
    }

    @Test
    fun `within folders group sorts alphabetically case-insensitive`() {
        val entries = listOf(
            FileEntry("Zebra", isDirectory = true),
            FileEntry("alpha", isDirectory = true),
            FileEntry("middle", isDirectory = true),
        )

        val sorted = entries.sortedWith(FileComparator(foldersFirst = true))

        assertEquals("alpha", sorted[0].name)
        assertEquals("middle", sorted[1].name)
        assertEquals("Zebra", sorted[2].name)
    }

    @Test
    fun `within files group sorts alphabetically case-insensitive`() {
        val entries = listOf(
            FileEntry("Zapp.txt", isDirectory = false),
            FileEntry("alpha.txt", isDirectory = false),
            FileEntry("Middle.txt", isDirectory = false),
        )

        val sorted = entries.sortedWith(FileComparator(foldersFirst = true))

        assertEquals("alpha.txt", sorted[0].name)
        assertEquals("Middle.txt", sorted[1].name)
        assertEquals("Zapp.txt", sorted[2].name)
    }

    @Test
    fun `empty list sorts without error`() {
        val sorted = emptyList<FileEntry>().sortedWith(FileComparator(foldersFirst = true))
        assertTrue(sorted.isEmpty())
    }

    @Test
    fun `single element list is unchanged`() {
        val entries = listOf(FileEntry("only.txt", isDirectory = false))
        val sorted = entries.sortedWith(FileComparator(foldersFirst = true))
        assertEquals(1, sorted.size)
        assertEquals("only.txt", sorted[0].name)
    }

    @Test
    fun `dotfiles sort normally among their group`() {
        val entries = listOf(
            FileEntry("readme.md", isDirectory = false),
            FileEntry(".gitignore", isDirectory = false),
            FileEntry(".hidden", isDirectory = true),
            FileEntry("src", isDirectory = true),
        )

        val sorted = entries.sortedWith(FileComparator(foldersFirst = true))

        // Folders: .hidden, src — Files: .gitignore, readme.md
        assertEquals(".hidden", sorted[0].name)
        assertEquals("src", sorted[1].name)
        assertEquals(".gitignore", sorted[2].name)
        assertEquals("readme.md", sorted[3].name)
    }
}
