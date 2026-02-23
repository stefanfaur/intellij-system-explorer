package ro.faur.explorer.sftp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import ro.faur.explorer.remote.SshConfigParser
import java.nio.file.Files
import java.nio.file.Path

class SshConfigParsingTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("sshconfig-test")
    }

    @Test
    fun `parses basic host entry`() {
        val configFile = tempDir.resolve("config")
        Files.writeString(configFile, """
            Host myserver
              HostName example.com
              Port 2222
              User deploy
        """.trimIndent())
        val entries = SshConfigParser.parse(configFile)
        assertEquals(1, entries.size)
        assertEquals("myserver", entries[0].alias)
        assertEquals("example.com", entries[0].hostName)
        assertEquals(2222, entries[0].port)
        assertEquals("deploy", entries[0].username)
    }

    @Test
    fun `parses multiple host entries`() {
        val configFile = tempDir.resolve("config")
        Files.writeString(configFile, """
            Host prod
              HostName prod.example.com
              User admin

            Host uat
              HostName uat.example.com
              User tester
        """.trimIndent())
        val entries = SshConfigParser.parse(configFile)
        assertEquals(2, entries.size)
    }

    @Test
    fun `parses ProxyJump directive`() {
        val configFile = tempDir.resolve("config")
        Files.writeString(configFile, """
            Host target
              HostName target.internal
              ProxyJump bastion

            Host bastion
              HostName bastion.example.com
        """.trimIndent())
        val entries = SshConfigParser.parse(configFile)
        val target = entries.find { it.alias == "target" }
        assertNotNull(target)
        assertEquals("bastion", target!!.proxyJump)
    }

    @Test
    fun `parses IdentityFile directive`() {
        val configFile = tempDir.resolve("config")
        Files.writeString(configFile, """
            Host secured
              HostName secure.example.com
              IdentityFile ~/.ssh/id_ed25519
        """.trimIndent())
        val entries = SshConfigParser.parse(configFile)
        assertEquals("~/.ssh/id_ed25519", entries[0].identityFile)
    }

    @Test
    fun `uses default port 22 when not specified`() {
        val configFile = tempDir.resolve("config")
        Files.writeString(configFile, """
            Host simple
              HostName simple.example.com
        """.trimIndent())
        val entries = SshConfigParser.parse(configFile)
        assertEquals(22, entries[0].port)
    }

    @Test
    fun `empty config file returns empty list`() {
        val configFile = tempDir.resolve("config")
        Files.writeString(configFile, "")
        val entries = SshConfigParser.parse(configFile)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `nonexistent config file returns empty list`() {
        val entries = SshConfigParser.parse(tempDir.resolve("nonexistent"))
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `ignores wildcard Host entries`() {
        val configFile = tempDir.resolve("config")
        Files.writeString(configFile, """
            Host *
              ServerAliveInterval 60

            Host myserver
              HostName example.com
        """.trimIndent())
        val entries = SshConfigParser.parse(configFile)
        assertEquals(1, entries.size)
        assertEquals("myserver", entries[0].alias)
    }
}
