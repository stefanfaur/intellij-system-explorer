package ro.faur.explorer.actions

import ro.faur.explorer.settings.ExplorerSettings
import java.nio.file.Path
import java.nio.file.Paths

object NavigationActions {

    fun goToParent(currentPath: String): String {
        val path = Paths.get(currentPath)
        return path.parent?.toString() ?: currentPath
    }

    fun goHome(): String {
        val defaultRoot = ExplorerSettings.getInstance().state.defaultRoot
        return if (defaultRoot.isNotEmpty()) {
            defaultRoot
        } else {
            System.getProperty("user.home")
        }
    }

    fun goToRoot(): String {
        return "/"
    }

    class NavigationHistory {
        private val history = mutableListOf<String>()
        private var currentIndex = -1

        val canGoBack: Boolean
            get() = currentIndex > 0

        val canGoForward: Boolean
            get() = currentIndex < history.size - 1

        fun push(path: String) {
            // Truncate any forward history
            if (currentIndex < history.size - 1) {
                history.subList(currentIndex + 1, history.size).clear()
            }
            history.add(path)
            currentIndex = history.size - 1
        }

        fun back(): String? {
            if (!canGoBack) return null
            currentIndex--
            return history[currentIndex]
        }

        fun forward(): String? {
            if (!canGoForward) return null
            currentIndex++
            return history[currentIndex]
        }

        /**
         * Returns recent unique paths from the navigation history,
         * most recent first, capped at [limit] entries.
         * Only includes paths up to the current index (excludes forward-history entries).
         */
        fun getRecentPaths(limit: Int = 10): List<String> {
            val seen = linkedSetOf<String>()
            // Walk backwards from current position to oldest (exclude forward history)
            for (i in currentIndex downTo 0) {
                seen.add(history[i])
                if (seen.size >= limit) break
            }
            return seen.toList()
        }
    }
}
