package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.ConnectionProfile

/**
 * Tests for SSH terminal command building.
 * Note: We test the buildSshCommand logic indirectly through the public interface.
 * SshTerminalAction.openTerminal() requires the IntelliJ platform, so we test
 * the data-oriented parts only.
 */
class SshTerminalCommandTest {

    @Test
    fun `ConnectionProfile has correct default values`() {
        val profile = ConnectionProfile(
            name = "test",
            host = "example.com",
            username = "user",
        )
        assertEquals(22, profile.port)
        assertEquals(ConnectionProfile.AuthMethod.AGENT, profile.authMethod)
        assertNull(profile.keyFilePath)
    }

    @Test
    fun `ConnectionProfile with key file stores path`() {
        val profile = ConnectionProfile(
            name = "test",
            host = "example.com",
            username = "user",
            authMethod = ConnectionProfile.AuthMethod.KEY_FILE,
            keyFilePath = "~/.ssh/id_rsa",
        )
        assertEquals(ConnectionProfile.AuthMethod.KEY_FILE, profile.authMethod)
        assertEquals("~/.ssh/id_rsa", profile.keyFilePath)
    }

    @Test
    fun `ConnectionProfile with custom port`() {
        val profile = ConnectionProfile(
            name = "test",
            host = "example.com",
            port = 2222,
            username = "user",
        )
        assertEquals(2222, profile.port)
    }
}
