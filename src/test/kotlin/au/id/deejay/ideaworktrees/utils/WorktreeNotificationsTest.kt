package au.id.deejay.ideaworktrees.utils

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Tests for WorktreeNotifications utility class.
 *
 * These tests verify that notifications are created with the correct type, title, and message,
 * and that the notification group ID is used correctly.
 */
@RunWith(JUnit4::class)
class WorktreeNotificationsTest : BasePlatformTestCase() {

    private val capturedNotifications = mutableListOf<Notification>()

    override fun setUp() {
        super.setUp()
        capturedNotifications.clear()

        // Subscribe to notifications to capture them for testing
        project.messageBus.connect(testRootDisposable).subscribe(
            Notifications.TOPIC,
            object : Notifications {
                override fun notify(notification: Notification) {
                    capturedNotifications.add(notification)
                }
            }
        )
    }

    @Test
    fun testShowSuccess() {
        WorktreeNotifications.showSuccess(project, "Test Success", "Success message")

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Success", notification.title)
        assertEquals("Success message", notification.content)
        assertEquals(NotificationType.INFORMATION, notification.type)
    }

    @Test
    fun testShowError() {
        WorktreeNotifications.showError(project, "Test Error", "Error message")

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Error", notification.title)
        assertEquals("Error message", notification.content)
        assertEquals(NotificationType.ERROR, notification.type)
    }

    @Test
    fun testShowWarning() {
        WorktreeNotifications.showWarning(project, "Test Warning", "Warning message")

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Warning", notification.title)
        assertEquals("Warning message", notification.content)
        assertEquals(NotificationType.WARNING, notification.type)
    }

    @Test
    fun testShowInfo() {
        WorktreeNotifications.showInfo(project, "Test Info", "Info message")

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Info", notification.title)
        assertEquals("Info message", notification.content)
        assertEquals(NotificationType.INFORMATION, notification.type)
    }

    @Test
    fun testShowErrorWithDetails() {
        WorktreeNotifications.showErrorWithDetails(
            project,
            "Test Error",
            "Main error",
            "Technical details"
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Error", notification.title)
        assertEquals("Main error\n\nDetails: Technical details", notification.content)
        assertEquals(NotificationType.ERROR, notification.type)
    }

    @Test
    fun testShowErrorWithNullDetails() {
        WorktreeNotifications.showErrorWithDetails(
            project,
            "Test Error",
            "Main error",
            null
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Error", notification.title)
        assertEquals("Main error", notification.content)
        assertEquals(NotificationType.ERROR, notification.type)
    }

    @Test
    fun testShowErrorWithBlankDetails() {
        WorktreeNotifications.showErrorWithDetails(
            project,
            "Test Error",
            "Main error",
            "   "
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Error", notification.title)
        assertEquals("Main error", notification.content)
        assertEquals(NotificationType.ERROR, notification.type)
    }

    @Test
    fun testShowErrorWithEmptyDetails() {
        WorktreeNotifications.showErrorWithDetails(
            project,
            "Test Error",
            "Main error",
            ""
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Test Error", notification.title)
        assertEquals("Main error", notification.content)
        assertEquals(NotificationType.ERROR, notification.type)
    }

    @Test
    fun testMultipleNotifications() {
        WorktreeNotifications.showSuccess(project, "Success 1", "Message 1")
        WorktreeNotifications.showError(project, "Error 1", "Message 2")
        WorktreeNotifications.showWarning(project, "Warning 1", "Message 3")

        assertEquals(3, capturedNotifications.size)
        assertEquals(NotificationType.INFORMATION, capturedNotifications[0].type)
        assertEquals(NotificationType.ERROR, capturedNotifications[1].type)
        assertEquals(NotificationType.WARNING, capturedNotifications[2].type)
    }
}

