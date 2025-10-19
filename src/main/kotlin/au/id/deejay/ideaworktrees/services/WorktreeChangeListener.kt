package au.id.deejay.ideaworktrees.services

/**
 * Listener for Git worktree changes initiated by the plugin.
 */
fun interface WorktreeChangeListener {
    /**
     * Invoked when the set of worktrees may have changed.
     */
    fun worktreesChanged()
}
