package ro.faur.explorer.quickopen.backend

import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files

/**
 * Loads the platform-specific nucleo JNI native library.
 * Library is bundled inside the plugin JAR under /natives/<platform>/.
 * Extracted to a temp file on first call; loaded with System.load().
 */
object NucleoNative {

    private val LOG = Logger.getInstance(NucleoNative::class.java)

    @Volatile private var loaded = false
    @Volatile private var loadError: String? = null

    /** True if the native library was successfully loaded. */
    val isAvailable: Boolean get() = loaded

    @JvmStatic external fun score(haystack: String, needle: String): Int
    @JvmStatic external fun matchIndices(haystack: String, needle: String): IntArray

    fun tryLoad() {
        if (loaded || loadError != null) return
        synchronized(this) {
            if (loaded || loadError != null) return
            try {
                val platform = detectPlatform()
                val libName = libName()
                val resource = "/natives/$platform/$libName"
                val stream = NucleoNative::class.java.getResourceAsStream(resource)
                    ?: throw IllegalStateException("Native lib not found: $resource")

                val tmpFile = Files.createTempFile("fuzzyjni", suffix(platform)).toFile()
                tmpFile.deleteOnExit()
                stream.use { it.copyTo(tmpFile.outputStream()) }
                System.load(tmpFile.absolutePath)
                loaded = true
                LOG.info("NucleoNative loaded for $platform")
            } catch (e: Exception) {
                loadError = e.message
                LOG.info("NucleoNative unavailable: ${e.message}")
            }
        }
    }

    private fun detectPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm")) -> "darwin-aarch64"
            os.contains("mac") -> "darwin-x86_64"
            os.contains("linux") -> "linux-x86_64"
            os.contains("win") -> "win32-x86_64"
            else -> throw IllegalStateException("Unsupported platform: os=$os arch=$arch")
        }
    }

    private fun libName() = when {
        System.getProperty("os.name").lowercase().contains("win") -> "fuzzyjni.dll"
        System.getProperty("os.name").lowercase().contains("mac") -> "libfuzzyjni.dylib"
        else -> "libfuzzyjni.so"
    }

    private fun suffix(platform: String) = when {
        platform.contains("win") -> ".dll"
        platform.contains("darwin") -> ".dylib"
        else -> ".so"
    }
}
