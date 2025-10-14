package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.ide.impl.ProjectUtil
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
 * Action to switch to a different Git worktree.
 */
class SwitchWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Execute Git commands on background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val service = GitWorktreeService.getInstance(project)
            try {
                val worktrees = service.listWorktrees()
                val currentWorktree = service.getCurrentWorktree()

                // Show popup on EDT
                ApplicationManager.getApplication().invokeLater({
                    if (worktrees.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No worktrees found in this repository.",
                            "No Worktrees"
                        )
                        return@invokeLater
                    }

                    // Filter out the current worktree
                    val otherWorktrees = worktrees.filter { it.path != currentWorktree?.path }

                    if (otherWorktrees.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No other worktrees available to switch to.",
                            "No Other Worktrees"
                        )
                        return@invokeLater
                    }

                    // Show popup to select worktree
                    val popup = JBPopupFactory.getInstance().createListPopup(
                        object : BaseListPopupStep<WorktreeInfo>("Switch to Worktree", otherWorktrees) {
                            override fun getTextFor(value: WorktreeInfo): String {
                                return "${value.displayName} (${value.name})"
                            }

                            override fun onChosen(selectedValue: WorktreeInfo, finalChoice: Boolean): PopupStep<*>? {
                                if (finalChoice) {
                                    switchToWorktree(project, selectedValue)
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

    private fun switchToWorktree(project: com.intellij.openapi.project.Project, worktree: WorktreeInfo) {
        // Confirm with user
        val result = Messages.showYesNoDialog(
            project,
            "This will close the current project and open the worktree at:\n${worktree.path}\n\nContinue?",
            "Switch Worktree",
            Messages.getQuestionIcon()
        )

        if (result != Messages.YES) {
            return
        }

        // Open the worktree in a new window
        val worktreeFile = worktree.path.toFile()
        if (!worktreeFile.exists()) {
            Messages.showErrorDialog(
                project,
                "Worktree directory does not exist: ${worktree.path}",
                "Directory Not Found"
            )
            return
        }

        // Close current project and open new one
        ProjectUtil.openOrImport(worktree.path, project, false)
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

