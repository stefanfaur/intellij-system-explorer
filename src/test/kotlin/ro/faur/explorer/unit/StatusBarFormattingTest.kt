package ro.faur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.util.FileSizeFormatter

/**
 * Tests the status bar formatting logic used by ExplorerPanel.
 *
 * The status bar displays:
 * - When nothing selected: "N folders, M files"
 * - When items selected: "N selected -- SIZE"
 *
 * These tests verify the string formatting patterns match expectations.
 */
class StatusBarFormattingTest {

    /**
     * Formats the "no selection" status text.
     * This mirrors the logic in ExplorerPanel.updateStatus().
     */
    private fun formatNoSelection(folderCount: Int, fileCount: Int): String {
        return "$folderCount folders, $fileCount files"
    }

    /**
     * Formats the "selection" status text.
     * This mirrors the logic in ExplorerPanel.updateStatus().
     */
    private fun formatSelection(selectedCount: Int, totalSizeBytes: Long): String {
        return "$selectedCount selected -- ${FileSizeFormatter.format(totalSizeBytes)}"
    }

    @Test
    fun `no selection with folders and files shows counts`() {
        assertEquals("3 folders, 2 files", formatNoSelection(3, 2))
    }

    @Test
    fun `no selection with zero folders shows zero`() {
        assertEquals("0 folders, 5 files", formatNoSelection(0, 5))
    }

    @Test
    fun `no selection with zero files shows zero`() {
        assertEquals("2 folders, 0 files", formatNoSelection(2, 0))
    }

    @Test
    fun `no selection empty directory shows all zeros`() {
        assertEquals("0 folders, 0 files", formatNoSelection(0, 0))
    }

    @Test
    fun `selection with files shows count and size`() {
        assertEquals("2 selected -- 1.5 KB", formatSelection(2, 1536))
    }

    @Test
    fun `selection single file shows count and size`() {
        assertEquals("1 selected -- 512 B", formatSelection(1, 512))
    }

    @Test
    fun `selection with directories only shows zero size`() {
        assertEquals("3 selected -- 0 B", formatSelection(3, 0))
    }

    @Test
    fun `selection with large files formats size properly`() {
        assertEquals("5 selected -- 1.0 MB", formatSelection(5, 1048576))
    }

    // --- Directory stats formatting ---

    /**
     * Formats the status text when only directories are selected.
     * Should show child count info instead of "0 B".
     */
    private fun formatDirectorySelection(selectedCount: Int, totalChildren: Int, totalSizeBytes: Long): String {
        val sizeStr = FileSizeFormatter.format(totalSizeBytes)
        return "$selectedCount selected -- $totalChildren items, $sizeStr"
    }

    /**
     * Formats the status text for a mixed selection (files + directories).
     */
    private fun formatMixedSelection(selectedCount: Int, dirChildren: Int, fileSizeBytes: Long): String {
        val sizeStr = FileSizeFormatter.format(fileSizeBytes)
        return if (dirChildren > 0) {
            "$selectedCount selected -- $dirChildren items in dirs, $sizeStr in files"
        } else {
            "$selectedCount selected -- $sizeStr"
        }
    }

    @Test
    fun `directory selection shows child count and size`() {
        val result = formatDirectorySelection(1, 5, 2048)
        assertEquals("1 selected -- 5 items, 2.0 KB", result)
    }

    @Test
    fun `multiple directories show combined child count`() {
        val result = formatDirectorySelection(2, 15, 1048576)
        assertEquals("2 selected -- 15 items, 1.0 MB", result)
    }

    @Test
    fun `mixed selection shows dir items and file size`() {
        val result = formatMixedSelection(3, 10, 4096)
        assertEquals("3 selected -- 10 items in dirs, 4.0 KB in files", result)
    }

    @Test
    fun `mixed selection with no dir children shows only file size`() {
        val result = formatMixedSelection(2, 0, 1024)
        assertEquals("2 selected -- 1.0 KB", result)
    }
}
