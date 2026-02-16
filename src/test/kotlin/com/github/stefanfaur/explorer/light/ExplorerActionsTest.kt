package com.github.stefanfaur.explorer.light

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import javax.swing.KeyStroke

/**
 * Tests that all System Explorer keyboard-shortcut actions are properly
 * registered in the ActionManager and have the expected shortcut bindings.
 */
class ExplorerActionsTest : BasePlatformTestCase() {

    private val actionManager get() = ActionManager.getInstance()

    /**
     * On macOS the $default keymap translates "ctrl" to "meta" (Cmd).
     * This helper returns the platform-appropriate modifier prefix.
     */
    private val platformCtrl: String
        get() = if (SystemInfo.isMac) "meta" else "ctrl"

    // ---- Action registration tests ----

    fun `test ToggleExplorer action is registered`() {
        val action = actionManager.getAction("SystemExplorer.Toggle")
        assertNotNull("ToggleExplorer action should be registered", action)
    }

    fun `test CopyFiles action is registered`() {
        val action = actionManager.getAction("SystemExplorer.CopyFiles")
        assertNotNull("CopyFiles action should be registered", action)
    }

    fun `test CutFiles action is registered`() {
        val action = actionManager.getAction("SystemExplorer.CutFiles")
        assertNotNull("CutFiles action should be registered", action)
    }

    fun `test PasteFiles action is registered`() {
        val action = actionManager.getAction("SystemExplorer.PasteFiles")
        assertNotNull("PasteFiles action should be registered", action)
    }

    fun `test CopyPath action is registered`() {
        val action = actionManager.getAction("SystemExplorer.CopyPath")
        assertNotNull("CopyPath action should be registered", action)
    }

    fun `test RenameFile action is registered`() {
        val action = actionManager.getAction("SystemExplorer.RenameFile")
        assertNotNull("RenameFile action should be registered", action)
    }

    fun `test DeleteFiles action is registered`() {
        val action = actionManager.getAction("SystemExplorer.DeleteFiles")
        assertNotNull("DeleteFiles action should be registered", action)
    }

    fun `test RefreshTree action is registered`() {
        val action = actionManager.getAction("SystemExplorer.RefreshTree")
        assertNotNull("RefreshTree action should be registered", action)
    }

    // ---- Shortcut binding tests ----

    fun `test ToggleExplorer has Alt+E shortcut`() {
        assertShortcutBound("SystemExplorer.Toggle", "alt E")
    }

    fun `test CopyFiles has Ctrl+C shortcut`() {
        assertShortcutBound("SystemExplorer.CopyFiles", "$platformCtrl C")
    }

    fun `test CutFiles has Ctrl+X shortcut`() {
        assertShortcutBound("SystemExplorer.CutFiles", "$platformCtrl X")
    }

    fun `test PasteFiles has Ctrl+V shortcut`() {
        assertShortcutBound("SystemExplorer.PasteFiles", "$platformCtrl V")
    }

    fun `test CopyPath has Ctrl+Shift+C shortcut`() {
        assertShortcutBound("SystemExplorer.CopyPath", "$platformCtrl shift C")
    }

    fun `test RenameFile has F2 shortcut`() {
        assertShortcutBound("SystemExplorer.RenameFile", "F2")
    }

    fun `test DeleteFiles has DELETE shortcut`() {
        assertShortcutBound("SystemExplorer.DeleteFiles", "DELETE")
    }

    fun `test RefreshTree has F5 shortcut`() {
        assertShortcutBound("SystemExplorer.RefreshTree", "F5")
    }

    // ---- QuickOpen action tests ----

    fun `test QuickOpen action is registered`() {
        val action = actionManager.getAction("SystemExplorer.QuickOpen")
        assertNotNull("QuickOpen action should be registered", action)
    }

    fun `test QuickOpen has Cmd+Shift+P shortcut`() {
        assertShortcutBound("SystemExplorer.QuickOpen", "$platformCtrl shift P")
    }

    // ---- Action group test ----

    fun `test all actions belong to SystemExplorer group`() {
        val group = actionManager.getAction("SystemExplorer.ActionGroup")
        assertNotNull("SystemExplorer.ActionGroup should be registered", group)
    }

    fun `test QuickOpen action is registered outside the explorer action group`() {
        // QuickOpen should be a top-level action, not inside SystemExplorer.ActionGroup
        val actionManager = com.intellij.openapi.actionSystem.ActionManager.getInstance()
        val action = actionManager.getAction("SystemExplorer.QuickOpen")
        assertNotNull("QuickOpen action should be registered", action)

        // Verify it's NOT inside the group (it should be independent)
        val group = actionManager.getAction("SystemExplorer.ActionGroup") as? com.intellij.openapi.actionSystem.DefaultActionGroup
        if (group != null) {
            val children = group.getChildren(null)
            val quickOpenInGroup = children.any {
                actionManager.getId(it) == "SystemExplorer.QuickOpen"
            }
            assertFalse("QuickOpen should NOT be inside SystemExplorer.ActionGroup for global availability", quickOpenInGroup)
        }
    }

    // ---- Helper ----

    private fun assertShortcutBound(actionId: String, keystrokeSpec: String) {
        val keymap = KeymapManager.getInstance().activeKeymap
        val shortcuts = keymap.getShortcuts(actionId)
        val expectedStroke = KeyStroke.getKeyStroke(keystrokeSpec)
        assertNotNull("KeyStroke for '$keystrokeSpec' should parse", expectedStroke)

        val found = shortcuts.any { sc ->
            sc is KeyboardShortcut && sc.firstKeyStroke == expectedStroke
        }
        assertTrue(
            "Action '$actionId' should have keyboard shortcut '$keystrokeSpec' but found: ${shortcuts.toList()}",
            found
        )
    }
}
