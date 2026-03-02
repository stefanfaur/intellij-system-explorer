package ro.faur.explorer.light

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.settings.ExplorerSettings
import ro.faur.explorer.ui.ExplorerPanel
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Integration tests for the status bar layout in ExplorerPanel.
 *
 * Verifies that:
 * - The main panel uses BorderLayout
 * - Hidden toggle button exists in BrowserPanel
 * - Permissions toggle button exists on Unix/Mac (absent on Windows)
 * - Toggling hidden updates fileTreeComponent.showHidden
 * - Permissions toggle state syncs with settings
 */
class StatusBarLayoutTest : BasePlatformTestCase() {

    private lateinit var explorerPanel: ExplorerPanel

    override fun setUp() {
        super.setUp()
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
        val mainPanel = component as? JPanel
        assertNotNull(mainPanel)
        assertTrue(mainPanel!!.layout is BorderLayout)
    }

    fun `test hidden checkbox exists and starts unchecked`() {
        val localPanel = explorerPanel.browserHost.localPanel
        val hiddenToggle = localPanel.hiddenToggle
        assertNotNull("Hidden toggle should exist", hiddenToggle)
        assertFalse("Hidden toggle should start inactive", localPanel.showHidden)
    }

    fun `test permissions checkbox exists on non-Windows platforms`() {
        val localPanel = explorerPanel.browserHost.localPanel
        if (SystemInfo.isWindows) {
            assertNull("Permissions toggle should not exist on Windows", localPanel.permissionsToggle)
        } else {
            assertNotNull("Permissions toggle should exist on Unix/Mac", localPanel.permissionsToggle)
            val settings = ExplorerSettings.getInstance()
            assertEquals(
                "Permissions state should match settings",
                settings.state.showFilePermissions,
                localPanel.showPermissions
            )
        }
    }

    fun `test permissions checkbox state syncs with settings`() {
        if (SystemInfo.isWindows) return

        val localPanel = explorerPanel.browserHost.localPanel
        assertNotNull(localPanel.permissionsToggle)

        val settings = ExplorerSettings.getInstance()
        val initialState = settings.state.showFilePermissions

        localPanel.permissionsToggle!!.doClick()

        assertEquals(
            "Settings should update when permissions toggle is clicked",
            !initialState,
            settings.state.showFilePermissions
        )
    }

    fun `test hidden checkbox toggles tree visibility`() {
        val localPanel = explorerPanel.browserHost.localPanel
        val initialShowHidden = localPanel.showHidden

        localPanel.hiddenToggle.doClick()

        assertEquals(
            "showHidden should toggle when hidden toggle is clicked",
            !initialShowHidden,
            localPanel.showHidden
        )
    }

    fun `test status bar layout is BorderLayout`() {
        val component = explorerPanel.component as JPanel
        assertTrue(
            "ExplorerPanel root should use BorderLayout",
            component.layout is BorderLayout
        )
    }
}
