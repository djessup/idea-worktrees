package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.ui.ManageWorktreesDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Action to show information about all Git worktrees.
 */
class ManageWorktreesAction : AnAction(), DumbAware {

    /**
     * Displays the manage worktrees dialog for the active project.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)

        // Show the manage worktrees dialog
        val dialog = ManageWorktreesDialog(project, service)
        dialog.show()
    }

    /**
     * Limits visibility to projects that are Git repositories.
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
     * Performs `update` checks on a background thread to keep the UI responsive.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
