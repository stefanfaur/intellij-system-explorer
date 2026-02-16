package ro.faur.explorer.light

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.settings.ExplorerSettings
import ro.faur.explorer.ui.ExplorerPanel
import java.awt.BorderLayout
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 * Integration tests for the status bar layout in ExplorerPanel.
 *
 * Verifies that:
 * - Status bar is correctly laid out with BorderLayout
 * - Hidden checkbox functionality works correctly
 * - Permissions checkbox (on Unix/Mac) correctly updates settings
 * - Checkboxes trigger appropriate tree updates
 */
class StatusBarLayoutTest : BasePlatformTestCase() {

    private lateinit var explorerPanel: ExplorerPanel

    override fun setUp() {
        super.setUp()
        // Reset settings to defaults
        ExplorerSettings.getInstance().loadState(ExplorerSettings.State())
        explorerPanel = ExplorerPanel(project)
    }

    override fun tearDown() {
        try {
            explorerPanel.dispose()
        } finally {
            super.tearDown()
        }
    }

    fun `test status bar exists in explorer panel`() {
        val component = explorerPanel.component
        assertNotNull(component)

        // The main panel should have a SOUTH component (status bar)
        val mainPanel = component as? JPanel
        assertNotNull(mainPanel)
        assertTrue(mainPanel!!.layout is BorderLayout)
    }

    fun `test hidden checkbox exists and starts unchecked`() {
        val component = explorerPanel.component as JPanel
        val statusBar = findStatusBar(component)
        assertNotNull("Status bar should exist", statusBar)

        val hiddenCheckbox = findCheckboxByLabel(statusBar!!, "Hidden")
        assertNotNull("Hidden checkbox should exist", hiddenCheckbox)
        assertFalse("Hidden checkbox should start unchecked", hiddenCheckbox!!.isSelected)
    }

    fun `test permissions checkbox exists on non-Windows platforms`() {
        val component = explorerPanel.component as JPanel
        val statusBar = findStatusBar(component)
        assertNotNull("Status bar should exist", statusBar)

        val permissionsCheckbox = findCheckboxByLabel(statusBar!!, "Permissions")

        if (SystemInfo.isWindows) {
            assertNull("Permissions checkbox should not exist on Windows", permissionsCheckbox)
        } else {
            assertNotNull("Permissions checkbox should exist on Unix/Mac", permissionsCheckbox)
            // Should initialize from settings
            val settings = ExplorerSettings.getInstance()
            assertEquals("Permissions checkbox should match settings value",
                settings.state.showFilePermissions, permissionsCheckbox!!.isSelected)
        }
    }

    fun `test permissions checkbox state syncs with settings`() {
        if (SystemInfo.isWindows) {
            // Skip on Windows
            return
        }

        val component = explorerPanel.component as JPanel
        val statusBar = findStatusBar(component)
        val permissionsCheckbox = findCheckboxByLabel(statusBar!!, "Permissions")
        assertNotNull(permissionsCheckbox)

        val settings = ExplorerSettings.getInstance()
        val initialState = settings.state.showFilePermissions

        // Toggle checkbox
        permissionsCheckbox!!.isSelected = !initialState

        // Verify settings updated
        assertEquals("Settings should update when checkbox is toggled",
            !initialState, settings.state.showFilePermissions)
    }

    fun `test hidden checkbox toggles tree visibility`() {
        val component = explorerPanel.component as JPanel
        val statusBar = findStatusBar(component)
        val hiddenCheckbox = findCheckboxByLabel(statusBar!!, "Hidden")
        assertNotNull(hiddenCheckbox)

        val initialShowHidden = explorerPanel.fileTreeComponent.showHidden

        // Toggle checkbox
        hiddenCheckbox!!.doClick()

        // Verify tree component updated
        assertEquals("Tree component showHidden should toggle with checkbox",
            !initialShowHidden, explorerPanel.fileTreeComponent.showHidden)
    }

    fun `test status bar layout is BorderLayout`() {
        val component = explorerPanel.component as JPanel
        val statusBar = findStatusBar(component)
        assertNotNull(statusBar)

        assertTrue("Status bar should use BorderLayout for left/right positioning",
            statusBar!!.layout is BorderLayout)
    }

    // Helper methods

    private fun findStatusBar(mainPanel: JPanel): JPanel? {
        val layout = mainPanel.layout as? BorderLayout ?: return null
        return layout.getLayoutComponent(BorderLayout.SOUTH) as? JPanel
    }

    private fun findCheckboxByLabel(parent: JPanel, label: String): JCheckBox? {
        return findCheckboxRecursive(parent, label)
    }

    private fun findCheckboxRecursive(container: java.awt.Container, label: String): JCheckBox? {
        for (component in container.components) {
            if (component is JCheckBox && component.text == label) {
                return component
            }
            if (component is java.awt.Container) {
                val found = findCheckboxRecursive(component, label)
                if (found != null) return found
            }
        }
        return null
    }
}
