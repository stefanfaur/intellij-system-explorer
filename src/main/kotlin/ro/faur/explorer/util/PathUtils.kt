package ro.faur.explorer.util

import java.nio.file.Paths

data class Breadcrumb(val name: String, val path: String)

object PathUtils {

    fun userHome(): String =
        System.getProperty("user.home")
            ?: System.getenv("HOME")
            ?: "/"

    fun desktopPath(): String = userHome() + "/Desktop"

    fun downloadsPath(): String = userHome() + "/Downloads"

    fun parentPath(path: String): String {
        val parent = Paths.get(path).parent
        return parent?.toString() ?: "/"
    }

    fun breadcrumbs(path: String): List<Breadcrumb> {
        if (path == "/") {
            return listOf(Breadcrumb("/", "/"))
        }

        val result = mutableListOf(Breadcrumb("/", "/"))
        val nioPath = Paths.get(path)

        for (i in 0 until nioPath.nameCount) {
            val segment = nioPath.getName(i).toString()
            val fullPath = "/" + (0..i).joinToString("/") { nioPath.getName(it).toString() }
            result.add(Breadcrumb(segment, fullPath))
        }

        return result
    }

    fun isHidden(name: String): Boolean {
        return name.startsWith(".") && name != "." && name != ".."
    }
}
