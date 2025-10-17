package com.adobe.ideaworktrees.ui

import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import java.nio.file.Paths

class WorktreeStatusBarWidgetTest : BasePlatformTestCase() {

    fun testFactoryIsAlwaysAvailable() {
        val factory = WorktreeStatusBarWidgetFactory()
        assertTrue(factory.isAvailable(project))
        assertEquals(WorktreeStatusBarWidgetFactory.ID, factory.id)
    }

    fun testWidgetHiddenWhenProjectNotGitRepository() {
        val projectPath = Paths.get(requireNotNull(project.basePath))
        FileUtil.delete(projectPath.resolve(".git").toFile())
        ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(emptyList())

        val service = GitWorktreeService.getInstance(project)
        assertFalse("Sanity check: test project should not be treated as git repo", service.isGitRepository())

        val factory = WorktreeStatusBarWidgetFactory()
        val widget = factory.createWidget(project) as WorktreeStatusBarWidget

        val method = WorktreeStatusBarWidget::class.java.getDeclaredMethod(
            "getWidgetState",
            VirtualFile::class.java
        )
        method.isAccessible = true
        val state = method.invoke(widget, *arrayOf<Any?>(null))
        val widgetStateClass = method.returnType
        val hiddenField = widgetStateClass.getField("HIDDEN")
        val hiddenValue = hiddenField.get(null)

        assertSame(hiddenValue, state)
    }

    fun testSuggestDirectoryNameUsesProjectFolder() {
        val projectPath = java.nio.file.Paths.get(requireNotNull(project.basePath))
        val branch = "feature/widget"
        val suggested = WorktreeStatusBarWidget.suggestDirectoryName(projectPath, branch)
    // Compute expected sanitized branch name using same rules as the helper
    val sanitizedBranch = branch.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim { it == '-' || it == '.' }
    val expected = projectPath.fileName.toString() + "-" + sanitizedBranch
    assertEquals(expected, suggested)
    }
}
