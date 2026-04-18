package ro.faur.explorer.shortcuts

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.ui.ExplorerPanel
import javax.swing.JPanel

/**
 * Tests for [ContextResolver] context detection.
 *
 * These tests verify that:
 * 1. Panels are registered correctly with ContextResolver when ExplorerPanel is created
 * 2. Manual registration/unregistration works
 * 3. ContextResolver methods don't throw
 *
 * Note: Focus-based context detection cannot be tested in a headless test environment
 * where KeyboardFocusManager.focusOwner is always null. The actual context detection
 * is verified through integration testing in a real IDE.
 */
class ContextResolverTest : BasePlatformTestCase() {

    private lateinit var explorerPanel: ExplorerPanel
    private lateinit var contextResolver: ContextResolver

    override fun setUp() {
        super.setUp()
        contextResolver = ContextResolver.instance
        explorerPanel = ExplorerPanel(project)
    }

    override fun tearDown() {
        try {
            explorerPanel.dispose()
        } finally {
            super.tearDown()
        }
    }

    // ─── Core Registration Tests ───────────────────────────────────────────

    /**
     * CRITICAL: This is the primary test that verifies the fix.
     * The local panel must be registered when ExplorerPanel is created.
     */
    fun `test local panel is registered when ExplorerPanel is created`() {
        val localPanel = explorerPanel.browserHost.localPanel
        
        val isRegistered = contextResolver.isRegistered(PanelContext.LOCAL_BROWSER, localPanel)
        assertTrue(
            "Local panel should be registered for LOCAL_BROWSER context when ExplorerPanel is created. " +
            "This test failing indicates the ShortcutController.registerBrowserHost() is not being called.",
            isRegistered
        )
    }

    /**
     * Verifies that at least one component is registered for LOCAL_BROWSER.
     */
    fun `test local browser has at least one registered component`() {
        val count = contextResolver.getRegisteredCount(PanelContext.LOCAL_BROWSER)
        assertTrue(
            "LOCAL_BROWSER should have at least 1 registered component, but had $count",
            count >= 1
        )
    }

    /**
     * Verifies ContextResolver is a singleton.
     */
    fun `test context resolver is singleton`() {
        val instance1 = ContextResolver.instance
        val instance2 = ContextResolver.instance
        assertSame("ContextResolver should be a singleton", instance1, instance2)
    }

    // ─── Basic Method Tests ────────────────────────────────────────────────

    fun `test getActiveContext does not throw`() {
        val context = contextResolver.getActiveContext()
        assertNotNull("getActiveContext should return a context", context)
    }

    fun `test updateContext does not throw`() {
        contextResolver.updateContext()
        // If we get here without exception, the test passes
        assertTrue("updateContext should complete without throwing", true)
    }

    fun `test onContextChanged callback can be set`() {
        var called = false
        contextResolver.onContextChanged = { called = true }
        assertNotNull("onContextChanged should be settable", contextResolver.onContextChanged)
    }

    // ─── Manual Registration Tests ────────────────────────────────────────

    fun `test registerPanel adds component to registry`() {
        val testPanel = JPanel()
        
        // Verify not registered initially
        assertFalse(
            "Test panel should not be registered initially",
            contextResolver.isRegistered(PanelContext.REMOTE_BROWSER, testPanel)
        )
        
        // Register
        contextResolver.registerPanel(PanelContext.REMOTE_BROWSER, testPanel)
        
        // Verify registered
        assertTrue(
            "Test panel should be registered after registerPanel call",
            contextResolver.isRegistered(PanelContext.REMOTE_BROWSER, testPanel)
        )
        
        // Clean up
        contextResolver.unregisterPanel(PanelContext.REMOTE_BROWSER, testPanel)
    }

    fun `test unregisterPanel removes component from registry`() {
        val testPanel = JPanel()
        
        // Register
        contextResolver.registerPanel(PanelContext.REMOTE_BROWSER, testPanel)
        assertTrue(
            "Test panel should be registered",
            contextResolver.isRegistered(PanelContext.REMOTE_BROWSER, testPanel)
        )
        
        // Unregister
        contextResolver.unregisterPanel(PanelContext.REMOTE_BROWSER, testPanel)
        
        // Verify removed
        assertFalse(
            "Test panel should be removed after unregisterPanel call",
            contextResolver.isRegistered(PanelContext.REMOTE_BROWSER, testPanel)
        )
    }

    fun `test isComponentActive does not throw`() {
        val localPanel = explorerPanel.browserHost.localPanel
        // Should not throw regardless of focus state
        val isActive = contextResolver.isComponentActive(localPanel)
        assertNotNull("isComponentActive should return a boolean", isActive)
    }

    // ─── Multiple Context Tests ───────────────────────────────────────────

    fun `test multiple contexts can be registered independently`() {
        val testPanel1 = JPanel()
        val testPanel2 = JPanel()
        
        // Register for different contexts
        contextResolver.registerPanel(PanelContext.LOCAL_BROWSER, testPanel1)
        contextResolver.registerPanel(PanelContext.REMOTE_BROWSER, testPanel2)
        
        // Verify independent registration
        assertTrue(
            "Panel 1 should be in LOCAL_BROWSER",
            contextResolver.isRegistered(PanelContext.LOCAL_BROWSER, testPanel1)
        )
        assertTrue(
            "Panel 2 should be in REMOTE_BROWSER",
            contextResolver.isRegistered(PanelContext.REMOTE_BROWSER, testPanel2)
        )
        
        // Verify they don't cross-contaminate
        assertFalse(
            "Panel 1 should NOT be in REMOTE_BROWSER",
            contextResolver.isRegistered(PanelContext.REMOTE_BROWSER, testPanel1)
        )
        assertFalse(
            "Panel 2 should NOT be in LOCAL_BROWSER",
            contextResolver.isRegistered(PanelContext.LOCAL_BROWSER, testPanel2)
        )
        
        // Clean up
        contextResolver.unregisterPanel(PanelContext.LOCAL_BROWSER, testPanel1)
        contextResolver.unregisterPanel(PanelContext.REMOTE_BROWSER, testPanel2)
    }
}
