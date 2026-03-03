package ro.faur.explorer.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.JTextArea

object ExplorerErrorNotifier {

    private const val GROUP_ID = "Explorer.Errors"

    fun notify(project: Project?, title: String, message: String, throwable: Throwable? = null) {
        try {
            val details = formatDetails(message, throwable)
            val notification = NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP_ID)
                .createNotification(title, message, NotificationType.ERROR)

            if (throwable != null) {
                notification.addAction(object : com.intellij.openapi.actionSystem.AnAction("Show Details") {
                    override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                        DetailsDialog(title, details).show()
                    }
                })
            }

            notification.notify(project)
        } catch (_: Exception) {
            // Platform not available (e.g. unit test context) — notification silently skipped
        }
    }

    /** Pure function — safe to unit-test without platform. */
    fun formatDetails(message: String, throwable: Throwable?): String {
        if (throwable == null) return message
        return "$message\n\n${throwable.stackTraceToString()}"
    }

    private class DetailsDialog(title: String, private val details: String) : DialogWrapper(false) {
        init {
            this.title = title
            setOKButtonText("Close")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val area = JTextArea(details).apply {
                isEditable = false
                font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
                lineWrap = false
            }
            return JScrollPane(area).apply {
                preferredSize = Dimension(800, 400)
            }
        }

        override fun createActions() = arrayOf(okAction)
    }
}
