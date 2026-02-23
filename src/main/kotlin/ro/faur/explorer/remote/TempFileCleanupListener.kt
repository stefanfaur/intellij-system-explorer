package ro.faur.explorer.remote

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Cleans up orphaned temp files from previous IDE sessions on startup.
 *
 * The temp file directory (`~/.cache/system-explorer/temp/`) may contain
 * leftover files from sessions that crashed or were force-killed without
 * proper cleanup. This listener walks the directory on IDE startup and
 * removes any files/directories found, since they are no longer tracked
 * by any live [ro.faur.explorer.remote.security.SecureTempFileManager].
 *
 * Register in plugin.xml:
 * ```xml
 * <applicationListeners>
 *   <listener class="ro.faur.explorer.remote.TempFileCleanupListener"
 *             topic="com.intellij.ide.AppLifecycleListener"/>
 * </applicationListeners>
 * ```
 */
class TempFileCleanupListener : AppLifecycleListener {

    private val log = Logger.getInstance(TempFileCleanupListener::class.java)

    private val tempBaseDir: Path = Paths.get(
        System.getProperty("user.home"),
        ".cache", "system-explorer", "temp"
    )

    override fun appStarted() {
        if (!Files.isDirectory(tempBaseDir)) return

        try {
            var cleanedFiles = 0
            var cleanedDirs = 0

            // Walk bottom-up so we can delete directories after their contents
            Files.walk(tempBaseDir)
                .sorted(Comparator.reverseOrder())
                .forEach { path ->
                    if (path == tempBaseDir) return@forEach // keep the base dir
                    try {
                        if (Files.isRegularFile(path)) {
                            Files.deleteIfExists(path)
                            cleanedFiles++
                        } else if (Files.isDirectory(path)) {
                            // Only delete empty directories
                            Files.list(path).use { stream ->
                                if (stream.findFirst().isEmpty) {
                                    Files.deleteIfExists(path)
                                    cleanedDirs++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.debug("Could not clean up orphaned temp file: $path — ${e.message}")
                    }
                }

            if (cleanedFiles > 0 || cleanedDirs > 0) {
                log.info("Cleaned up $cleanedFiles orphaned temp files and $cleanedDirs empty directories from previous sessions")
            }
        } catch (e: Exception) {
            log.warn("Error during orphaned temp file cleanup", e)
        }
    }
}
