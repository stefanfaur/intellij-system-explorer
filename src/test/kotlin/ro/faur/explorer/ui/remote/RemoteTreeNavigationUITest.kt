package ro.faur.explorer.ui.remote

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * Layer 5 UI tests for remote tree navigation.
 *
 * These tests require a running IDE environment with Xvfb and are
 * disabled until the RemoteUI test harness is implemented.
 */
@Disabled("Requires RemoteUI test harness — placeholder for Layer 5 UI tests")
class RemoteTreeNavigationUITest {

    @Test
    fun `tree shows not connected on startup`() {
        // Verify the tree root node text is "(not connected)" before any connection
    }

    @Test
    fun `double-click directory navigates into it`() {
        // Connect, list root, double-click a directory node, verify path bar updates
    }

    @Test
    fun `backspace navigates to parent`() {
        // Navigate into /home/user, press Backspace, verify path bar shows /home
    }

    @Test
    fun `right-click shows context menu`() {
        // Right-click a file node, verify JPopupMenu is visible with expected items
    }

    @Test
    fun `ctrl-c copies to clipboard`() {
        // Select a file, press Ctrl+C, verify CopyPasteManager contains RemoteFileTransferable
    }
}
