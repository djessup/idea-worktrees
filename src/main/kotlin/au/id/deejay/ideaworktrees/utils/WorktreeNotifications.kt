package au.id.deejay.ideaworktrees.utils

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Centralized utility for creating and displaying notifications related to Git worktree operations.
 *
 * This object provides a single source of truth for all notification creation in the plugin,
 * ensuring consistent notification behavior and making it easy to modify notification logic
 * across the entire plugin.
 *
 * All methods are thread-safe and can be called from any thread. The IntelliJ platform
 * automatically handles EDT marshalling for notification display.
 *
 * Usage example:
 * ```kotlin
 * WorktreeNotifications.showSuccess(project, "Worktree Created", "Successfully created worktree at /path/to/worktree")
 * WorktreeNotifications.showErrorWithDetails(project, "Failed to Create Worktree", "Branch already exists", gitOutput)
 * ```
 */
object WorktreeNotifications {
    /**
     * The notification group ID registered in plugin.xml.
     * This is the single source of truth for the notification group identifier.
     */
    private const val NOTIFICATION_GROUP_ID = "Git Worktree"

    /**
     * Shows a success notification with the specified title and message.
     *
     * @param project The project context for the notification
     * @param title The notification title
     * @param message The notification message
     */
    fun showSuccess(project: Project, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, message, NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * Shows an error notification with the specified title and message.
     *
     * @param project The project context for the notification
     * @param title The notification title
     * @param message The notification message
     */
    fun showError(project: Project, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, message, NotificationType.ERROR)
            .notify(project)
    }

    /**
     * Shows a warning notification with the specified title and message.
     *
     * @param project The project context for the notification
     * @param title The notification title
     * @param message The notification message
     */
    fun showWarning(project: Project, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, message, NotificationType.WARNING)
            .notify(project)
    }

    /**
     * Shows an informational notification with the specified title and message.
     *
     * @param project The project context for the notification
     * @param title The notification title
     * @param message The notification message
     */
    fun showInfo(project: Project, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, message, NotificationType.INFORMATION)
            .notify(project)
    }

    /**
     * Shows an error notification with optional technical details.
     *
     * If details are provided (non-null and non-blank), they are appended to the error message
     * with the format: "error\n\nDetails: details"
     *
     * @param project The project context for the notification
     * @param title The notification title
     * @param error The main error message
     * @param details Optional technical details (e.g., Git command output, stack trace)
     */
    fun showErrorWithDetails(project: Project, title: String, error: String, details: String?) {
        val fullMessage = if (!details.isNullOrBlank()) {
            "$error\n\nDetails: $details"
        } else {
            error
        }

        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, fullMessage, NotificationType.ERROR)
            .notify(project)
    }
}

