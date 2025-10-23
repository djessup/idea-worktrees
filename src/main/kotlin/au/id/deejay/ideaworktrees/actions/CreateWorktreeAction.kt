package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.utils.WorktreeOperations
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware

/**
 * Action to create a new Git worktree.
 */
class CreateWorktreeAction : AnAction(), DumbAware {

    /**
     * Opens the create worktree dialog and delegates to [WorktreeOperations].
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Get parent directory from current project
        val parentPath = project.basePath?.let { java.nio.file.Paths.get(it).parent }

        val service = GitWorktreeService.getInstance(project)

        WorktreeOperations.createWorktree(
            project = project,
            service = service,
            parentPath = parentPath,
            promptToOpen = true,
            modalityState = ModalityState.nonModal()
        )
    }

    /**
     * Enables the action only when the current project is backed by a Git repository.
     */
    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val service = GitWorktreeService.getInstance(project)
        e.presentation.isEnabledAndVisible = service.isGitRepository()
    }

    /**
     * Requests background updates to avoid blocking the UI thread during Git checks.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
