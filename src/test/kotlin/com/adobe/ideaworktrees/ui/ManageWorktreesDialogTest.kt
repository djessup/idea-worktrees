package com.adobe.ideaworktrees.ui

import com.adobe.ideaworktrees.AbstractGitWorktreeTestCase
import com.adobe.ideaworktrees.services.GitWorktreeService
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
