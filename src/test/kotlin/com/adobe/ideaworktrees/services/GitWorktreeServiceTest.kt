package com.adobe.ideaworktrees.services

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.model.WorktreeOperationResult
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Exercising [GitWorktreeService] through the IntelliJ test fixture to validate the
 * most critical Git workflows. These tests follow the official plugin testing guidance by
 * extending [BasePlatformTestCase], which provides a lightweight IDE project environment.
 */
class GitWorktreeServiceTest : BasePlatformTestCase() {

    private lateinit var worktreesRoot: Path
    private val gitExecutable: String by lazy { selectGitExecutable() }

    private val projectPath: Path
        get() = Paths.get(requireNotNull(project.basePath) { "Project basePath should not be null" })

    override fun setUp() {
        super.setUp()
        worktreesRoot = Files.createTempDirectory("git-worktree-service-wts")
        initGitRepository()
    }

    override fun tearDown() {
        try {
            FileUtil.delete(worktreesRoot.toFile())
        } finally {
            super.tearDown()
        }
    }

    fun testCreateWorktreeRequiresInitialCommit() {
        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-initial")

        val result = service.createWorktree(target, "feature/initial")

        assertTrue(result is WorktreeOperationResult.RequiresInitialCommit)
        assertFalse(target.exists())
    }

    fun testCreateWorktreeAfterInitialCommit() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-feature")

        val result = service.createWorktree(target, "feature/test")

        assertTrue(result is WorktreeOperationResult.Success)
        assertTrue("Worktree directory should exist", target.exists())

