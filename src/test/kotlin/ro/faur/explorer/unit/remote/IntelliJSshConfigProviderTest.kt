package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.IntelliJSshConfigProvider

class IntelliJSshConfigProviderTest {

    @Test
    fun `returns empty list when IntelliJ SSH plugin is not available`() {
        // In a unit test environment the IntelliJ SSH plugin classes are not on the classpath,
        // so getProfiles() should gracefully return an empty list rather than throwing.
        val profiles = IntelliJSshConfigProvider.getProfiles()
        assertTrue(profiles.isEmpty(), "Expected empty list when SSH plugin is absent")
    }

    @Test
    fun `getProfiles returns a list type`() {
        // Verify the return type contract — must always be a List, never null.
        val profiles = IntelliJSshConfigProvider.getProfiles()
        assertNotNull(profiles, "getProfiles() must never return null")
    }

    @Test
    fun `repeated calls return consistent results`() {
        // Calling multiple times should be safe and idempotent.
        val first = IntelliJSshConfigProvider.getProfiles()
        val second = IntelliJSshConfigProvider.getProfiles()
        assertEquals(first, second, "Consecutive calls should return the same result")
    }
}
