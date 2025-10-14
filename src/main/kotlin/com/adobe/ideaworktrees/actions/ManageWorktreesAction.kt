package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * Action to show information about all Git worktrees.
 */
class ManageWorktreesAction : AnAction(), DumbAware {

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

