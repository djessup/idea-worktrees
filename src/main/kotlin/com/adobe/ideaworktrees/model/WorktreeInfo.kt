package com.adobe.ideaworktrees.model

import java.nio.file.Path

/**
 * Represents information about a Git worktree.
 *
 * @property path The absolute path to the worktree directory
 * @property branch The branch checked out in this worktree (null if detached HEAD)
 * @property commit The commit hash currently checked out
 * @property isLocked Whether the worktree is locked
 * @property isPrunable Whether the worktree can be pruned (directory doesn't exist)
 * @property isBare Whether this is a bare repository
 * @property isMain Whether this worktree is the primary/main checkout for the repository
 */
data class WorktreeInfo(
    val path: Path,
    val branch: String?,
    val commit: String,
    val isLocked: Boolean = false,
    val isPrunable: Boolean = false,
    val isBare: Boolean = false,
    val isMain: Boolean = false
) {
    /**
     * Returns the name of the worktree (last component of the path).
     */
    val name: String
        get() = path.fileName?.toString() ?: path.toString()

    /**
     * Returns a display name for the worktree, preferring the branch name if available.
     */
    val displayName: String
        get() = branch ?: commit.take(7)

    override fun toString(): String {
        return "WorktreeInfo(path=$path, branch=$branch, commit=${commit.take(7)})"
    }
}
