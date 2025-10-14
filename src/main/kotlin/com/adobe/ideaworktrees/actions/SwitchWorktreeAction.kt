package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.ide.impl.ProjectUtil
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
 * Action to switch to a different Git worktree.
 */
class SwitchWorktreeAction : AnAction(), DumbAware {

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

        // Filter out the current worktree
        val otherWorktrees = worktrees.filter { it.path != currentWorktree?.path }

        if (otherWorktrees.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No other worktrees available to switch to.",
                "No Other Worktrees"
            )
            return
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
        val isGitRepo = service.isGitRepository()
        val hasMultipleWorktrees = service.listWorktrees().size > 1

        e.presentation.isEnabledAndVisible = isGitRepo && hasMultipleWorktrees
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

