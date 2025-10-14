package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import java.nio.file.Paths

/**
 * Action to create a new Git worktree.
 */
class CreateWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)

        // Get branch name from user
        val branchName = Messages.showInputDialog(
            project,
            "Enter the name for the new branch:",
            "Create New Worktree",
            Messages.getQuestionIcon()
        ) ?: return

        if (branchName.isBlank()) {
            Messages.showErrorDialog(project, "Branch name cannot be empty", "Invalid Input")
            return
        }

        // Get worktree path from user
        val worktreeName = Messages.showInputDialog(
            project,
            "Enter the name for the new worktree directory:",
            "Create New Worktree",
            Messages.getQuestionIcon(),
            branchName,
            null
        ) ?: return

        if (worktreeName.isBlank()) {
            Messages.showErrorDialog(project, "Worktree name cannot be empty", "Invalid Input")
            return
        }

        // Create worktree path relative to project
        val projectPath = Paths.get(project.basePath ?: return)
        val worktreePath = projectPath.parent.resolve(worktreeName)

        // Check if path already exists
        if (worktreePath.toFile().exists()) {
            Messages.showErrorDialog(
                project,
                "A directory with this name already exists: $worktreePath",
                "Directory Exists"
            )
            return
        }

        // Create the worktree
        val success = service.createWorktree(worktreePath, branchName, createBranch = true)

        if (success) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Git Worktree")
                .createNotification(
                    "Worktree created successfully",
                    "Created worktree '$worktreeName' with branch '$branchName' at $worktreePath",
                    NotificationType.INFORMATION
                )
                .notify(project)

            // Ask if user wants to open the new worktree
            val openWorktree = Messages.showYesNoDialog(
                project,
                "Would you like to open the new worktree in a new window?",
                "Open Worktree",
                Messages.getQuestionIcon()
            )

            if (openWorktree == Messages.YES) {
                // TODO: Implement opening worktree in new window
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Git Worktree")
                    .createNotification(
                        "Opening worktree in new window not yet implemented",
                        NotificationType.INFORMATION
                    )
                    .notify(project)
            }
        } else {
            Messages.showErrorDialog(
                project,
                "Failed to create worktree. Check the IDE log for details.",
                "Error Creating Worktree"
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
        e.presentation.isEnabledAndVisible = service.isGitRepository()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

