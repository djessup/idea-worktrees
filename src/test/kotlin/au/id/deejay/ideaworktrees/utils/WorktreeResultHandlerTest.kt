package au.id.deejay.ideaworktrees.utils

import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.nio.file.Paths

/**
 * Tests for WorktreeResultHandler utility class.
 *
 * These tests verify that WorktreeOperationResult instances are handled correctly,
 * with appropriate notifications and user prompts.
 */
@RunWith(JUnit4::class)
class WorktreeResultHandlerTest : BasePlatformTestCase() {

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
    fun testHandleSuccessWithoutPrompt() {
        val result = WorktreeOperationResult.Success("Worktree created successfully")

        WorktreeResultHandler.handle(
            project = project,
            result = result,
            successTitle = "Worktree Created"
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Worktree Created", notification.title)
        assertEquals("Worktree created successfully", notification.content)
        assertEquals(NotificationType.INFORMATION, notification.type)
    }

    @Test
    fun testHandleSuccessWithDetails() {
        val result = WorktreeOperationResult.Success(
            message = "Worktree created",
            details = "Additional info"
        )

        WorktreeResultHandler.handle(
            project = project,
            result = result,
            successTitle = "Success"
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Success", notification.title)
        assertEquals("Worktree created", notification.content)
    }

    @Test
    fun testHandleFailureWithoutDetails() {
        val result = WorktreeOperationResult.Failure("Branch already exists")

        WorktreeResultHandler.handle(
            project = project,
            result = result,
            errorTitle = "Failed to Create Worktree"
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Failed to Create Worktree", notification.title)
        assertEquals("Branch already exists", notification.content)
        assertEquals(NotificationType.ERROR, notification.type)
    }

    @Test
    fun testHandleFailureWithDetails() {
        val result = WorktreeOperationResult.Failure(
            error = "Branch already exists",
            details = "fatal: branch 'feature' already exists"
        )

        WorktreeResultHandler.handle(
            project = project,
            result = result,
            errorTitle = "Failed to Create Worktree"
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Failed to Create Worktree", notification.title)
        assertTrue(notification.content.contains("Branch already exists"))
        assertTrue(notification.content.contains("Details:"))
        assertTrue(notification.content.contains("fatal: branch 'feature' already exists"))
        assertEquals(NotificationType.ERROR, notification.type)
    }

    @Test
    fun testHandleRequiresInitialCommitWithCustomHandler() {
        val result = WorktreeOperationResult.RequiresInitialCommit("Repository has no commits")
        var handlerCalled = false
        var receivedResult: WorktreeOperationResult.RequiresInitialCommit? = null

        WorktreeResultHandler.handle(
            project = project,
            result = result,
            onInitialCommitRequired = { r ->
                handlerCalled = true
                receivedResult = r
            }
        )

        assertTrue(handlerCalled)
        assertEquals(result, receivedResult)
        assertEquals(0, capturedNotifications.size) // Custom handler, no default notification
    }

    @Test
    fun testHandleRequiresInitialCommitWithoutCustomHandler() {
        val result = WorktreeOperationResult.RequiresInitialCommit("Repository has no commits")

        WorktreeResultHandler.handle(
            project = project,
            result = result
        )

        assertEquals(1, capturedNotifications.size)
        val notification = capturedNotifications[0]
        assertEquals("Initial Commit Required", notification.title)
        assertEquals("Repository has no commits", notification.content)
        assertEquals(NotificationType.INFORMATION, notification.type)
    }

    @Test
    fun testHandleWithDefaultTitles() {
        val successResult = WorktreeOperationResult.Success("Success message")

        WorktreeResultHandler.handle(
            project = project,
            result = successResult
        )

        assertEquals(1, capturedNotifications.size)
        assertEquals("Operation Successful", capturedNotifications[0].title)

        capturedNotifications.clear()

        val failureResult = WorktreeOperationResult.Failure("Error message")

        WorktreeResultHandler.handle(
            project = project,
            result = failureResult
        )

        assertEquals(1, capturedNotifications.size)
        assertEquals("Operation Failed", capturedNotifications[0].title)
    }

    @Test
    fun testValidationThrowsWhenPromptToOpenWithoutPath() {
        val result = WorktreeOperationResult.Success("Success")

        try {
            WorktreeResultHandler.handle(
                project = project,
                result = result,
                promptToOpen = true,
                worktreePath = null
            )
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("worktreePath must be provided") == true)
        }
    }

    @Test
    fun testHandleSuccessWithPromptToOpenDoesNotThrowValidationError() {
        val result = WorktreeOperationResult.Success("Worktree created")
        val path = Paths.get("/tmp/test-worktree")

        // This test verifies that the method doesn't throw IllegalArgumentException
        // when promptToOpen is true and worktreePath is provided.
        // The dialog interaction will happen but we're only testing validation here.
        var validationPassed = false
        try {
            WorktreeResultHandler.handle(
                project = project,
                result = result,
                successTitle = "Created",
                promptToOpen = true,
                worktreePath = path
            )
            validationPassed = true
        } catch (e: IllegalArgumentException) {
            fail("Should not throw IllegalArgumentException when worktreePath is provided: ${e.message}")
        } catch (e: Exception) {
            // Other exceptions (like dialog-related) are acceptable in this test
            // We're only testing that validation passes
            validationPassed = true
        }

        assertTrue("Validation should have passed", validationPassed)
    }

    @Test
    fun testMultipleResultsHandledSequentially() {
        val success = WorktreeOperationResult.Success("Success 1")
        val failure = WorktreeOperationResult.Failure("Error 1")
        val requiresCommit = WorktreeOperationResult.RequiresInitialCommit("Need commit")

        WorktreeResultHandler.handle(project, success, successTitle = "S1")
        WorktreeResultHandler.handle(project, failure, errorTitle = "E1")
        WorktreeResultHandler.handle(project, requiresCommit)

        assertEquals(3, capturedNotifications.size)
        assertEquals("S1", capturedNotifications[0].title)
        assertEquals("E1", capturedNotifications[1].title)
        assertEquals("Initial Commit Required", capturedNotifications[2].title)
    }
}

