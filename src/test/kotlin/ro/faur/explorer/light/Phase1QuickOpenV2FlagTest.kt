package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.settings.QuickOpenSettings

/**
 * Phase 6 settings fields: ripgrepPath, useRipgrepForExternalPaths,
 * maxIndexSize, contentSearchEnabled.
 *
 * All tests fail to compile if the fields are absent from QuickOpenSettings.State.
 */
class Phase1QuickOpenV2FlagTest : BasePlatformTestCase() {

    // -------------------------------------------------------------------------
    // ripgrepPath (Phase 6)
    // -------------------------------------------------------------------------

    fun `test ripgrepPath defaults to empty string`() {
        val settings = QuickOpenSettings.getInstance()
        assertEquals("", settings.state.ripgrepPath)
    }

    fun `test ripgrepPath can be set and retrieved`() {
        val settings = QuickOpenSettings.getInstance()
        settings.state.ripgrepPath = "/opt/homebrew/bin/rg"
        assertEquals("/opt/homebrew/bin/rg", settings.state.ripgrepPath)
    }

    // -------------------------------------------------------------------------
    // useRipgrepForExternalPaths (Phase 6)
    // -------------------------------------------------------------------------

    fun `test useRipgrepForExternalPaths defaults to false`() {
        val settings = QuickOpenSettings.getInstance()
        assertFalse(settings.state.useRipgrepForExternalPaths)
    }

    // -------------------------------------------------------------------------
    // maxIndexSize (Phase 6)
    // -------------------------------------------------------------------------

    fun `test maxIndexSize defaults to 50000`() {
        val settings = QuickOpenSettings.getInstance()
        assertEquals(50_000, settings.state.maxIndexSize)
    }

    // -------------------------------------------------------------------------
    // contentSearchEnabled (Phase 6)
    // -------------------------------------------------------------------------

    fun `test contentSearchEnabled defaults to true`() {
        val settings = QuickOpenSettings.getInstance()
        assertTrue(settings.state.contentSearchEnabled)
    }

    // -------------------------------------------------------------------------
    // All new Phase 6 fields survive a state round-trip together
    // -------------------------------------------------------------------------

    fun `test all Phase 6 settings fields survive round-trip`() {
        val settings = QuickOpenSettings.getInstance()
        settings.state.ripgrepPath = "/usr/bin/rg"
        settings.state.useRipgrepForExternalPaths = true
        settings.state.maxIndexSize = 25_000
        settings.state.contentSearchEnabled = false

        val state = settings.state
        val fresh = QuickOpenSettings()
        fresh.loadState(state)

        assertEquals("/usr/bin/rg", fresh.state.ripgrepPath)
        assertTrue(fresh.state.useRipgrepForExternalPaths)
        assertEquals(25_000, fresh.state.maxIndexSize)
        assertFalse(fresh.state.contentSearchEnabled)
    }
}
