package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
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

        val worktrees = service.listWorktrees()
        if (worktrees.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No worktrees found in this repository.",
                "No Worktrees"
            )
            return
        }

        val currentWorktree = service.getCurrentWorktree()

        // Filter out the current worktree (can't delete the one we're in)
        val deletableWorktrees = worktrees.filter { it.path != currentWorktree?.path && !it.isMain }

        if (deletableWorktrees.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No deletable worktrees available. You cannot delete the current worktree or the main repository.",
                "No Deletable Worktrees"
            )
            return
        }

        // Show popup to select worktree to delete
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
    }

    private fun deleteWorktree(
        project: com.intellij.openapi.project.Project,
        service: GitWorktreeService,
        worktree: WorktreeInfo
    ) {
        // Confirm deletion
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

        // Delete the worktree
        val success = service.deleteWorktree(worktree.path, force)

        if (success) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Git Worktree")
                .createNotification(
                    "Worktree deleted successfully",
                    "Deleted worktree at ${worktree.path}",
                    NotificationType.INFORMATION
                )
                .notify(project)
        } else {
            Messages.showErrorDialog(
                project,
                "Failed to delete worktree. Check the IDE log for details.",
                "Error Deleting Worktree"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val service = GitWorktreeService.getInstance(project)
        val isGitRepo = service.isGitRepository()
        val hasMultipleWorktrees = service.listWorktrees().size > 1

        e.presentation.isEnabledAndVisible = isGitRepo && hasMultipleWorktrees
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

