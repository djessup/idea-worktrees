package au.id.deejay.ideaworktrees.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

/**
 * Covers display name derivation and other helpers on [WorktreeInfo].
 */
class WorktreeInfoTest {

    /**
     * Stripping `refs/heads/` should yield the bare branch name.
     */
    @Test
    fun displayNameStripsRefsHeadsPrefix() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/heads/master",
            commit = "abc123def456"
        )
        assertEquals("master", worktree.displayName)
    }

    /**
     * Removing `refs/remotes/` should preserve remote-qualified branch names.
     */
    @Test
    fun displayNameStripsRefsRemotesPrefix() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/remotes/origin/feature",
            commit = "abc123def456"
        )
        assertEquals("origin/feature", worktree.displayName)
    }

    /**
     * Validates tag prefixes are removed for display.
     */
    @Test
    fun displayNameStripsRefsTagsPrefix() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/tags/v1.0",
            commit = "abc123def456"
        )
        assertEquals("v1.0", worktree.displayName)
    }

    /**
     * Ensures branches without known prefixes are returned unchanged.
     */
    @Test
    fun displayNameReturnsBranchAsIsWhenNoPrefixMatches() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "feature/my-branch",
            commit = "abc123def456"
        )
        assertEquals("feature/my-branch", worktree.displayName)
    }

    /**
     * Confirms commit hashes are truncated when no branch name exists.
     */
    @Test
    fun displayNameFallsBackToShortenedCommitWhenNoBranch() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = null,
            commit = "abc123def456"
        )
        assertEquals("abc123d", worktree.displayName)
    }

    /**
     * Handles commit hashes shorter than seven characters gracefully.
     */
    @Test
    fun displayNameHandlesShortCommitHash() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = null,
            commit = "abc"
        )
        assertEquals("abc", worktree.displayName)
    }

    /**
     * Treats empty branch strings as legitimate display values.
     */
    @Test
    fun displayNameHandlesEmptyBranch() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "",
            commit = "abc123def456"
        )
        // Empty string is still a non-null value, so it should be returned as-is
        assertEquals("", worktree.displayName)
    }

    /**
     * Supports remote branches with additional path segments.
     */
    @Test
    fun displayNameHandlesComplexRemoteBranch() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/remotes/upstream/feature/complex-name",
            commit = "abc123def456"
        )
        assertEquals("upstream/feature/complex-name", worktree.displayName)
    }

    /**
     * Handles tags containing nested path segments.
     */
    @Test
    fun displayNameHandlesTagWithSlashes() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/tags/release/v1.0.0",
            commit = "abc123def456"
        )
        assertEquals("release/v1.0.0", worktree.displayName)
    }

    /**
     * Returns the last path segment as the worktree name.
     */
    @Test
    fun nameReturnsLastPathComponent() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree/my-feature"),
            branch = "feature/test",
            commit = "abc123def456"
        )
        assertEquals("my-feature", worktree.name)
    }

    /**
     * Ensures `toString` includes key identifying attributes for debugging.
     */
    @Test
    fun toStringIncludesKeyProperties() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/heads/master",
            commit = "abc123def456",
            isMain = true,
            isLocked = false,
            isPrunable = false
        )
        val str = worktree.toString()
        assert(str.contains("path=/test/worktree"))
        assert(str.contains("branch=refs/heads/master"))
        assert(str.contains("commit=abc123d"))
        assert(str.contains("isMain=true"))
        assert(str.contains("isLocked=false"))
        assert(str.contains("isPrunable=false"))
    }
}
