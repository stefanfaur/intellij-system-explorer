package ro.faur.explorer.gitpanel

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Project-level service that broadcasts browser navigation events so the Git panel
 * can auto-select the backend matching the currently active browser tab/path.
 *
 * Browser panels call [reportNavigation] on every navigation.
 * [GitPanelComponent] subscribes via [addListener].
 */
@Service(Service.Level.PROJECT)
class ActiveBrowserTracker : Disposable {

    private val listeners = CopyOnWriteArrayList<(connectionName: String?, path: String) -> Unit>()

    /** Called by browser panels whenever the current path changes or a tab becomes active. */
    fun reportNavigation(connectionName: String?, path: String) {
        listeners.forEach { it(connectionName, path) }
    }

    fun addListener(l: (String?, String) -> Unit) { listeners.add(l) }
    fun removeListener(l: (String?, String) -> Unit) { listeners.remove(l) }

    override fun dispose() { listeners.clear() }

    companion object {
        fun getInstance(project: Project): ActiveBrowserTracker =
            project.getService(ActiveBrowserTracker::class.java)
    }
}
