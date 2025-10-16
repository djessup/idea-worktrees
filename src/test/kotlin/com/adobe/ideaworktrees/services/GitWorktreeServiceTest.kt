package com.adobe.ideaworktrees.services

import com.adobe.ideaworktrees.model.WorktreeOperationResult
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

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

    private fun initGitRepository() {
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
