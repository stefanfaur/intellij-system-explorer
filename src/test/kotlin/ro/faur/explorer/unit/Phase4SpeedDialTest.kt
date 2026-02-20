package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ro.faur.explorer.quickopen.aliases.TeleportAliasStore

/**
 * Phase 4 — TeleportAliasStore pure-unit tests (no IntelliJ application context)
 *
 * These tests instantiate TeleportAliasStore directly (not via service registry),
 * exercising the state management logic in isolation.
 *
 * Tests fail to compile until TeleportAliasStore exists.
 */
class Phase4SpeedDialTest {

    private fun freshStore(): TeleportAliasStore {
        val store = TeleportAliasStore()
        store.loadState(TeleportAliasStore.State())
        return store
    }

    @Test
    fun `alias can be added and retrieved without application context`() {
        val store = freshStore()
        store.addAlias("myalias", "/tmp/target")
        assertEquals("/tmp/target", store.getAliases()["myalias"])
    }

    @Test
    fun `adding alias with same name replaces old value`() {
        val store = freshStore()
        store.addAlias("key", "/first")
        store.addAlias("key", "/second")
        val aliases = store.getAliases()
        assertEquals(1, aliases.size, "Should only have one entry for 'key'")
        assertEquals("/second", aliases["key"])
    }

    @Test
    fun `removing alias leaves other aliases intact`() {
        val store = freshStore()
        store.addAlias("a", "/path/a")
        store.addAlias("b", "/path/b")
        store.removeAlias("a")
        val aliases = store.getAliases()
        assertFalse(aliases.containsKey("a"))
        assertTrue(aliases.containsKey("b"))
    }

    @Test
    fun `getAliases returns an immutable snapshot`() {
        val store = freshStore()
        store.addAlias("snap", "/snap/path")
        val snapshot = store.getAliases()
        store.addAlias("extra", "/extra/path")
        // snapshot should not reflect the addition made after it was captured
        // (the plan says getAliases() returns an associate, i.e. a new Map)
        assertFalse(snapshot.containsKey("extra"), "Snapshot should not contain later additions")
    }

    @Test
    fun `TeleportAliasStore State can be created and has empty aliases list`() {
        val state = TeleportAliasStore.State()
        assertTrue(state.aliases.isEmpty())
    }

    @Test
    fun `getState returns current state object`() {
        val store = freshStore()
        store.addAlias("x", "/x")
        val state = store.getState()
        assertNotNull(state)
        assertTrue(state.aliases.any { it.name == "x" && it.path == "/x" })
    }
}
