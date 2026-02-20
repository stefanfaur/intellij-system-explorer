package ro.faur.explorer.light

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.quickopen.aliases.TeleportAliasStore

/**
 * Phase 4 — TeleportAliasStore persistence and CRUD
 *
 * Covers the Automated Verification items:
 *   - persistence round-trip: aliases survive loadState/getState cycle
 *   - alias resolution: given "proj" → "/some/path", getAliases()["proj"] == "/some/path"
 *
 * All tests fail to compile until TeleportAliasStore exists under
 * ro.faur.explorer.quickopen.aliases.
 */
class Phase4TeleportAliasStoreTest : BasePlatformTestCase() {

    private fun freshStore(): TeleportAliasStore {
        val store = TeleportAliasStore()
        store.loadState(TeleportAliasStore.State())
        return store
    }

    // -------------------------------------------------------------------------
    // Basic CRUD
    // -------------------------------------------------------------------------

    fun `test addAlias stores alias accessible via getAliases`() {
        val store = freshStore()
        store.addAlias("proj", "/some/path")
        val aliases = store.getAliases()
        assertTrue(aliases.containsKey("proj"))
        assertEquals("/some/path", aliases["proj"])
    }

    fun `test removeAlias removes the alias`() {
        val store = freshStore()
        store.addAlias("proj", "/some/path")
        store.removeAlias("proj")
        assertFalse(store.getAliases().containsKey("proj"))
    }

    fun `test addAlias with existing name overwrites the path`() {
        val store = freshStore()
        store.addAlias("proj", "/old/path")
        store.addAlias("proj", "/new/path")
        assertEquals("/new/path", store.getAliases()["proj"])
    }

    fun `test removing non-existent alias does not throw`() {
        val store = freshStore()
        store.removeAlias("does-not-exist")
        // Should complete without exception
        assertTrue(store.getAliases().isEmpty())
    }

    fun `test multiple aliases coexist`() {
        val store = freshStore()
        store.addAlias("home", "/Users/test")
        store.addAlias("proj", "/workspace/myproject")
        store.addAlias("cfg", "/etc/config")
        val aliases = store.getAliases()
        assertEquals(3, aliases.size)
        assertEquals("/Users/test", aliases["home"])
        assertEquals("/workspace/myproject", aliases["proj"])
        assertEquals("/etc/config", aliases["cfg"])
    }

    fun `test getAliases returns empty map when no aliases added`() {
        val store = freshStore()
        assertTrue(store.getAliases().isEmpty())
    }

    // -------------------------------------------------------------------------
    // Persistence round-trip
    // -------------------------------------------------------------------------

    fun `test state round-trip preserves all aliases`() {
        val store = freshStore()
        store.addAlias("proj", "/workspace/project")
        store.addAlias("tests", "/workspace/project/src/test")

        val savedState = store.getState()

        val reloaded = TeleportAliasStore()
        reloaded.loadState(savedState)

        val aliases = reloaded.getAliases()
        assertEquals("/workspace/project", aliases["proj"])
        assertEquals("/workspace/project/src/test", aliases["tests"])
    }

    fun `test state round-trip with empty store produces empty aliases`() {
        val store = freshStore()
        val savedState = store.getState()

        val reloaded = TeleportAliasStore()
        reloaded.loadState(savedState)

        assertTrue(reloaded.getAliases().isEmpty())
    }

    fun `test state round-trip after removal reflects removal`() {
        val store = freshStore()
        store.addAlias("temp", "/tmp/thing")
        store.removeAlias("temp")

        val savedState = store.getState()
        val reloaded = TeleportAliasStore()
        reloaded.loadState(savedState)

        assertFalse(reloaded.getAliases().containsKey("temp"))
    }

    // -------------------------------------------------------------------------
    // Service accessibility (requires application context)
    // -------------------------------------------------------------------------

    fun `test getInstance returns non-null service`() {
        val instance = TeleportAliasStore.getInstance()
        assertNotNull(instance)
    }

    fun `test getInstance returns the same service object across calls`() {
        val a = TeleportAliasStore.getInstance()
        val b = TeleportAliasStore.getInstance()
        assertSame(a, b)
    }
}
