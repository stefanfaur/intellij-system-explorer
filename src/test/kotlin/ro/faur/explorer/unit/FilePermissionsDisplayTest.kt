package ro.faur.explorer.unit

import com.intellij.openapi.util.SystemInfo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * Tests file permissions formatting logic.
 *
 * Verifies that:
 * - Permissions are formatted correctly on Unix/Mac (drwxr-xr-x format)
 * - The formatPermissions helper produces expected output for various permission sets
 * - Edge cases are handled (Windows detection, errors, etc.)
 *
 * Note: Cell renderer integration is tested via light tests that have access to VirtualFile mocks.
 */
class FilePermissionsDisplayTest {

    /**
     * Helper method to format permissions similar to FileTreeComponent.
     * This duplicates the logic to test it in isolation.
     */
    private fun formatPermissions(path: String, isDirectory: Boolean): String {
        if (SystemInfo.isWindows) return ""

        try {
            val p = java.nio.file.Paths.get(path)
            val perms = Files.getPosixFilePermissions(p)
            val permString = PosixFilePermissions.toString(perms)
            val prefix = if (isDirectory) "d" else "-"
            return "$prefix$permString"
        } catch (e: Exception) {
            return ""
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `formatPermissions returns correct format for directory with rwxr-xr-x`() {
        // Create a temporary directory with known permissions
        val tempDir = Files.createTempDirectory("test-perms")
        try {
            // Set permissions: rwxr-xr-x (755)
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_EXECUTE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_EXECUTE
            )
            Files.setPosixFilePermissions(tempDir, perms)

            val result = formatPermissions(tempDir.toString(), isDirectory = true)
            assertEquals("drwxr-xr-x", result, "Directory permissions should be formatted as drwxr-xr-x")
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `formatPermissions returns correct format for file with rw-r--r--`() {
        // Create a temporary file with known permissions
        val tempFile = Files.createTempFile("test-perms", ".txt")
        try {
            // Set permissions: rw-r--r-- (644)
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.OTHERS_READ
            )
            Files.setPosixFilePermissions(tempFile, perms)

            val result = formatPermissions(tempFile.toString(), isDirectory = false)
            assertEquals("-rw-r--r--", result, "File permissions should be formatted as -rw-r--r--")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `formatPermissions returns correct format for file with rwx------`() {
        // Create a temporary file with owner-only permissions
        val tempFile = Files.createTempFile("test-perms", ".txt")
        try {
            // Set permissions: rwx------ (700)
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE
            )
            Files.setPosixFilePermissions(tempFile, perms)

            val result = formatPermissions(tempFile.toString(), isDirectory = false)
            assertEquals("-rwx------", result, "File permissions should be formatted as -rwx------")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `formatPermissions returns correct format for read-only file`() {
        // Create a temporary file with read-only permissions
        val tempFile = Files.createTempFile("test-perms", ".txt")
        try {
            // Set permissions: r-------- (400)
            val perms = setOf(PosixFilePermission.OWNER_READ)
            Files.setPosixFilePermissions(tempFile, perms)

            val result = formatPermissions(tempFile.toString(), isDirectory = false)
            assertEquals("-r--------", result, "File permissions should be formatted as -r--------")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `formatPermissions returns correct format for world-writable file`() {
        // Create a temporary file with world-writable permissions
        val tempFile = Files.createTempFile("test-perms", ".txt")
        try {
            // Set permissions: rw-rw-rw- (666)
            val perms = setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.GROUP_READ,
                PosixFilePermission.GROUP_WRITE,
                PosixFilePermission.OTHERS_READ,
                PosixFilePermission.OTHERS_WRITE
            )
            Files.setPosixFilePermissions(tempFile, perms)

            val result = formatPermissions(tempFile.toString(), isDirectory = false)
            assertEquals("-rw-rw-rw-", result, "File permissions should be formatted as -rw-rw-rw-")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    fun `formatPermissions returns empty string on Windows`() {
        // On Windows, permissions should always be empty
        val result = formatPermissions("C:\\test\\file.txt", isDirectory = false)
        assertEquals("", result, "Permissions should be empty on Windows")
    }

    @Test
    fun `formatPermissions returns empty string for nonexistent path`() {
        // Should fail silently and return empty string
        val result = formatPermissions("/nonexistent/path/that/does/not/exist", isDirectory = false)
        assertEquals("", result, "Permissions should be empty for nonexistent path")
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `formatPermissions prefix is d for directories and dash for files`() {
        val tempDir = Files.createTempDirectory("test-dir")
        val tempFile = Files.createTempFile("test-file", ".txt")
        try {
            val dirResult = formatPermissions(tempDir.toString(), isDirectory = true)
            val fileResult = formatPermissions(tempFile.toString(), isDirectory = false)

            assertTrue(dirResult.startsWith("d"), "Directory permissions should start with 'd'")
            assertTrue(fileResult.startsWith("-"), "File permissions should start with '-'")
        } finally {
            tempDir.toFile().deleteRecursively()
            Files.deleteIfExists(tempFile)
        }
    }

    @Test
    @EnabledOnOs(OS.LINUX, OS.MAC)
    fun `formatPermissions returns 10 character string on success`() {
        val tempFile = Files.createTempFile("test-length", ".txt")
        try {
            val result = formatPermissions(tempFile.toString(), isDirectory = false)
            assertEquals(10, result.length, "Permissions string should be exactly 10 characters (1 prefix + 9 permission chars)")
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }
}
