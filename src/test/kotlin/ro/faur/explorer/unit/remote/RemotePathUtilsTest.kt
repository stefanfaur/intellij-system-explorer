package ro.faur.explorer.unit.remote

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import ro.faur.explorer.remote.RemotePathUtils

class RemotePathUtilsTest {

    @Test
    fun `parentPath of root returns root`() {
        assertEquals("/", RemotePathUtils.parentPath("/"))
    }

    @Test
    fun `parentPath of nested returns parent`() {
        assertEquals("/var/www", RemotePathUtils.parentPath("/var/www/html"))
    }

    @Test
    fun `parentPath of top-level dir returns root`() {
        assertEquals("/", RemotePathUtils.parentPath("/home"))
    }

    @Test
    fun `join combines paths correctly`() {
        assertEquals("/var/www/index.html", RemotePathUtils.join("/var/www", "index.html"))
    }

    @Test
    fun `join with trailing slash on parent`() {
        assertEquals("/var/www/index.html", RemotePathUtils.join("/var/www/", "index.html"))
    }

    @Test
    fun `fileName extracts last segment`() {
        assertEquals("app.yml", RemotePathUtils.fileName("/etc/config/app.yml"))
    }

    @Test
    fun `fileName of root returns empty`() {
        assertEquals("", RemotePathUtils.fileName("/"))
    }

    @Test
    fun `isSensitiveFile detects env files`() {
        assertTrue(RemotePathUtils.isSensitiveFile(".env"))
        assertTrue(RemotePathUtils.isSensitiveFile(".env.production"))
    }

    @Test
    fun `isSensitiveFile detects secret patterns`() {
        assertTrue(RemotePathUtils.isSensitiveFile("secrets.yml"))
        assertTrue(RemotePathUtils.isSensitiveFile("credentials.json"))
        assertTrue(RemotePathUtils.isSensitiveFile("password.txt"))
    }

    @Test
    fun `isSensitiveFile returns false for normal files`() {
        assertFalse(RemotePathUtils.isSensitiveFile("readme.md"))
        assertFalse(RemotePathUtils.isSensitiveFile("application.yml"))
        assertFalse(RemotePathUtils.isSensitiveFile("index.html"))
    }

    @Test
    fun `breadcrumbs splits remote path into segments`() {
        val crumbs = RemotePathUtils.breadcrumbs("/var/www/html")
        assertEquals(listOf("/", "var", "www", "html"), crumbs)
    }
}
