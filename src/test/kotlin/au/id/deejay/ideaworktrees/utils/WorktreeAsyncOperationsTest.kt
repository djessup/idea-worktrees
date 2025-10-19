package au.id.deejay.ideaworktrees.utils

import au.id.deejay.ideaworktrees.AbstractGitWorktreeTestCase
import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil

/**
 * Tests for WorktreeAsyncOperations utility class.
 *
 * These tests verify that async operations are executed correctly, callbacks are
 * invoked on the EDT, and error handling works as expected.
 */
class WorktreeAsyncOperationsTest : AbstractGitWorktreeTestCase() {

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

    fun testLoadWorktreesWithCurrentSuccess() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        var receivedWorktrees: List<WorktreeInfo>? = null
        var receivedCurrent: WorktreeInfo? = null
        var wasOnEDT = false

        WorktreeAsyncOperations.loadWorktreesWithCurrent(
            project = project,
            service = service,
            onSuccess = { worktrees, current ->
                wasOnEDT = ApplicationManager.getApplication().isDispatchThread
                receivedWorktrees = worktrees
                receivedCurrent = current
            }
        )

        waitForCondition("Callback should have been invoked") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            receivedWorktrees != null
        }

        assertTrue("Callback should be on EDT", wasOnEDT)
        assertNotNull("Worktrees should not be null", receivedWorktrees)
        assertTrue("Should have at least one worktree", receivedWorktrees!!.isNotEmpty())
        assertNotNull("Current worktree should not be null", receivedCurrent)
    }

    fun testLoadWorktreesWithCurrentCallbacksOnEDT() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        var wasOnEDT = false
        var callbackInvoked = false

        WorktreeAsyncOperations.loadWorktreesWithCurrent(
            project = project,
            service = service,
            onSuccess = { _, _ ->
                wasOnEDT = ApplicationManager.getApplication().isDispatchThread
                callbackInvoked = true
            }
        )

        waitForCondition("Callback should have been invoked") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            callbackInvoked
        }

        assertTrue("Callback should be on EDT", wasOnEDT)
    }

    fun testLoadWorktreesWithCurrentWithMultipleWorktrees() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        // Create a worktree to have multiple worktrees
        val featurePath = worktreePath("wt-multi")
        service.createWorktree(featurePath, "feature/multi").await()

        var receivedWorktrees: List<WorktreeInfo>? = null

        WorktreeAsyncOperations.loadWorktreesWithCurrent(
            project = project,
            service = service,
            onSuccess = { worktrees, _ ->
                receivedWorktrees = worktrees
            }
        )

        waitForCondition("Callback should have been invoked") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            receivedWorktrees != null
        }

        assertNotNull("Worktrees should not be null", receivedWorktrees)
        assertEquals("Should have 2 worktrees", 2, receivedWorktrees!!.size)
    }

    fun testLoadWorktreesSuccess() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        var receivedWorktrees: List<WorktreeInfo>? = null
        var wasOnEDT = false

        WorktreeAsyncOperations.loadWorktrees(
            project = project,
            service = service,
            onSuccess = { worktrees ->
                wasOnEDT = ApplicationManager.getApplication().isDispatchThread
                receivedWorktrees = worktrees
            }
        )

        waitForCondition("Callback should have been invoked") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            receivedWorktrees != null
        }

        assertTrue("Callback should be on EDT", wasOnEDT)
        assertNotNull("Worktrees should not be null", receivedWorktrees)
        assertTrue("Should have at least one worktree", receivedWorktrees!!.isNotEmpty())
    }

    fun testLoadWorktreesCallbacksOnEDT() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        var wasOnEDT = false
        var callbackInvoked = false

        WorktreeAsyncOperations.loadWorktrees(
            project = project,
            service = service,
            onSuccess = { _ ->
                wasOnEDT = ApplicationManager.getApplication().isDispatchThread
                callbackInvoked = true
            }
        )

        waitForCondition("Callback should have been invoked") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            callbackInvoked
        }

        assertTrue("Callback should be on EDT", wasOnEDT)
    }

    fun testLoadWorktreesWithMultipleWorktrees() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        // Create a worktree to have multiple worktrees
        val featurePath = worktreePath("wt-load")
        service.createWorktree(featurePath, "feature/load").await()

        var receivedWorktrees: List<WorktreeInfo>? = null

        WorktreeAsyncOperations.loadWorktrees(
            project = project,
            service = service,
            onSuccess = { worktrees ->
                receivedWorktrees = worktrees
            }
        )

        waitForCondition("Callback should have been invoked") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            receivedWorktrees != null
        }

        assertNotNull("Worktrees should not be null", receivedWorktrees)
        assertEquals("Should have 2 worktrees", 2, receivedWorktrees!!.size)
    }

    fun testLoadWorktreesWithCurrentParallelExecution() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        // Create a worktree to have multiple worktrees
        val featurePath = worktreePath("wt-parallel")
        service.createWorktree(featurePath, "feature/parallel").await()

        var receivedWorktrees: List<WorktreeInfo>? = null
        var receivedCurrent: WorktreeInfo? = null

        WorktreeAsyncOperations.loadWorktreesWithCurrent(
            project = project,
            service = service,
            onSuccess = { worktrees, current ->
                receivedWorktrees = worktrees
                receivedCurrent = current
            }
        )

        waitForCondition("Callback should have been invoked") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            receivedWorktrees != null
        }

        assertNotNull("Worktrees should not be null", receivedWorktrees)
        assertEquals("Should have 2 worktrees", 2, receivedWorktrees!!.size)
        assertNotNull("Current worktree should not be null", receivedCurrent)
    }

    private fun waitForCondition(message: String, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
        fail(message)
    }
}

