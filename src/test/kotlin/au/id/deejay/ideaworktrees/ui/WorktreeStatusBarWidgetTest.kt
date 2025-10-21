package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.services.GitWorktreeService
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
        service.forceGitRepositoryForTests(false)
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
        val suggested = au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, branch)
        // Compute expected sanitized branch name using same rules as the helper
        val sanitizedBranch = branch.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim { it == '-' || it == '.' }
        val expected = projectPath.fileName.toString() + "-" + sanitizedBranch
        assertEquals(expected, suggested)
    }

    fun testSuggestDirectoryNamePreservesUnderscores() {
        val projectPath = java.nio.file.Paths.get("/test/my-project")
        val branch = "my_feature_branch"
        val suggested = au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, branch)
        assertEquals("my-project-my_feature_branch", suggested)
    }

    fun testSuggestDirectoryNameHandlesMultipleSlashes() {
        val projectPath = java.nio.file.Paths.get("/test/my-project")
        val branch = "feature//double-slash"
        val suggested = au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, branch)
        assertEquals("my-project-feature-double-slash", suggested)
    }

    fun testSuggestDirectoryNameSanitizesSpecialCharacters() {
        val projectPath = java.nio.file.Paths.get("/test/my-project")

        // Test hash symbol
        assertEquals("my-project-bug-123", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "bug#123"))

        // Test colon
        assertEquals("my-project-hotfix-urgent", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "hotfix:urgent"))

        // Test space
        assertEquals("my-project-my-branch", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "my branch"))

        // Test at symbol
        assertEquals("my-project-test-branch", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "test@branch"))
    }

    fun testSuggestDirectoryNameTrimsLeadingAndTrailingSpecialChars() {
        val projectPath = java.nio.file.Paths.get("/test/my-project")

        // Test leading hyphens
        assertEquals("my-project-leading", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "---leading"))

        // Test trailing hyphens
        assertEquals("my-project-trailing", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "trailing---"))

        // Test leading dots
        assertEquals("my-project-dots", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "...dots"))

        // Test trailing dots
        assertEquals("my-project-dots", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "dots..."))

        // Test mixed leading/trailing
        assertEquals("my-project-middle", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "-.middle.-"))
    }

    fun testSuggestDirectoryNamePreservesValidCharacters() {
        val projectPath = java.nio.file.Paths.get("/test/my-project")

        // Test valid name with hyphens
        assertEquals("my-project-valid-name", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "valid-name"))

        // Test valid name with dots
        assertEquals("my-project-valid.name", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "valid.name"))

        // Test valid name with underscores
        assertEquals("my-project-valid_name", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "valid_name"))

        // Test alphanumeric
        assertEquals("my-project-v1.2.3", au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(projectPath, "v1.2.3"))
    }

    fun testSuggestDirectoryNameHandlesNullProjectPath() {
        val suggested = au.id.deejay.ideaworktrees.utils.WorktreeOperations.suggestDirectoryName(null, "feature/test")
        assertEquals("project-feature-test", suggested)
    }
}
