package au.id.deejay.ideaworktrees.utils

import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

/**
 * Centralized utility for handling WorktreeOperationResult instances.
 *
 * This object provides standardized handling of worktree operation results, ensuring
 * consistent user experience across all worktree operations. It handles success notifications,
 * error messages with details, and special cases like initial commit requirements.
 *
 * All methods execute on the EDT and are safe to call from any thread.
 *
 * Usage example:
 * ```kotlin
 * service.createWorktree(path, branch).whenComplete { result, error ->
 *     ApplicationManager.getApplication().invokeLater({
 *         if (error != null) {
 *             WorktreeNotifications.showError(project, "Failed", error.message ?: "Unknown error")
 *             return@invokeLater
 *         }
 *         WorktreeResultHandler.handle(
 *             project = project,
 *             result = result,
 *             successTitle = "Worktree Created",
 *             promptToOpen = true,
 *             worktreePath = path,
 *             onInitialCommitRequired = { handleInitialCommit(it) }
 *         )
 *     }, ModalityState.nonModal())
 * }
 * ```
 */
object WorktreeResultHandler {
    /**
     * Handles a WorktreeOperationResult with standardized behavior.
     *
     * This method processes the result and takes appropriate action based on the result type:
     * - Success: Shows success notification, optionally prompts to open worktree
     * - Failure: Shows error notification with details
     * - RequiresInitialCommit: Invokes custom handler or shows default info message
     *
     * @param project The project context
     * @param result The operation result to handle
     * @param successTitle The title for success notifications (default: "Operation Successful")
     * @param errorTitle The title for error notifications (default: "Operation Failed")
     * @param promptToOpen Whether to prompt the user to open the worktree after success (default: false)
     * @param worktreePath The path to the worktree (required if promptToOpen is true)
     * @param onInitialCommitRequired Optional custom handler for RequiresInitialCommit results
     * @throws IllegalArgumentException if promptToOpen is true but worktreePath is null
     */
    fun handle(
        project: Project,
        result: WorktreeOperationResult,
        successTitle: String = "Operation Successful",
        errorTitle: String = "Operation Failed",
        promptToOpen: Boolean = false,
        worktreePath: Path? = null,
        onInitialCommitRequired: ((WorktreeOperationResult.RequiresInitialCommit) -> Unit)? = null
    ) {
        // Validate parameters
        if (promptToOpen && worktreePath == null) {
            throw IllegalArgumentException("worktreePath must be provided when promptToOpen is true")
        }

        when (result) {
            is WorktreeOperationResult.Success -> {
                WorktreeNotifications.showSuccess(project, successTitle, result.message)

                if (promptToOpen && worktreePath != null) {
                    promptToOpenWorktree(project, worktreePath)
                }
            }

            is WorktreeOperationResult.Failure -> {
                WorktreeNotifications.showErrorWithDetails(
                    project,
                    errorTitle,
                    result.error,
                    result.details
                )
            }

            is WorktreeOperationResult.RequiresInitialCommit -> {
                if (onInitialCommitRequired != null) {
                    onInitialCommitRequired(result)
                } else {
                    WorktreeNotifications.showInfo(
                        project,
                        "Initial Commit Required",
                        result.message
                    )
                }
            }
        }
    }

    /**
     * Prompts the user to open a worktree in a new window.
     *
     * Shows a Yes/No dialog asking if the user wants to open the worktree.
     * If the user selects Yes, attempts to open the worktree using ProjectUtil.openOrImport.
     * If opening fails, shows an error notification.
     *
     * This method must be called on the EDT.
     *
     * @param project The project context
     * @param path The path to the worktree to open
     */
    fun promptToOpenWorktree(project: Project, path: Path) {
        val response = Messages.showYesNoDialog(
            project,
            "Would you like to open the new worktree in a new window?",
            "Open Worktree",
            Messages.getQuestionIcon()
        )

        if (response == Messages.YES) {
            try {
                ProjectUtil.openOrImport(path, project, false)
            } catch (e: Exception) {
                WorktreeNotifications.showError(
                    project,
                    "Failed to Open Worktree",
                    e.message ?: "Unknown error occurred while opening worktree"
                )
            }
        }
    }
}

