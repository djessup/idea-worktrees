package au.id.deejay.ideaworktrees.services

import au.id.deejay.ideaworktrees.AbstractGitWorktreeTestCase
import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Exercising [GitWorktreeService] through the IntelliJ test fixture to validate the
 * most critical Git workflows. These tests follow the official plugin testing guidance by
 * extending [AbstractGitWorktreeTestCase], which provisions a lightweight git project.
 */
class GitWorktreeServiceTest : AbstractGitWorktreeTestCase() {

    fun testCreateWorktreeRequiresInitialCommit() {
        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-initial")

        val result = service.createWorktree(target, "feature/initial").await()

        assertTrue(result is WorktreeOperationResult.RequiresInitialCommit)
        assertFalse(target.exists())
    }

    fun testCreateWorktreeAfterInitialCommit() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-feature")

        val result = service.createWorktree(target, "feature/test").await()

        assertTrue(result is WorktreeOperationResult.Success)
        assertTrue("Worktree directory should exist", target.exists())

        val listed = service.listWorktrees().await()
        val targetRealPath = target.toRealPath()
        val hasMatch = listed.any { candidate ->
            runCatching { candidate.path.toRealPath() == targetRealPath }.getOrDefault(false)
        }
        assertTrue("Expected worktree paths: ${listed.map { it.path }} to contain $targetRealPath", hasMatch)
    }

    fun testCreateWorktreeRejectsDuplicateName() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val first = worktreePath("wt-shared-name")
        assertTrue(
            "Initial worktree creation should succeed",
            service.createWorktree(first, "feature/shared").await() is WorktreeOperationResult.Success
        )

        val alternateParent = worktreesRoot.resolve("alt-parent")
        Files.createDirectories(alternateParent)
        val second = alternateParent.resolve("wt-shared-name")

        val result = service.createWorktree(second, "feature/shared-duplicate").await()

        assertTrue("Expected duplicate worktree creation to fail", result is WorktreeOperationResult.Failure)
        val failure = result as WorktreeOperationResult.Failure
        assertTrue("Failure message should mention existing name", failure.error.contains("already exists"))
        assertFalse("Duplicate worktree directory should not be created", second.exists())
    }

    fun testAllowCreateInitialCommit() {
        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-auto")

        val result = service.createWorktree(
            path = target,
            branch = "feature/auto",
            createBranch = true,
            allowCreateInitialCommit = true
        ).await()

        assertTrue(result is WorktreeOperationResult.Success)
        val commitCount = runGit("rev-list", "--count", "HEAD").trim().toInt()
        assertTrue("Expected repository to contain at least one commit, found $commitCount", commitCount >= 1)
        assertTrue(target.exists())
    }

    fun testDeleteWorktreeRemovesDirectory() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-remove")

        assertTrue(service.createWorktree(target, "feature/remove").await() is WorktreeOperationResult.Success)
        assertTrue(target.exists())

        val deleteResult = service.deleteWorktree(target, force = true).await()
        assertTrue(deleteResult is WorktreeOperationResult.Success)
        assertFalse(target.exists())
    }

    fun testMoveWorktreeMovesDirectory() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val original = worktreePath("wt-original")
        val moved = worktreePath("wt-renamed")

        assertTrue(service.createWorktree(original, "feature/move").await() is WorktreeOperationResult.Success)
        assertTrue(original.exists())
        assertFalse(moved.exists())

        val moveResult = service.moveWorktree(original, moved).await()
        assertTrue(moveResult is WorktreeOperationResult.Success)
        assertFalse(original.exists())
        assertTrue(moved.exists())
    }

    fun testListWorktreesMarksMainWorktree() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val featurePath = worktreePath("wt-main-marker")

        assertTrue(
            service.createWorktree(featurePath, "feature/main-marker").await() is WorktreeOperationResult.Success
        )

        val worktrees = service.listWorktrees().await()
        val main = findMainWorktree(worktrees)
        assertTrue("Main worktree should be flagged as main", main.isMain)

        val featureInfo = worktrees.first {
            normalizePath(it.path) == normalizePath(featurePath)
        }
        assertFalse("Secondary worktrees should not be marked as main", featureInfo.isMain)
    }

    fun testMoveWorktreeRejectsMainWorktree() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val main = findMainWorktree(service.listWorktrees().await())
        val destination = worktreePath("wt-main-new-location")

        val result = service.moveWorktree(main.path, destination).await()
        assertTrue("Expected move operation to fail for the main worktree", result is WorktreeOperationResult.Failure)

        val failure = result as WorktreeOperationResult.Failure
        assertTrue(
            "Failure message should mention main worktree: ${failure.error}",
            failure.error.contains("main worktree", ignoreCase = true)
        )
    }

    fun testCompareWorktreesDetectsChanges() {
        val service = GitWorktreeService.getInstance(project)

        projectPath.resolve("sample.txt").writeText("main\n")
        runGit("add", "sample.txt")
        runGit("commit", "-m", "Add sample file")

        val featurePath = worktreePath("wt-compare")
        assertTrue(service.createWorktree(featurePath, "feature/compare").await() is WorktreeOperationResult.Success)

        featurePath.resolve("sample.txt").writeText("feature change\n")
        runGit("add", "sample.txt", workingDir = featurePath)
        runGit("commit", "-m", "Update sample", workingDir = featurePath)

        val worktrees = service.listWorktrees().await()
        val featureWorktree = findWorktreeByBranch(worktrees, "feature/compare", featurePath)
        val mainWorktree = findMainWorktree(worktrees)

        val compareResult = service.compareWorktrees(featureWorktree, mainWorktree).await()
        assertTrue(compareResult is WorktreeOperationResult.Success)
        val details = compareResult.successDetails()
        assertTrue("Expected diff details to contain sample.txt, but was: $details", details?.contains("sample.txt") == true)
    }

    fun testCompareWorktreesFailsWhenSourceDirty() {
        val service = GitWorktreeService.getInstance(project)

        projectPath.resolve("dirty.txt").writeText("main\n")
        runGit("add", "dirty.txt")
        runGit("commit", "-m", "Base commit")

        val featurePath = worktreePath("wt-dirty-compare")
        assertTrue(service.createWorktree(featurePath, "feature/dirty").await() is WorktreeOperationResult.Success)

        featurePath.resolve("dirty.txt").writeText("uncommitted change\n")

        val worktrees = service.listWorktrees().await()
        val featureWorktree = findWorktreeByBranch(worktrees, "feature/dirty", featurePath)
        val mainWorktree = findMainWorktree(worktrees)

        val compareResult = service.compareWorktrees(featureWorktree, mainWorktree).await()
        assertTrue(compareResult is WorktreeOperationResult.Failure)
        val failure = compareResult as WorktreeOperationResult.Failure
        assertTrue(
            "Expected failure message to mention uncommitted changes, but was: ${failure.error}",
            failure.error.contains("uncommitted", ignoreCase = true)
        )
        assertTrue(
            "Expected failure details to mention the dirty worktree path, but was: ${failure.details}",
            failure.details?.contains(featurePath.fileName.toString()) == true
        )
    }

    fun testMergeWorktreeFastForward() {
        val service = GitWorktreeService.getInstance(project)

        projectPath.resolve("merge.txt").writeText("base\n")
        runGit("add", "merge.txt")
        runGit("commit", "-m", "Base commit")

        val featurePath = worktreePath("wt-merge")
        assertTrue(service.createWorktree(featurePath, "feature/merge").await() is WorktreeOperationResult.Success)

        featurePath.resolve("merge.txt").writeText("feature update\n")
        runGit("add", "merge.txt", workingDir = featurePath)
        runGit("commit", "-m", "Feature update", workingDir = featurePath)

        val worktrees = service.listWorktrees().await()
        val featureWorktree = findWorktreeByBranch(worktrees, "feature/merge", featurePath)
        val mainWorktree = findMainWorktree(worktrees)

        val mergeResult = service.mergeWorktree(featureWorktree, mainWorktree, fastForwardOnly = true).await()
        assertTrue(mergeResult is WorktreeOperationResult.Success)

        val mergedContent = projectPath.resolve("merge.txt").readText()
        assertTrue("Expected merged content to include feature changes.", mergedContent.contains("feature update"))
    }

    fun testListWorktreesWhenRepositoryMissingGitDir() {
        val service = GitWorktreeService.getInstance(project)
        FileUtil.delete(projectPath.resolve(".git").toFile())

        val listed = service.listWorktrees().await()

        assertTrue("Expected empty list when git directory is missing", listed.isEmpty())
    }

    fun testCreateWorktreeFailsForMissingBranchWhenNotCreating() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-existing-branch")

        val result = service.createWorktree(
            path = target,
            branch = "feature/manual",
            createBranch = false
        ).await()

        assertTrue(result is WorktreeOperationResult.Failure)
        assertFalse(target.exists())
    }

    fun testDeleteWorktreeFailureWhenGitCommandFails() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val nonexistent = worktreePath("wt-missing-delete")

        FileUtil.delete(projectPath.resolve(".git").toFile())

        val result = service.deleteWorktree(nonexistent, force = false).await()

        assertTrue(result is WorktreeOperationResult.Failure)
        val message = (result as WorktreeOperationResult.Failure).error
        assertTrue("Failure message should mention delete", message.contains("delete", ignoreCase = true))
    }

    fun testCreateWorktreeFailsWhenTargetDirectoryAlreadyExists() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-existing-dir")
        Files.createDirectories(target)
        target.resolve("existing.txt").writeText("occupied")

        val result = service.createWorktree(target, "feature/existing").await()

        assertTrue(result is WorktreeOperationResult.Failure)
    }

    fun testMergeWorktreeFastForwardFailureOnDivergedHistory() {
        val service = GitWorktreeService.getInstance(project)

        projectPath.resolve("base.txt").writeText("base\n")
        runGit("add", "base.txt")
        runGit("commit", "-m", "Base commit")

        val featurePath = worktreePath("wt-ff-only")
        assertTrue(service.createWorktree(featurePath, "feature/ff-only").await() is WorktreeOperationResult.Success)

        featurePath.resolve("feature.txt").writeText("feature\n")
        runGit("add", "feature.txt", workingDir = featurePath)
        runGit("commit", "-m", "Feature commit", workingDir = featurePath)

        projectPath.resolve("main.txt").writeText("main\n")
        runGit("add", "main.txt")
        runGit("commit", "-m", "Main diverge")

        val worktrees = service.listWorktrees().await()
        val featureWorktree = findWorktreeByBranch(worktrees, "feature/ff-only", featurePath)
        val mainWorktree = findMainWorktree(worktrees)

        val result = service.mergeWorktree(featureWorktree, mainWorktree, fastForwardOnly = true).await()

        assertTrue(result is WorktreeOperationResult.Failure)
    }

    fun testCaseInsensitivePathComparisonUsesNormalizedPaths() {
        val service = GitWorktreeService.getInstance(project)
        val originalOs = System.getProperty("os.name")
        System.setProperty("os.name", "Windows 11")
        try {
            val method = GitWorktreeService::class.java.getDeclaredMethod(
                "isSamePath",
                Path::class.java,
                Path::class.java
            )
            method.isAccessible = true

            val upper = projectPath.resolve("Folder").resolve("File.txt")
            Files.createDirectories(upper.parent)
            Files.writeString(upper, "content")

            val lower = projectPath.resolve("folder").resolve("file.txt")

            val result = method.invoke(service, upper, lower) as Boolean
            assertTrue(result)
        } finally {
            // Restore OS name and clean up created test files to keep repository clean between tests
            System.setProperty("os.name", originalOs)
            runCatching { FileUtil.delete(projectPath.resolve("Folder").toFile()) }
        }
    }

    fun testHasCommitsReturnsFalseForInvalidWorkingDirectory() {
        val service = GitWorktreeService.getInstance(project)
        val method = GitWorktreeService::class.java.getDeclaredMethod("hasCommits", Path::class.java)
        method.isAccessible = true

        val bogus = projectPath.resolve("does-not-exist").resolve("repo")

        val result = method.invoke(service, bogus) as Boolean

        assertFalse(result)
    }

    fun testDeleteWorktreeFailsWhenProjectPathMissing() {
        val service = GitWorktreeService.getInstance(project)

        val currentDir = projectPath
        val backup = currentDir.resolveSibling(currentDir.fileName.toString() + "-backup")
        Files.move(currentDir, backup)
        try {
            val result = service.deleteWorktree(currentDir.resolve("missing"), force = false).await()
            assertTrue(result is WorktreeOperationResult.Failure)
        } finally {
            Files.move(backup, currentDir)
        }
    }

    fun testCompareWorktreesFailureForInvalidRange() {
        val service = GitWorktreeService.getInstance(project)
        val invalid = WorktreeInfo(projectPath, null, "deadbeef")

        val result = service.compareWorktrees(invalid, invalid).await()

        assertTrue(result is WorktreeOperationResult.Failure)
    }

    fun testMergeWorktreeFailsWhenTargetPathMissing() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val worktrees = service.listWorktrees().await()
        val source = findMainWorktree(worktrees)
        val missingTarget = WorktreeInfo(
            path = worktreePath("wt-missing-target"),
            branch = "feature/missing",
            commit = source.commit,
            isMain = false
        )

        val result = service.mergeWorktree(source, missingTarget, fastForwardOnly = false).await()

        assertTrue(result is WorktreeOperationResult.Failure)
        val message = (result as WorktreeOperationResult.Failure).error
        assertTrue(message.contains("Target worktree path", ignoreCase = true))
    }

    fun testIsGitAvailableReturnsTrueOnTestEnvironment() {
        val service = GitWorktreeService.getInstance(project)
        val future = CompletableFuture<Boolean>()

        ApplicationManager.getApplication().executeOnPooledThread {
            future.complete(service.isGitAvailable())
        }

        assertTrue(
            "Git should be available in test environment",
            future.get(30, TimeUnit.SECONDS)
        )
    }

    fun testForceGitRepositoryOverride() {
        val service = GitWorktreeService.getInstance(project)

        // Ensure the repository is temporarily unreadable by removing the git directory
        val gitDir = projectPath.resolve(".git")
        val backup = gitDir.resolveSibling(gitDir.fileName.toString() + "-backup")
        if (Files.exists(gitDir)) {
            Files.move(gitDir, backup)
        }

        try {
            service.forceGitRepositoryForTests(false)
            assertFalse("Without override the repository should not be detected", service.isGitRepository())

            service.forceGitRepositoryForTests(true)
            assertTrue("Override should force repository detection", service.isGitRepository())
        } finally {
            service.forceGitRepositoryForTests(false)
            if (Files.exists(backup)) {
                Files.move(backup, gitDir)
            }
        }
    }
}
