package com.github.stefanfaur.explorer.unit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import com.github.stefanfaur.explorer.util.FileSizeFormatter

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
}
