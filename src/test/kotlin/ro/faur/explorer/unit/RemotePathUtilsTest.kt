package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import ro.faur.explorer.remote.RemotePathUtils

class RemotePathUtilsTest {
    @Test fun `join produces correct path`() {
        assertEquals("/var/www/foo.txt", RemotePathUtils.join("/var/www", "foo.txt"))
    }
    @Test fun `join rejects path traversal`() {
        assertThrows(IllegalArgumentException::class.java) {
            RemotePathUtils.join("/var/www", "../etc/passwd")
        }
    }
    @Test fun `join handles trailing slash on parent`() {
        assertEquals("/var/www/foo.txt", RemotePathUtils.join("/var/www/", "foo.txt"))
    }
}
