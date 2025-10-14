package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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

        // Execute Git commands on background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val service = GitWorktreeService.getInstance(project)
            try {
                val worktrees = service.listWorktrees()
                val currentWorktree = service.getCurrentWorktree()

                // Show UI on EDT
                ApplicationManager.getApplication().invokeLater({
                    if (worktrees.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No worktrees found in this repository.",
                            "No Worktrees"
                        )
                        return@invokeLater
                    }

                    // Filter out the current worktree (can't delete the one we're in)
                    val deletableWorktrees = worktrees.filter { it.path != currentWorktree?.path && !it.isMain }

                    if (deletableWorktrees.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No deletable worktrees available. You cannot delete the current worktree or the main repository.",
                            "No Deletable Worktrees"
                        )
                        return@invokeLater
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
                }, ModalityState.nonModal())
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Git Worktree")
                        .createNotification(
                            "Failed to list worktrees: ${e.message}",
                            NotificationType.ERROR
                        )
                        .notify(project)
                }, ModalityState.nonModal())
            }
        }
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

        // Delete the worktree on background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.deleteWorktree(worktree.path, force)

            // Show result on EDT
            ApplicationManager.getApplication().invokeLater({
                if (result.isSuccess) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Git Worktree")
                        .createNotification(
                            "Worktree Deleted",
                            result.getSuccessMessage() ?: "Deleted worktree successfully",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                } else {
                    val errorMsg = result.getErrorMessage() ?: "Failed to delete worktree"
                    val details = result.getErrorDetails()
                    val fullMessage = if (details != null) {
                        "$errorMsg\n\nDetails: $details"
                    } else {
                        errorMsg
                    }

                    Messages.showErrorDialog(
                        project,
                        fullMessage,
                        "Error Deleting Worktree"
                    )
                }
            }, ModalityState.nonModal())
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