        val listed = service.listWorktrees()
        val targetRealPath = target.toRealPath()
        val hasMatch = listed.any { candidate ->
            runCatching { candidate.path.toRealPath() == targetRealPath }.getOrDefault(false)
        }
        assertTrue("Expected worktree paths: ${listed.map { it.path }} to contain $targetRealPath", hasMatch)
    }

    fun testAllowCreateInitialCommit() {
        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-auto")

        val result = service.createWorktree(
            path = target,
            branch = "feature/auto",
            createBranch = true,
            allowCreateInitialCommit = true
        )

        assertTrue(result is WorktreeOperationResult.Success)
        val commitCount = runGit("rev-list", "--count", "HEAD").trim().toInt()
        assertTrue("Expected repository to contain at least one commit, found $commitCount", commitCount >= 1)
        assertTrue(target.exists())
    }

    fun testDeleteWorktreeRemovesDirectory() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val target = worktreePath("wt-remove")

        assertTrue(service.createWorktree(target, "feature/remove") is WorktreeOperationResult.Success)
        assertTrue(target.exists())

        val deleteResult = service.deleteWorktree(target, force = true)
        assertTrue(deleteResult is WorktreeOperationResult.Success)
        assertFalse(target.exists())
    }

    fun testMoveWorktreeMovesDirectory() {
        createEmptyCommit("initial")

        val service = GitWorktreeService.getInstance(project)
        val original = worktreePath("wt-original")
        val moved = worktreePath("wt-renamed")

        assertTrue(service.createWorktree(original, "feature/move") is WorktreeOperationResult.Success)
        assertTrue(original.exists())
        assertFalse(moved.exists())

        val moveResult = service.moveWorktree(original, moved)
        assertTrue(moveResult is WorktreeOperationResult.Success)
        assertFalse(original.exists())
        assertTrue(moved.exists())
    }

    fun testCompareWorktreesDetectsChanges() {
        val service = GitWorktreeService.getInstance(project)

        val file = projectPath.resolve("sample.txt")
        file.writeText("main\n")
        runGit("add", "sample.txt")
        runGit("commit", "-m", "Add sample file")

        val featurePath = worktreePath("wt-compare")
        assertTrue(service.createWorktree(featurePath, "feature/compare") is WorktreeOperationResult.Success)

        val featureFile = featurePath.resolve("sample.txt")
        featureFile.writeText("feature change\n")
        runGit("add", "sample.txt", workingDir = featurePath)
        runGit("commit", "-m", "Update sample", workingDir = featurePath)

        val worktrees = service.listWorktrees()
        val featureWorktree = findWorktreeByBranch(worktrees, "feature/compare", featurePath)
        val mainWorktree = findMainWorktree(worktrees)

        val compareResult = service.compareWorktrees(featureWorktree, mainWorktree)
        assertTrue(compareResult is WorktreeOperationResult.Success)
        val details = compareResult.successDetails()
        assertTrue("Expected diff details to contain sample.txt, but was: $details", details?.contains("sample.txt") == true)
    }

    fun testMergeWorktreeFastForward() {
        val service = GitWorktreeService.getInstance(project)

        val file = projectPath.resolve("merge.txt")
        file.writeText("base\n")
        runGit("add", "merge.txt")
        runGit("commit", "-m", "Base commit")

        val featurePath = worktreePath("wt-merge")
        assertTrue(service.createWorktree(featurePath, "feature/merge") is WorktreeOperationResult.Success)

        val featureFile = featurePath.resolve("merge.txt")
        featureFile.writeText("feature update\n")
        runGit("add", "merge.txt", workingDir = featurePath)
        runGit("commit", "-m", "Feature update", workingDir = featurePath)

        val worktrees = service.listWorktrees()
        val featureWorktree = findWorktreeByBranch(worktrees, "feature/merge", featurePath)
        val mainWorktree = findMainWorktree(worktrees)

        val mergeResult = service.mergeWorktree(featureWorktree, mainWorktree, fastForwardOnly = true)
        assertTrue(mergeResult is WorktreeOperationResult.Success)

        val mergedContent = file.readText()
        assertTrue("Expected merged content to include feature changes.", mergedContent.contains("feature update"))
    }

    private fun findWorktreeByBranch(worktrees: List<WorktreeInfo>, branchName: String, fallbackPath: Path? = null): WorktreeInfo {
        val normalizedBranch = branchName.removePrefix("refs/heads/")
        for (info in worktrees) {
            val rawBranch = info.branch?.trim()
            val normalized = rawBranch?.removePrefix("refs/heads/")
            val display = info.displayName.trim()
            val matches = normalized == normalizedBranch ||
                rawBranch == branchName ||
                rawBranch == normalizedBranch ||
                display == normalizedBranch ||
                display == branchName
            if (matches) {
                return info
            }
        }

        if (fallbackPath != null) {
            val normalizedPath = normalizePath(fallbackPath)
            for (info in worktrees) {
                val infoPath = normalizePath(info.path)
                if (infoPath == normalizedPath) {
                    return info
                }
            }
        }

        throw NoSuchElementException(
            "Worktree not found with branch $branchName. Available: ${worktrees.map { it.branch ?: it.path }}"
        )
    }

    private fun normalizePath(path: Path): String {
        return path.toAbsolutePath().normalize().toString().removePrefix("/private")
    }

    private fun findMainWorktree(worktrees: List<WorktreeInfo>): WorktreeInfo {
        return worktrees.firstOrNull { normalizePath(it.path) == normalizePath(projectPath) }
            ?: worktrees.firstOrNull { it.isMain }
            ?: throw IllegalStateException("Main worktree not found among ${worktrees.map { it.path }}")
    }

    private fun initGitRepository() {
        FileUtil.delete(projectPath.resolve(".git").toFile())
        runGit("init")
        runGit("config", "user.name", "Test User")
        runGit("config", "user.email", "test@example.com")
    }

    private fun createEmptyCommit(message: String) {
        runGit("commit", "--allow-empty", "-m", message)
    }

    private fun runGit(vararg args: String, workingDir: Path = projectPath): String {
        val command = mutableListOf(gitExecutable)
        command.addAll(args)

        Files.createDirectories(workingDir)

        val processBuilder = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)

        // Ensure PATH from the outer process is visible when the tests run on the IDE's Executor.
        processBuilder.environment()["PATH"] = System.getenv("PATH")

        val process = processBuilder.start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw AssertionError("Git command failed: ${command.joinToString(" ")}\n$output")
        }
        return output
    }

    private fun worktreePath(name: String): Path = worktreesRoot.resolve(name)

    private fun selectGitExecutable(): String {
        val explicit = System.getenv("GIT_EXECUTABLE")?.takeIf { it.isNotBlank() }
        if (explicit != null && Files.isExecutable(Paths.get(explicit))) {
            return explicit
        }

        val likelyPaths = listOf(
            Paths.get("/usr/bin/git"),
            Paths.get("/usr/local/bin/git")
        )

        for (candidate in likelyPaths) {
            if (Files.isExecutable(candidate)) {
                return candidate.toString()
            }
        }

        return "git"
    }

}
