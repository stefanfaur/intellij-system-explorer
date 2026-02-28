package ro.faur.explorer.gitpanel.ui

import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.AsyncProcessIcon
import java.awt.BorderLayout
import javax.swing.JPanel
import com.intellij.openapi.project.Project

/**
 * A container slot that manages the lifecycle of an embedded DiffRequestPanel.
 * All public methods MUST be called on the EDT.
 */
class InlineDiffPanel(
    private val project: Project,
    private val parentDisposable: Disposable
) : JPanel(BorderLayout()) {

    private var currentChildDisposable: Disposable? = null

    /** Show a loading spinner while diff content is being fetched off-EDT. */
    fun showSpinner() {
        currentChildDisposable?.let {
            Disposer.dispose(it)
            currentChildDisposable = null
        }
        removeAll()
        val spinnerWrapper = JPanel(BorderLayout())
        val centerWrapper = JPanel()
        centerWrapper.add(AsyncProcessIcon("diff-loading"))
        spinnerWrapper.add(centerWrapper, BorderLayout.CENTER)
        add(spinnerWrapper, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    /**
     * Replace the current slot content with a DiffRequestPanel showing the given request.
     * The previous child disposable is disposed before creating the new one.
     */
    fun showDiffRequest(request: SimpleDiffRequest) {
        currentChildDisposable?.let {
            Disposer.dispose(it)
            currentChildDisposable = null
        }
        val childDisposable = Disposer.newDisposable(parentDisposable)
        currentChildDisposable = childDisposable

        val panel = DiffManager.getInstance().createRequestPanel(project, childDisposable, null)
        panel.setRequest(request)

        removeAll()
        add(panel.component, BorderLayout.CENTER)
        revalidate()
        repaint()
    }

    /** Dispose the current diff panel and show an empty slot. */
    fun clear() {
        currentChildDisposable?.let {
            Disposer.dispose(it)
            currentChildDisposable = null
        }
        removeAll()
        revalidate()
        repaint()
    }
}
