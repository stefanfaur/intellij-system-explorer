package com.github.stefanfaur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.settings.ExplorerSettings

class ExplorerSettingsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Reset settings to defaults before each test
        ExplorerSettings.getInstance().loadState(ExplorerSettings.State())
    }

    fun `test default settings have expected values`() {
        val settings = ExplorerSettings.getInstance()

        assertFalse(settings.state.showHiddenFiles)
        assertTrue(settings.state.sortFoldersFirst)
        assertTrue(settings.state.confirmDelete)
        assertTrue(settings.state.deleteToTrash)
        assertEquals("", settings.state.defaultRoot) // empty means user home
    }

    fun `test modifying setting persists across getInstance calls`() {
        val settings = ExplorerSettings.getInstance()
        settings.state.showHiddenFiles = true
        settings.state.sortFoldersFirst = false

        // Get a fresh reference
        val reloaded = ExplorerSettings.getInstance()

        assertTrue(reloaded.state.showHiddenFiles)
        assertFalse(reloaded.state.sortFoldersFirst)
    }

    fun `test state is serializable to XML and back`() {
        val settings = ExplorerSettings.getInstance()
        settings.state.showHiddenFiles = true
        settings.state.confirmDelete = false
        settings.state.defaultRoot = "/custom/path"

        val state = settings.state

        // Create fresh instance and load state
        val fresh = ExplorerSettings()
        fresh.loadState(state)

        assertTrue(fresh.state.showHiddenFiles)
        assertFalse(fresh.state.confirmDelete)
        assertEquals("/custom/path", fresh.state.defaultRoot)
    }

    fun `test new settings have expected default values`() {
        val settings = ExplorerSettings.getInstance()

        // New settings with defaults
        assertTrue(settings.state.showFileSizeInTree)
        assertFalse(settings.state.showFilePermissions)
        assertEquals("name", settings.state.sortBy)
        assertTrue(settings.state.expandDirectoriesOnSingleClick)
        assertTrue(settings.state.rememberLastPath)
    }

    fun `test default state equals unmodified state`() {
        // PersistentStateComponent only saves when state differs from default
        val default1 = ExplorerSettings.State()
        val default2 = ExplorerSettings.State()

        assertEquals(default1, default2)
    }
}
