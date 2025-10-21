package au.id.deejay.ideaworktrees.utils

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.nio.file.Path

/**
 * Utility class providing reusable worktree operations with standardized UX.
 * 
 * This class centralizes common worktree workflows (create, open, delete) to ensure
 * consistent behavior across different UI contexts (dialogs, actions, widgets).
 * 
 * All operations handle threading correctly:
 * - Git operations run on background threads via GitWorktreeService
 * - UI updates are marshaled back to EDT via invokeLater
 * - Callbacks are invoked on EDT after operations complete
 */
object WorktreeOperations {

    /**
     * Callbacks for create worktree operation.
     */
    data class CreateWorktreeCallbacks(
        /**
         * Called on EDT after successful worktree creation.
         * Use this to refresh UI state (tables, caches, etc.).
         */
        val onSuccess: (() -> Unit)? = null,
        
        /**
         * Called on EDT after worktree creation fails.
         * Use this for additional error handling beyond the default error dialog.
         */
        val onFailure: ((WorktreeOperationResult.Failure) -> Unit)? = null,
        
        /**
         * Called on EDT when user cancels the operation.
         */
        val onCancel: (() -> Unit)? = null
    )

    /**
     * Callbacks for delete worktree operation.
     */
    data class DeleteWorktreeCallbacks(
        /**
         * Called on EDT after successful worktree deletion.
         * Use this to refresh UI state (tables, caches, etc.).
         */
        val onSuccess: (() -> Unit)? = null,
        
        /**
         * Called on EDT after worktree deletion fails.
         */
        val onFailure: ((WorktreeOperationResult.Failure) -> Unit)? = null,
        
        /**
         * Called on EDT when user cancels the operation.
         */
        val onCancel: (() -> Unit)? = null
    )

    /**
     * Creates a new worktree with standardized UX flow.
     * 
     * This operation:
     * 1. Validates the target path doesn't already exist
     * 2. Calls GitWorktreeService.createWorktree on a background thread
     * 3. Handles the RequiresInitialCommit flow with user confirmation
     * 4. Shows success/error notifications
     * 5. Optionally prompts to open the new worktree
     * 6. Invokes callbacks on EDT for UI updates
     * 
     * @param project The current project
     * @param service The GitWorktreeService instance
     * @param worktreePath The path where the worktree should be created
     * @param branchName The branch name for the new worktree
     * @param promptToOpen If true, prompts user to open the new worktree after creation
     * @param modalityState The modality state for EDT callbacks (use ModalityState.stateForComponent for dialogs)
     * @param callbacks Optional callbacks for success/failure/cancel
     */
    fun createWorktree(
        project: Project,
        service: GitWorktreeService,
        worktreePath: Path,
        branchName: String,
        promptToOpen: Boolean = true,
        modalityState: ModalityState = ModalityState.nonModal(),
        callbacks: CreateWorktreeCallbacks = CreateWorktreeCallbacks()
    ) {
        // Validate path doesn't exist (on EDT)
        val worktreeFile = worktreePath.toFile()
        if (worktreeFile.exists()) {
            Messages.showErrorDialog(
                project,
                "A directory with this name already exists: $worktreePath",
                "Directory Exists"
            )
            callbacks.onCancel?.invoke()
            return
        }

        val application = ApplicationManager.getApplication()

        // Recursive function to handle initial commit flow
        lateinit var submitCreateRequest: (Boolean) -> Unit

        fun handleResult(result: WorktreeOperationResult) {
            when (result) {
                is WorktreeOperationResult.Success -> {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Git Worktree")
                        .createNotification(
                            "Worktree Created",
                            result.message,
                            NotificationType.INFORMATION
                        )
                        .notify(project)

                    callbacks.onSuccess?.invoke()

                    // Optionally prompt to open the new worktree
                    if (promptToOpen) {
                        ProjectUtil.openOrImport(worktreePath, project, false)
                    }
                }
                is WorktreeOperationResult.RequiresInitialCommit -> {
                    val response = Messages.showYesNoDialog(
                        project,
                        "The repository has no commits. Create an empty initial commit so the new worktree can be created?",
                        "Initial Commit Required",
                        Messages.getQuestionIcon()
                    )

                    if (response == Messages.YES) {
                        submitCreateRequest(true)
                    } else {
                        Messages.showInfoMessage(
                            project,
                            "Create an initial commit in the repository and try again.",
                            "Initial Commit Required"
                        )
                        callbacks.onCancel?.invoke()
                    }
                }
                is WorktreeOperationResult.Failure -> {
                    val errorMsg = result.error
                    val details = result.details
                    val fullMessage = if (details != null) {
                        "$errorMsg\n\nDetails: $details"
                    } else {
                        errorMsg
                    }

                    Messages.showErrorDialog(
                        project,
                        fullMessage,
                        "Error Creating Worktree"
                    )
                    callbacks.onFailure?.invoke(result)
                }
            }
        }

        submitCreateRequest = { allowInitialCommit: Boolean ->
            service.createWorktree(
                worktreePath,
                branchName,
                createBranch = true,
                allowCreateInitialCommit = allowInitialCommit
            ).whenComplete { result, error ->
                application.invokeLater({
                    when {
                        error != null -> {
                            Messages.showErrorDialog(
                                project,
                                "Failed to create worktree: ${error.message ?: "Unknown error"}",
                                "Error Creating Worktree"
                            )
                            callbacks.onFailure?.invoke(
                                WorktreeOperationResult.Failure(
                                    error = error.message ?: "Unknown error"
                                )
                            )
                        }
                        result != null -> handleResult(result)
                        else -> {
                            Messages.showErrorDialog(
                                project,
                                "Failed to create worktree: Unknown error",
                                "Error Creating Worktree"
                            )
                            callbacks.onFailure?.invoke(
                                WorktreeOperationResult.Failure(error = "Unknown error")
                            )
                        }
                    }
                }, modalityState)
            }
        }

        // Start the create request
        submitCreateRequest(false)
    }

