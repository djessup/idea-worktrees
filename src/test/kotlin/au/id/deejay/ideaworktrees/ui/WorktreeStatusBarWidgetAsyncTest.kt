package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.AbstractGitWorktreeTestCase
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil

class WorktreeStatusBarWidgetAsyncTest : AbstractGitWorktreeTestCase() {

    fun testCacheRefreshReflectsLatestWorktrees() {
        createEmptyCommit("initial")
        val service = GitWorktreeService.getInstance(project)

        val featurePath = worktreePath("wt-widget")
        val createResult = service.createWorktree(featurePath, "feature/widget").await()
        assertTrue("Worktree creation failed: $createResult", createResult.isSuccess)
        val allWorktrees = service.listWorktrees().await()
        if (allWorktrees.size != 2) {
            fail("Unexpected worktree list: ${allWorktrees.map { normalizePath(it.path) }}")
        }

        lateinit var widget: WorktreeStatusBarWidget
        ApplicationManager.getApplication().invokeAndWait {
            widget = WorktreeStatusBarWidget(project)
        }

        ApplicationManager.getApplication().invokeAndWait {
            widget.refreshCacheForTest()
        }
        waitForCondition("Cache refresh did not populate worktrees") {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            widget.getCachedWorktreesForTest().size == 2
        }

        val cached = widget.getCachedWorktreesForTest()
        assertEquals(2, cached.size)
        assertTrue(cached.any { normalizePath(it.path) == normalizePath(featurePath) })
        assertEquals(
            normalizePath(projectPath),
            normalizePath(widget.getCachedCurrentWorktreeForTest()!!.path)
        )
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
