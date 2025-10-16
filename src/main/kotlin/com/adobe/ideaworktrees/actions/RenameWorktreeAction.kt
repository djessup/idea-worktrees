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
import java.nio.file.Paths
import java.nio.file.Files
import java.nio.file.Path

/**
 * Action that renames an existing Git worktree by moving it to a new directory.
 */
class RenameWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val worktrees = service.listWorktrees()
                val projectRoot = project.basePath?.let { Paths.get(it).toAbsolutePath().normalize().toString() }

                val candidates = worktrees.filter { worktree ->
                    val normalizedPath = worktree.path.toAbsolutePath().normalize().toString()
                    val isProjectRoot = projectRoot != null && normalizedPath == projectRoot
                    !worktree.isMain && !isProjectRoot
                }
                ApplicationManager.getApplication().invokeLater({
                    if (candidates.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No additional worktrees available to rename.",
                            "Rename Worktree"
                        )
                        return@invokeLater
                    }

                    val displayNames = candidates.map { it.displayString() }.toTypedArray()
                    val selected = Messages.showEditableChooseDialog(
                        "Select the worktree you would like to rename:",
                        "Rename Worktree",
                        Messages.getQuestionIcon(),
                        displayNames,
                        displayNames.first(),
                        null
                    ) ?: return@invokeLater

                    val worktree = candidates[displayNames.indexOf(selected)]
                    val newName = Messages.showInputDialog(
                        project,
                        "Enter a new name for '${worktree.displayName}':",
                        "Rename Worktree",
                        Messages.getQuestionIcon(),
                        worktree.path.fileName?.toString() ?: "",
                        null
                    )?.trim() ?: return@invokeLater

                    if (!isValidName(newName)) {
                        Messages.showErrorDialog(
                            project,
                            "The provided name is invalid. Please avoid path separators or empty names.",
                            "Rename Worktree"
                        )
                        return@invokeLater
                    }

                    val parent = worktree.path.parent
                    if (parent == null) {
                        Messages.showErrorDialog(
                            project,
                            "Cannot determine the parent directory for the selected worktree.",
                            "Rename Worktree"
                        )
                        return@invokeLater
                    }

                    val newPath = parent.resolve(newName)
                    if (Files.exists(newPath)) {
                        Messages.showErrorDialog(
                            project,
                            "A directory with the name '$newName' already exists.",
                            "Rename Worktree"
                        )
                        return@invokeLater
                    }

                    ApplicationManager.getApplication().executeOnPooledThread {
                        val result = service.moveWorktree(worktree.path, newPath)
                        ApplicationManager.getApplication().invokeLater({
                            if (result.isSuccess) {
                                notify(
                                    project,
                                    "Worktree Renamed",
                                    result.successMessage() ?: "Worktree renamed successfully",
                                    NotificationType.INFORMATION
                                )
                            } else {
                                val message = result.errorMessage() ?: "Failed to rename worktree"
                                val details = result.errorDetails()
                                val fullMessage = if (details != null) "$message\n\nDetails: $details" else message
                                Messages.showErrorDialog(project, fullMessage, "Rename Worktree")
                            }
                        }, ModalityState.nonModal())
                    }
                }, ModalityState.nonModal())
            } catch (ex: Exception) {
                ApplicationManager.getApplication().invokeLater({
                    Messages.showErrorDialog(
                        project,
                        "Failed to list worktrees: ${ex.message}",
                        "Rename Worktree"
                    )
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
        val worktrees = service.listWorktrees()
        val projectRoot = project.basePath?.let { Paths.get(it).toAbsolutePath().normalize().toString() }
        val hasCandidate = worktrees.any { worktree ->
            val normalizedPath = worktree.path.toAbsolutePath().normalize().toString()
            val isProjectRoot = projectRoot != null && normalizedPath == projectRoot
            !worktree.isMain && !isProjectRoot
        }
        e.presentation.isEnabledAndVisible = service.isGitRepository() && hasCandidate
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun isValidName(name: String): Boolean {
        if (name.isBlank()) return false
        return !name.contains('/') && !name.contains('\\')
    }

    private fun WorktreeInfo.displayString(): String {
        return buildString {
            append(displayName)
            append(" — ")
            append(path.toString())
        }
    }

    private fun notify(project: com.intellij.openapi.project.Project, title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Git Worktree")
            .createNotification(title, message, type)
            .notify(project)
    }
}
