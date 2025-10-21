package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.AbstractGitWorktreeTestCase
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil

class ManageWorktreesDialogTest : AbstractGitWorktreeTestCase() {

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

    fun testTableColumnMappingAndTypes() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        val featurePath = worktreePath("wt-table")
        service.createWorktree(featurePath, "feature/table").await()

        lateinit var dialog: ManageWorktreesDialog
        ApplicationManager.getApplication().invokeAndWait {
            dialog = ManageWorktreesDialog(project, service)
        }

        waitForWorktreeCount(dialog, expected = 2)

        val rows = dialog.snapshotWorktrees()
        assertEquals("Expected snapshot to include main and feature worktree", 2, rows.size)

        val columnCount = dialog.tableColumnCountForTest()
        assertEquals("Expected 6 columns in Manage Worktrees table", 6, columnCount)

        assertEquals(
            "First column should expose Boolean class for checkbox rendering",
            java.lang.Boolean::class.java,
            dialog.tableColumnClassForTest(0)
        )
        assertEquals(
            "Non-boolean columns should report String class",
            String::class.java,
            dialog.tableColumnClassForTest(1)
        )

        val mainRowIndex = rows.indexOfFirst { normalizePath(it.path) == normalizePath(projectPath) }
        val featureRowIndex = rows.indexOfFirst { normalizePath(it.path) == normalizePath(featurePath) }

        assertTrue("Expected to find main worktree row", mainRowIndex >= 0)
        assertTrue("Expected to find feature worktree row", featureRowIndex >= 0)

        assertTrue(dialog.tableValueForTest(mainRowIndex, 0) as Boolean)
        assertFalse(dialog.tableValueForTest(featureRowIndex, 0) as Boolean)

        val mainStatus = dialog.tableValueForTest(mainRowIndex, 5) as String
        assertTrue(
            "Main worktree status should include MAIN tag",
            mainStatus.contains("MAIN")
        )

        val featureStatus = dialog.tableValueForTest(featureRowIndex, 5) as String
        assertEquals("Expected non-main worktree status to be '-'", "-", featureStatus)

        val disposable = dialog.disposable
        ApplicationManager.getApplication().invokeAndWait {
            dialog.close(DialogWrapper.CANCEL_EXIT_CODE)
        }
        Disposer.dispose(disposable)
    }

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