    /**
     * Opens a worktree in a new window with optional confirmation.
     * 
     * @param project The current project
     * @param worktree The worktree to open
     * @param confirmBeforeOpen If true, shows a confirmation dialog before opening
     */
    fun openWorktree(
        project: Project,
        worktree: WorktreeInfo,
        confirmBeforeOpen: Boolean = true
    ) {
        if (confirmBeforeOpen) {
            val result = Messages.showYesNoCancelDialog(
                project,
                "Open worktree '${worktree.displayName}' in a new window?",
                "Open Worktree",
                Messages.getQuestionIcon()
            )

            if (result != Messages.YES) {
                return
            }
        }

        try {
            ProjectUtil.openOrImport(worktree.path, project, false)
        } catch (e: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Git Worktree")
                .createNotification(
                    "Failed to open worktree: ${e.message}",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }

    /**
     * Deletes a worktree with confirmation and standardized UX flow.
     * 
     * This operation:
     * 1. Shows a confirmation dialog with strong warning
     * 2. Handles locked worktrees (prompts for force deletion)
     * 3. Calls GitWorktreeService.deleteWorktree on a background thread
     * 4. Shows success/error notifications
     * 5. Invokes callbacks on EDT for UI updates
     * 
     * @param project The current project
     * @param service The GitWorktreeService instance
     * @param worktree The worktree to delete
     * @param modalityState The modality state for EDT callbacks
     * @param callbacks Optional callbacks for success/failure/cancel
     */
    fun deleteWorktree(
        project: Project,
        service: GitWorktreeService,
        worktree: WorktreeInfo,
        modalityState: ModalityState = ModalityState.nonModal(),
        callbacks: DeleteWorktreeCallbacks = DeleteWorktreeCallbacks()
    ) {
        // Confirm deletion (on EDT)
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete the worktree at:\n${worktree.path}\n\n" +
                "This will remove the worktree directory and all its contents.\n\n" +
                "<b>Uncommitted changes will be lost!</b> " +
                "This operation cannot be undone.",
            "Confirm Delete Worktree",
            Messages.getWarningIcon()
        )

        if (result != Messages.YES) {
            callbacks.onCancel?.invoke()
            return
        }

        // Check if worktree is locked and prompt for force deletion
        val force = if (worktree.isLocked) {
            val forceResult = Messages.showYesNoDialog(
                project,
                "The worktree is locked. Force deletion?",
                "Worktree Locked",
                Messages.getWarningIcon()
            )
            if (forceResult != Messages.YES) {
                callbacks.onCancel?.invoke()
                return
            }
            true
        } else {
            false
        }

        val application = ApplicationManager.getApplication()

        // Execute deletion on background thread
        service.deleteWorktree(worktree.path, force)
            .whenComplete { deleteResult, error ->
                application.invokeLater({
                    if (error != null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to delete worktree: ${error.message ?: "Unknown error"}",
                            "Error Deleting Worktree"
                        )
                        callbacks.onFailure?.invoke(
                            WorktreeOperationResult.Failure(error = error.message ?: "Unknown error")
                        )
                        return@invokeLater
                    }

                    if (deleteResult == null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to delete worktree: Unknown error",
                            "Error Deleting Worktree"
                        )
                        callbacks.onFailure?.invoke(
                            WorktreeOperationResult.Failure(error = "Unknown error")
                        )
                        return@invokeLater
                    }

                    if (deleteResult.isSuccess) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Worktree Deleted",
                                deleteResult.successMessage() ?: "Deleted worktree successfully",
                                NotificationType.INFORMATION
                            )
                            .notify(project)

                        callbacks.onSuccess?.invoke()
                    } else {
                        val errorMsg = deleteResult.errorMessage() ?: "Failed to delete worktree"
                        val details = deleteResult.errorDetails()
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
                        callbacks.onFailure?.invoke(
                            WorktreeOperationResult.Failure(error = errorMsg, details = details)
                        )
                    }
                }, modalityState)
            }
    }
}

