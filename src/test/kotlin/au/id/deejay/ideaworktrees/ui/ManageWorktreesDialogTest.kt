package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.AbstractGitWorktreeTestCase
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil

/**
 * UI-level tests validating the manage worktrees dialog refresh behaviour.
 */
class ManageWorktreesDialogTest : AbstractGitWorktreeTestCase() {

    /**
     * Confirms the dialog loads worktrees on initialization without manual refresh.
     */
    fun testDialogLoadsWorktreesAutomaticallyOnOpen() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        val featurePath = worktreePath("wt-dialog-auto")
        service.createWorktree(featurePath, "feature/dialog-auto").await()

        lateinit var dialog: ManageWorktreesDialog
        ApplicationManager.getApplication().invokeAndWait {
            dialog = ManageWorktreesDialog(project, service)
        }

        // The dialog should automatically load worktrees without requiring manual refresh
        waitForWorktreeCount(dialog, expected = 2)

        val snapshot = dialog.snapshotWorktrees()
        assertEquals("Dialog should auto-load 2 worktrees on open", 2, snapshot.size)
        assertTrue("Should contain feature worktree", snapshot.any { normalizePath(it.path) == normalizePath(featurePath) })

        val current = dialog.currentWorktreeForTest()
        assertNotNull("Current worktree should be identified", current)
        assertEquals(normalizePath(projectPath), normalizePath(current!!.path))

        val disposable = dialog.disposable
        ApplicationManager.getApplication().invokeAndWait {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
        Disposer.dispose(disposable)
    }

    /**
     * Verifies the dialog marks the project worktree as the current entry.
     */
    fun testDialogLoadsWorktreesAndMarksCurrent() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        val featurePath = worktreePath("wt-dialog")
        service.createWorktree(featurePath, "feature/dialog").await()

        lateinit var dialog: ManageWorktreesDialog
        ApplicationManager.getApplication().invokeAndWait {
            dialog = ManageWorktreesDialog(project, service)
        }

        waitForWorktreeCount(dialog, expected = 2)

        val snapshot = dialog.snapshotWorktrees()
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.any { normalizePath(it.path) == normalizePath(featurePath) })

        val current = dialog.currentWorktreeForTest()
        assertNotNull("Current worktree should point at the project root", current)
        assertEquals(normalizePath(projectPath), normalizePath(current!!.path))

        val disposable = dialog.disposable
        ApplicationManager.getApplication().invokeAndWait {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
        Disposer.dispose(disposable)
    }

    /**
     * Ensures deleting a worktree triggers the dialog to refresh its table contents.
     */
    fun testDialogRefreshAfterWorktreeDeletion() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        val featurePath = worktreePath("wt-remove-dialog")
        service.createWorktree(featurePath, "feature/remove").await()

        lateinit var dialog: ManageWorktreesDialog
        ApplicationManager.getApplication().invokeAndWait {
            dialog = ManageWorktreesDialog(project, service)
        }
        waitForWorktreeCount(dialog, expected = 2)

        service.deleteWorktree(featurePath, force = true).await()

        ApplicationManager.getApplication().invokeAndWait {
            dialog.refreshForTest()
        }
        waitForWorktreeCount(dialog, expected = 1)

        assertTrue(dialog.snapshotWorktrees().none { normalizePath(it.path) == normalizePath(featurePath) })

        val disposable = dialog.disposable
        ApplicationManager.getApplication().invokeAndWait {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
        Disposer.dispose(disposable)
    }

    /**
     * Checks creating a worktree through the test hook refreshes the table.
     */
    fun testDialogCreateWorktreeUpdatesTable() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        lateinit var dialog: ManageWorktreesDialog
        ApplicationManager.getApplication().invokeAndWait {
            dialog = ManageWorktreesDialog(project, service)
        }
        waitForWorktreeCount(dialog, expected = 1)

        val newWorktreePath = worktreePath("wt-create-dialog")
        val result = dialog.createWorktreeForTest(newWorktreePath, "feature/dialog-create").await()
        assertTrue(
            "Expected worktree creation to succeed but was $result",
            result is WorktreeOperationResult.Success
        )

        waitForWorktreeCount(dialog, expected = 2)
        assertTrue(
            "Should contain newly created worktree",
            dialog.snapshotWorktrees().any { normalizePath(it.path) == normalizePath(newWorktreePath) }
        )

        val disposable = dialog.disposable
        ApplicationManager.getApplication().invokeAndWait {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
        Disposer.dispose(disposable)
    }

    /**
     * Spins until the dialog caches the expected number of worktrees or times out.
     */
    private fun waitForWorktreeCount(dialog: ManageWorktreesDialog, expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            if (dialog.snapshotWorktrees().size == expected) {
                return
            }
            Thread.sleep(25)
        }
        fail("Timed out waiting for worktree count to reach $expected (current=${dialog.snapshotWorktrees().size})")
    }
}
