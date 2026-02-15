package com.github.stefanfaur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.stefanfaur.explorer.settings.ExplorerConfigurable
import com.github.stefanfaur.explorer.settings.ExplorerSettings

class ExplorerConfigurableTest : BasePlatformTestCase() {

    private lateinit var configurable: ExplorerConfigurable

    override fun setUp() {
        super.setUp()
        // Reset settings to defaults before each test
        ExplorerSettings.getInstance().loadState(ExplorerSettings.State())
        configurable = ExplorerConfigurable()
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
        } finally {
            super.tearDown()
        }
    }

    fun `test displayName is System Explorer`() {
        assertEquals("System Explorer", configurable.displayName)
    }

    fun `test createComponent returns non-null panel`() {
        val component = configurable.createComponent()
        assertNotNull(component)
    }

    fun `test isModified returns false initially`() {
        configurable.createComponent()
        configurable.reset()
        assertFalse(configurable.isModified)
    }

    fun `test changing showHiddenFiles checkbox makes isModified true`() {
        configurable.createComponent()
        configurable.reset()

        // Flip the showHiddenFiles value in the UI
        configurable.showHiddenFilesCheckBox.isSelected = !ExplorerSettings.getInstance().state.showHiddenFiles
        assertTrue(configurable.isModified)
    }

    fun `test changing sortFoldersFirst checkbox makes isModified true`() {
        configurable.createComponent()
        configurable.reset()

        configurable.sortFoldersFirstCheckBox.isSelected = !ExplorerSettings.getInstance().state.sortFoldersFirst
        assertTrue(configurable.isModified)
    }

    fun `test changing confirmDelete checkbox makes isModified true`() {
        configurable.createComponent()
        configurable.reset()

        configurable.confirmDeleteCheckBox.isSelected = !ExplorerSettings.getInstance().state.confirmDelete
        assertTrue(configurable.isModified)
    }

    fun `test changing deleteToTrash checkbox makes isModified true`() {
        configurable.createComponent()
        configurable.reset()

        configurable.deleteToTrashCheckBox.isSelected = !ExplorerSettings.getInstance().state.deleteToTrash
        assertTrue(configurable.isModified)
    }

    fun `test changing defaultRoot field makes isModified true`() {
        configurable.createComponent()
        configurable.reset()

        configurable.defaultRootField.text = "/some/other/path"
        assertTrue(configurable.isModified)
    }

    fun `test apply saves settings to ExplorerSettings`() {
        configurable.createComponent()
        configurable.reset()

        configurable.showHiddenFilesCheckBox.isSelected = true
        configurable.sortFoldersFirstCheckBox.isSelected = false
        configurable.confirmDeleteCheckBox.isSelected = false
        configurable.deleteToTrashCheckBox.isSelected = false
        configurable.defaultRootField.text = "/custom/root"

        configurable.apply()

        val settings = ExplorerSettings.getInstance()
        assertTrue(settings.state.showHiddenFiles)
        assertFalse(settings.state.sortFoldersFirst)
        assertFalse(settings.state.confirmDelete)
        assertFalse(settings.state.deleteToTrash)
        assertEquals("/custom/root", settings.state.defaultRoot)
    }

    fun `test reset restores UI from settings`() {
        val settings = ExplorerSettings.getInstance()
        settings.loadState(ExplorerSettings.State(
            showHiddenFiles = true,
            sortFoldersFirst = false,
            confirmDelete = false,
            deleteToTrash = false,
            defaultRoot = "/restored/path"
        ))

        configurable.createComponent()
        configurable.reset()

        assertTrue(configurable.showHiddenFilesCheckBox.isSelected)
        assertFalse(configurable.sortFoldersFirstCheckBox.isSelected)
        assertFalse(configurable.confirmDeleteCheckBox.isSelected)
        assertFalse(configurable.deleteToTrashCheckBox.isSelected)
        assertEquals("/restored/path", configurable.defaultRootField.text)
    }

    fun `test isModified returns false after apply`() {
        configurable.createComponent()
        configurable.reset()

        configurable.showHiddenFilesCheckBox.isSelected = true
        assertTrue(configurable.isModified)

        configurable.apply()
        assertFalse(configurable.isModified)
    }

    fun `test reset after modification restores original values`() {
        configurable.createComponent()
        configurable.reset()

        val originalShowHidden = ExplorerSettings.getInstance().state.showHiddenFiles

        configurable.showHiddenFilesCheckBox.isSelected = !originalShowHidden
        assertTrue(configurable.isModified)

        configurable.reset()
        assertEquals(originalShowHidden, configurable.showHiddenFilesCheckBox.isSelected)
        assertFalse(configurable.isModified)
    }
}
