package ro.faur.explorer.shortcuts

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Shows brief notifications for chord-related events.
 * 
 * Uses IntelliJ's notification system for non-intrusive feedback.
 */
object ChordToast {

    private const val NOTIFICATION_GROUP = "System Explorer.Shortcuts"

    /**
     * Shows a toast when a chord is pressed but no action exists for the current context.
     */
    fun showNoActionForContext() {
        showNotification(
            title = "No action for this context",
            content = "The pressed shortcut is not available in the current panel.",
            notificationType = NotificationType.INFORMATION
        )
    }

    /**
     * Shows a toast when an invalid chord sequence is entered.
     */
    fun showInvalidChord() {
        showNotification(
            title = "Unknown shortcut",
            content = "Press backtick followed by a valid action key.",
            notificationType = NotificationType.INFORMATION
        )
    }

    /**
     * Shows a toast when a chord action fails.
     */
    fun showActionFailed(actionName: String, reason: String) {
        showNotification(
            title = "Action failed: $actionName",
            content = reason,
            notificationType = NotificationType.WARNING
        )
    }

    /**
     * Shows a toast for a successful action.
     */
    fun showActionSuccess(actionName: String) {
        showNotification(
            title = actionName,
            content = "",
            notificationType = NotificationType.INFORMATION
        )
    }

    /**
     * Shows an informational toast.
     */
    fun showInfo(message: String) {
        showNotification(
            title = "System Explorer",
            content = message,
            notificationType = NotificationType.INFORMATION
        )
    }

    fun showNotification(
        title: String,
        content: String,
        notificationType: NotificationType
    ) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                ?.createNotification(title, content, notificationType)
                ?.notify(null)
        } catch (e: Exception) {
            // Fallback: notification system may not be available in tests
            System.err.println("[ChordToast] $title: $content")
        }
    }
}
