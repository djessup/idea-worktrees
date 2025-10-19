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
import java.nio.file.Paths
import java.nio.file.Files

/**
 * Action that renames an existing Git worktree by moving it to a new directory.
 */
class RenameWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)

        WorktreeAsyncOperations.loadWorktrees(
            project = project,
            service = service,
            onSuccess = { worktrees ->
                val projectRoot = project.basePath?.let { Paths.get(it).toAbsolutePath().normalize().toString() }

                val candidates = worktrees.filter { worktree ->
                    val normalizedPath = worktree.path.toAbsolutePath().normalize().toString()
                    val isProjectRoot = projectRoot != null && normalizedPath == projectRoot
                    !worktree.isMain && !isProjectRoot
                }

                if (candidates.isEmpty()) {
                    Messages.showInfoMessage(
                        project,
                        "No additional worktrees available to rename. The main worktree and the current project worktree cannot be renamed.",
                        "Rename Worktree"
                    )
                    return@loadWorktrees
                }

                val displayNames = candidates.map { it.displayString() }.toTypedArray()
                val selected = Messages.showEditableChooseDialog(
                    "Select the worktree you would like to rename:",
                    "Rename Worktree",
                    Messages.getQuestionIcon(),
                    displayNames,
                    displayNames.first(),
                    null
                ) ?: return@loadWorktrees

                val worktree = candidates[displayNames.indexOf(selected)]
                val newName = Messages.showInputDialog(
                    project,
                    "Enter a new name for '${worktree.displayName}':",
                    "Rename Worktree",
                    Messages.getQuestionIcon(),
                    worktree.path.fileName?.toString() ?: "",
                    null
                )?.trim() ?: return@loadWorktrees

                if (!isValidName(newName)) {
                    Messages.showErrorDialog(
                        project,
                        "The provided name is invalid. Please avoid path separators or empty names.",
                        "Rename Worktree"
                    )
                    return@loadWorktrees
                }

                val parent = worktree.path.parent
                if (parent == null) {
                    Messages.showErrorDialog(
                        project,
                        "Cannot determine the parent directory for the selected worktree.",
                        "Rename Worktree"
                    )
                    return@loadWorktrees
                }

                val newPath = parent.resolve(newName)
                if (Files.exists(newPath)) {
                    Messages.showErrorDialog(
                        project,
                        "A directory with the name '$newName' already exists.",
                        "Rename Worktree"
                    )
                    return@loadWorktrees
                }

                service.moveWorktree(worktree.path, newPath).thenAccept { result ->
                    WorktreeResultHandler.handle(
                        project = project,
                        result = result,
                        successTitle = "Worktree Renamed",
                        errorTitle = "Rename Worktree"
                    )
                }
            },
            onError = { error ->
                Messages.showErrorDialog(
                    project,
                    "Failed to list worktrees: ${error.message ?: "Unknown error"}",
                    "Rename Worktree"
                )
            }
        )
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
}
