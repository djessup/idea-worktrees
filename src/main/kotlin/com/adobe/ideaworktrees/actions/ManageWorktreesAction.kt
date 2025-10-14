package com.adobe.ideaworktrees.actions

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

/**
 * Action to show information about all Git worktrees.
 */
class ManageWorktreesAction : AnAction(), DumbAware {

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

                    val message = buildString {
                        appendLine("Git Worktrees:")
                        appendLine()
                        worktrees.forEach { worktree ->
                            val isCurrent = worktree.path == currentWorktree?.path
                            val marker = if (isCurrent) "* " else "  "
                            appendLine("$marker${worktree.displayName}")
                            appendLine("   Path: ${worktree.path}")
                            if (worktree.branch != null) {
                                appendLine("   Branch: ${worktree.branch}")
                            }
                            appendLine("   Commit: ${worktree.commit.take(7)}")
                            if (worktree.isLocked) {
                                appendLine("   Status: LOCKED")
                            }
                            if (worktree.isPrunable) {
                                appendLine("   Status: PRUNABLE")
                            }
                            appendLine()
                        }
                    }

                    Messages.showInfoMessage(project, message, "Git Worktrees")
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

