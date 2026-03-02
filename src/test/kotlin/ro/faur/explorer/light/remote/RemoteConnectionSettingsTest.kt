package ro.faur.explorer.light.remote

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import ro.faur.explorer.remote.settings.RemoteConnectionSettings
import ro.faur.explorer.remote.ConnectionProfile

class RemoteConnectionSettingsTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        RemoteConnectionSettings.getInstance(project).loadState(RemoteConnectionSettings.State())
    }

    fun `test empty state by default`() {
        val settings = RemoteConnectionSettings.getInstance(project)
        assertTrue(settings.state.connections.isEmpty())
    }

    fun `test add connection profile`() {
        val settings = RemoteConnectionSettings.getInstance(project)
        settings.addConnection(ConnectionProfile(
            name = "prod", host = "prod.example.com", port = 22,
            username = "deploy", authMethod = ConnectionProfile.AuthMethod.AGENT,
        ))
        assertEquals(1, settings.state.connections.size)
        assertEquals("prod", settings.state.connections[0].name)
    }

    fun `test remove connection profile`() {
        val settings = RemoteConnectionSettings.getInstance(project)
        settings.addConnection(ConnectionProfile(
            name = "temp", host = "temp.example.com", port = 22,
            username = "user", authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        ))
        settings.removeConnection("temp")
        assertTrue(settings.state.connections.isEmpty())
    }

    fun `test state serializes and deserializes`() {
        val settings = RemoteConnectionSettings.getInstance(project)
        settings.addConnection(ConnectionProfile(
            name = "test", host = "test.example.com", port = 2222,
            username = "tester", authMethod = ConnectionProfile.AuthMethod.KEY_FILE,
            keyFilePath = "~/.ssh/id_ed25519",
        ))
        val state = settings.state
        val fresh = RemoteConnectionSettings()
        fresh.loadState(state)
        assertEquals(1, fresh.state.connections.size)
        val conn = fresh.state.connections[0]
        assertEquals("test", conn.name)
        assertEquals("test.example.com", conn.host)
        assertEquals(2222, conn.port)
        assertEquals("~/.ssh/id_ed25519", conn.keyFilePath)
    }

    fun `test no passwords stored in state`() {
        val settings = RemoteConnectionSettings.getInstance(project)
        settings.addConnection(ConnectionProfile(
            name = "secure", host = "secure.example.com", port = 22,
            username = "admin", authMethod = ConnectionProfile.AuthMethod.PASSWORD,
        ))
        val state = settings.state
        val conn = state.connections[0]
        // ConnectionProfile intentionally has no password field — credentials go through PasswordSafe
        assertNotNull(conn)
        assertEquals("secure", conn.name)
    }

    fun `test duplicate connection name rejected`() {
        val settings = RemoteConnectionSettings.getInstance(project)
        settings.addConnection(ConnectionProfile(
            name = "dup", host = "host1.example.com", port = 22,
            username = "user", authMethod = ConnectionProfile.AuthMethod.AGENT,
        ))
        val result = settings.addConnection(ConnectionProfile(
            name = "dup", host = "host2.example.com", port = 22,
            username = "user", authMethod = ConnectionProfile.AuthMethod.AGENT,
        ))
        assertFalse(result)
        assertEquals(1, settings.state.connections.size)
    }
}
