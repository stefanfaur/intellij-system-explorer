package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.settings.ExplorerSettings

/**
 * Phase 1 gap — quickOpenV2Enabled feature flag
 *
 * The Success Criteria for Phase 1 include a manual step "Toggle quickOpenV2Enabled = true
 * in settings, trigger Quick Open — popup opens centered." The plan adds this field to
 * ExplorerSettings.State but existing ExplorerSettingsTest does not cover it.
 *
 * Phase 6 adds further settings fields: ripgrepPath, useRipgrepForExternalPaths,
 * maxIndexSize, allowNetworkMountIndexing, contentSearchEnabled, contentSearchScope.
 *
 * All tests fail to compile if the fields are absent from ExplorerSettings.State.
 */
class Phase1QuickOpenV2FlagTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        ExplorerSettings.getInstance().loadState(ExplorerSettings.State())
    }

    // -------------------------------------------------------------------------
    // quickOpenV2Enabled (Phase 1)
    // -------------------------------------------------------------------------

    fun `test quickOpenV2Enabled defaults to false`() {
        val settings = ExplorerSettings.getInstance()
        assertFalse(settings.state.quickOpenV2Enabled)
    }

    fun `test quickOpenV2Enabled can be toggled to true`() {
        val settings = ExplorerSettings.getInstance()
        settings.state.quickOpenV2Enabled = true
        assertTrue(ExplorerSettings.getInstance().state.quickOpenV2Enabled)
    }

    fun `test quickOpenV2Enabled persists in state round-trip`() {
        val settings = ExplorerSettings.getInstance()
        settings.state.quickOpenV2Enabled = true
        val state = settings.state

        val fresh = ExplorerSettings()
        fresh.loadState(state)

        assertTrue(fresh.state.quickOpenV2Enabled)
    }

    fun `test quickOpenV2Enabled can be disabled after being enabled`() {
        val settings = ExplorerSettings.getInstance()
        settings.state.quickOpenV2Enabled = true
        settings.state.quickOpenV2Enabled = false
        assertFalse(settings.state.quickOpenV2Enabled)
    }

    // -------------------------------------------------------------------------
    // ripgrepPath (Phase 6)
    // -------------------------------------------------------------------------

    fun `test ripgrepPath defaults to empty string`() {
        val settings = ExplorerSettings.getInstance()
        assertEquals("", settings.state.ripgrepPath)
    }

    fun `test ripgrepPath can be set and retrieved`() {
        val settings = ExplorerSettings.getInstance()
        settings.state.ripgrepPath = "/opt/homebrew/bin/rg"
        assertEquals("/opt/homebrew/bin/rg", settings.state.ripgrepPath)
    }

    // -------------------------------------------------------------------------
    // useRipgrepForExternalPaths (Phase 6)
    // -------------------------------------------------------------------------

    fun `test useRipgrepForExternalPaths defaults to false`() {
        val settings = ExplorerSettings.getInstance()
        assertFalse(settings.state.useRipgrepForExternalPaths)
    }

    // -------------------------------------------------------------------------
    // maxIndexSize (Phase 6)
    // -------------------------------------------------------------------------

    fun `test maxIndexSize defaults to 50000`() {
        val settings = ExplorerSettings.getInstance()
        assertEquals(50_000, settings.state.maxIndexSize)
    }

    // -------------------------------------------------------------------------
    // allowNetworkMountIndexing (Phase 6)
    // -------------------------------------------------------------------------

    fun `test allowNetworkMountIndexing defaults to false`() {
        val settings = ExplorerSettings.getInstance()
        assertFalse(settings.state.allowNetworkMountIndexing)
    }

    // -------------------------------------------------------------------------
    // contentSearchEnabled (Phase 6)
    // -------------------------------------------------------------------------

    fun `test contentSearchEnabled defaults to true`() {
        val settings = ExplorerSettings.getInstance()
        assertTrue(settings.state.contentSearchEnabled)
    }

    // -------------------------------------------------------------------------
    // contentSearchScope (Phase 6)
    // -------------------------------------------------------------------------

    fun `test contentSearchScope defaults to empty string`() {
        val settings = ExplorerSettings.getInstance()
        assertEquals("", settings.state.contentSearchScope)
    }

    // -------------------------------------------------------------------------
    // All new Phase 6 fields survive a state round-trip together
    // -------------------------------------------------------------------------

    fun `test all Phase 6 settings fields survive round-trip`() {
        val settings = ExplorerSettings.getInstance()
        settings.state.ripgrepPath = "/usr/bin/rg"
        settings.state.useRipgrepForExternalPaths = true
        settings.state.maxIndexSize = 25_000
        settings.state.allowNetworkMountIndexing = true
        settings.state.contentSearchEnabled = false
        settings.state.contentSearchScope = "/custom/scope"

        val state = settings.state
        val fresh = ExplorerSettings()
        fresh.loadState(state)

        assertEquals("/usr/bin/rg", fresh.state.ripgrepPath)
        assertTrue(fresh.state.useRipgrepForExternalPaths)
        assertEquals(25_000, fresh.state.maxIndexSize)
        assertTrue(fresh.state.allowNetworkMountIndexing)
        assertFalse(fresh.state.contentSearchEnabled)
        assertEquals("/custom/scope", fresh.state.contentSearchScope)
    }
}
