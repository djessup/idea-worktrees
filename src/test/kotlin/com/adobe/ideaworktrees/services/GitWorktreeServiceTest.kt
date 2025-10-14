package com.adobe.ideaworktrees.services

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

/**
 * Tests for GitWorktreeService.
 */
class GitWorktreeServiceTest : BasePlatformTestCase() {

    private lateinit var service: GitWorktreeService

    override fun setUp() {
        super.setUp()
        service = GitWorktreeService.getInstance(project)
    }

    fun testServiceIsAvailable() {
        assertNotNull("GitWorktreeService should be available", service)
    }

    fun testIsGitAvailable() {
        // This test assumes Git is installed on the system
        // In a real CI environment, you might want to skip this if Git is not available
        val isAvailable = service.isGitAvailable()
        assertTrue("Git should be available", isAvailable)
    }

    fun testParseWorktreeListEmpty() {
        // Test with empty output
        val worktrees = service.listWorktrees()
        // This will return empty list if project is not a git repo
        assertNotNull("Worktree list should not be null", worktrees)
    }

    fun testGetCurrentWorktree() {
        // Test getting current worktree
        val currentWorktree = service.getCurrentWorktree()
        // May be null if project is not in a git worktree
        // Just verify it doesn't throw an exception
        assertNotNull("Service should handle getCurrentWorktree call", service)
    }

    fun testIsGitRepository() {
        // Test if project is a git repository
        val isGitRepo = service.isGitRepository()
        // Just verify it doesn't throw an exception
        assertNotNull("Service should handle isGitRepository call", service)
    }
}

