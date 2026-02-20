package ro.faur.explorer.unit

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RelativePathCopyTest {

    @Test
    fun `computeRelativePath returns relative path when absolutePath is under projectRoot`() {
        val projectRoot = "/home/user/myproject"
        val absolutePath = "/home/user/myproject/src/main/kotlin/Foo.kt"
        assertEquals("src/main/kotlin/Foo.kt", computeRelativePath(absolutePath, projectRoot))
    }

    @Test
    fun `computeRelativePath returns absolute path when outside projectRoot`() {
        val projectRoot = "/home/user/myproject"
        val absolutePath = "/etc/hosts"
        assertEquals("/etc/hosts", computeRelativePath(absolutePath, projectRoot))
    }

    @Test
    fun `computeRelativePath handles trailing slash on projectRoot`() {
        val projectRoot = "/home/user/myproject/"
        val absolutePath = "/home/user/myproject/build.gradle.kts"
        assertEquals("build.gradle.kts", computeRelativePath(absolutePath, projectRoot))
    }

    @Test
    fun `computeRelativePath returns same path when projectRoot is blank`() {
        val absolutePath = "/some/path/file.kt"
        assertEquals(absolutePath, computeRelativePath(absolutePath, ""))
    }

    private fun computeRelativePath(absolutePath: String, projectRoot: String): String {
        if (projectRoot.isBlank()) return absolutePath
        val root = projectRoot.trimEnd('/')
        return if (absolutePath.startsWith("$root/")) {
            absolutePath.removePrefix("$root/")
        } else {
            absolutePath
        }
    }
}
