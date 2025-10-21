package au.id.deejay.ideaworktrees.model

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
     * Returns a display name for the worktree, preferring the bare branch name if available.
     * Strips common Git reference prefixes (refs/heads/, refs/remotes/, refs/tags/) from branch names.
     * Falls back to the shortened commit hash (first 7 characters) when no branch is available.
     */
    val displayName: String
        get() = branch?.let { stripGitRefPrefix(it) } ?: commit.take(7)

    /**
     * Strips common Git reference prefixes from a branch name.
     * - refs/heads/ → removed (e.g., "refs/heads/master" → "master")
     * - refs/remotes/ → removed (e.g., "refs/remotes/origin/feature" → "origin/feature")
     * - refs/tags/ → removed (e.g., "refs/tags/v1.0" → "v1.0")
     * - Otherwise → returned as-is
     */
    private fun stripGitRefPrefix(ref: String): String {
        return when {
            ref.startsWith("refs/heads/") -> ref.removePrefix("refs/heads/")
            ref.startsWith("refs/remotes/") -> ref.removePrefix("refs/remotes/")
            ref.startsWith("refs/tags/") -> ref.removePrefix("refs/tags/")
            else -> ref
        }
    }

    override fun toString(): String {
        return "WorktreeInfo(path=$path, branch=$branch, commit=${commit.take(7)}, isMain=$isMain, isLocked=$isLocked, isPrunable=$isPrunable)"
    }
}
