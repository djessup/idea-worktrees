package com.adobe.ideaworktrees.services

import com.adobe.ideaworktrees.AbstractGitWorktreeTestCase
import com.adobe.ideaworktrees.model.WorktreeOperationResult
import java.nio.file.Files
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
}
