package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.utils.WorktreeAsyncOperations
import au.id.deejay.ideaworktrees.utils.WorktreeNotifications
import au.id.deejay.ideaworktrees.utils.WorktreeResultHandler
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep

/**
 * Action to delete a Git worktree.
 */
class DeleteWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)

        WorktreeAsyncOperations.loadWorktreesWithCurrent(
            project = project,
            service = service,
            onSuccess = { worktrees, currentWorktree ->
                if (worktrees.isEmpty()) {
                    Messages.showInfoMessage(
                        project,
                        "No worktrees found in this repository.",
                        "No Worktrees"
                    )
                    return@loadWorktreesWithCurrent
                }

                val deletableWorktrees = worktrees.filter { it.path != currentWorktree?.path && !it.isMain }

                if (deletableWorktrees.isEmpty()) {
                    Messages.showInfoMessage(
                        project,
                        "No deletable worktrees available. You cannot delete the current worktree or the main repository.",
                        "No Deletable Worktrees"
                    )
                    return@loadWorktreesWithCurrent
                }

                val popup = JBPopupFactory.getInstance().createListPopup(
                    object : BaseListPopupStep<WorktreeInfo>("Delete Worktree", deletableWorktrees) {
                        override fun getTextFor(value: WorktreeInfo): String {
                            return "${value.displayName} (${value.name})"
                        }

                        override fun onChosen(selectedValue: WorktreeInfo, finalChoice: Boolean): PopupStep<*>? {
                            if (finalChoice) {
                                deleteWorktree(project, service, selectedValue)
                            }
                            return PopupStep.FINAL_CHOICE
                        }
                    }
                )

                popup.showCenteredInCurrentWindow(project)
            },
            onError = { error ->
                WorktreeNotifications.showError(
                    project = project,
                    title = "Failed to List Worktrees",
                    message = error.message ?: "Unknown error"
                )
            }
        )
    }

    private fun deleteWorktree(
        project: com.intellij.openapi.project.Project,
        service: GitWorktreeService,
        worktree: WorktreeInfo
    ) {
        // Confirm deletion (on EDT)
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete the worktree at:\n${worktree.path}\n\n" +
                    "This will remove the worktree directory and all its contents.",
            "Confirm Delete Worktree",
            Messages.getWarningIcon()
        )

        if (result != Messages.YES) {
            return
        }

        // Check if worktree has uncommitted changes
        val force = if (worktree.isLocked) {
            val forceResult = Messages.showYesNoDialog(
                project,
                "The worktree is locked. Force deletion?",
                "Worktree Locked",
                Messages.getWarningIcon()
            )
            forceResult == Messages.YES
        } else {
            false
        }

        service.deleteWorktree(worktree.path, force)
            .whenComplete { result, error ->
                if (error != null) {
                    WorktreeNotifications.showError(
                        project = project,
                        title = "Error Deleting Worktree",
                        message = error.message ?: "Unknown error occurred while deleting worktree"
                    )
                    return@whenComplete
                }

                if (result != null) {
                    WorktreeResultHandler.handle(
                        project = project,
                        result = result,
                        successTitle = "Worktree Deleted",
                        errorTitle = "Error Deleting Worktree"
                    )
                } else {
                    WorktreeNotifications.showError(
                        project = project,
                        title = "Error Deleting Worktree",
                        message = "Worktree deletion failed with an unknown error"
                    )
                }
            }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val service = GitWorktreeService.getInstance(project)
        // Only check if it's a Git repository - don't call listWorktrees() in update()
        // as it would block the UI thread
        e.presentation.isEnabledAndVisible = service.isGitRepository()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
