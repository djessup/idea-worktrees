package au.id.deejay.ideaworktrees.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class WorktreeInfoTest {

    @Test
    fun displayNameStripsRefsHeadsPrefix() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/heads/master",
            commit = "abc123def456"
        )
        assertEquals("master", worktree.displayName)
    }

    @Test
    fun displayNameStripsRefsRemotesPrefix() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/remotes/origin/feature",
            commit = "abc123def456"
        )
        assertEquals("origin/feature", worktree.displayName)
    }

    @Test
    fun displayNameStripsRefsTagsPrefix() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/tags/v1.0",
            commit = "abc123def456"
        )
        assertEquals("v1.0", worktree.displayName)
    }

    @Test
    fun displayNameReturnsBranchAsIsWhenNoPrefixMatches() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "feature/my-branch",
            commit = "abc123def456"
        )
        assertEquals("feature/my-branch", worktree.displayName)
    }

    @Test
    fun displayNameFallsBackToShortenedCommitWhenNoBranch() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = null,
            commit = "abc123def456"
        )
        assertEquals("abc123d", worktree.displayName)
    }

    @Test
    fun displayNameHandlesShortCommitHash() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = null,
            commit = "abc"
        )
        assertEquals("abc", worktree.displayName)
    }

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

    @Test
    fun displayNameHandlesComplexRemoteBranch() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/remotes/upstream/feature/complex-name",
            commit = "abc123def456"
        )
        assertEquals("upstream/feature/complex-name", worktree.displayName)
    }

    @Test
    fun displayNameHandlesTagWithSlashes() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree"),
            branch = "refs/tags/release/v1.0.0",
            commit = "abc123def456"
        )
        assertEquals("release/v1.0.0", worktree.displayName)
    }

    @Test
    fun nameReturnsLastPathComponent() {
        val worktree = WorktreeInfo(
            path = Paths.get("/test/worktree/my-feature"),
            branch = "feature/test",
            commit = "abc123def456"
        )
        assertEquals("my-feature", worktree.name)
    }

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

