package au.id.deejay.ideaworktrees.utils

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project

/**
 * Centralized utility for async worktree loading operations with correct EDT threading.
 *
 * This object provides standardized async operation patterns for loading worktrees,
 * ensuring that all callbacks are properly marshalled to the EDT and that error
 * handling is consistent across the plugin.
 *
 * All async operations run on background threads and invoke callbacks on the EDT
 * using ModalityState.nonModal().
 *
 * Threading model:
 * ```
 * [Caller Thread (EDT)]
 *     ↓
 * WorktreeAsyncOperations.loadWorktreesWithCurrent()
 *     ↓
 * [Background Thread] service.listWorktrees() + service.getCurrentWorktree()
 *     ↓
 * whenComplete { result, error -> ... }
 *     ↓
 * [EDT via invokeLater] onSuccess(worktrees, current) or onError(exception)
 * ```
 *
 * Usage example:
 * ```kotlin
 * WorktreeAsyncOperations.loadWorktreesWithCurrent(
 *     project = project,
 *     service = service,
 *     onSuccess = { worktrees, current ->
 *         // Update UI with worktrees (already on EDT)
 *         updateTable(worktrees, current)
 *     },
 *     onError = { error ->
 *         // Show custom error message (already on EDT)
 *         Messages.showErrorDialog(project, error.message, "Error")
 *     }
 * )
 * ```
 */
object WorktreeAsyncOperations {
    /**
     * Loads both the list of worktrees and the current worktree in parallel.
     *
     * This method executes service.listWorktrees() and service.getCurrentWorktree()
     * in parallel using thenCombine, then invokes the appropriate callback on the EDT.
     *
     * @param project The project context
     * @param service The GitWorktreeService instance
     * @param onSuccess Callback invoked on EDT with (worktrees, current) when both operations succeed
     * @param onError Optional callback invoked on EDT with the exception if either operation fails.
     *                If not provided, shows a default error notification.
     */
    fun loadWorktreesWithCurrent(
        project: Project,
        service: GitWorktreeService,
        onSuccess: (worktrees: List<WorktreeInfo>, current: WorktreeInfo?) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        service.listWorktrees()
            .thenCombine(service.getCurrentWorktree()) { worktrees, current -> worktrees to current }
            .whenComplete { result, error ->
                ApplicationManager.getApplication().invokeLater({
                    if (error != null || result == null) {
                        val exception = error ?: IllegalStateException("Result was null")
                        if (onError != null) {
                            onError(exception)
                        } else {
                            WorktreeNotifications.showError(
                                project,
                                "Failed to Load Worktrees",
                                exception.message ?: "Unknown error"
                            )
                        }
                    } else {
                        val (worktrees, current) = result
                        onSuccess(worktrees, current)
                    }
                }, ModalityState.nonModal())
            }
    }

    /**
     * Loads the list of worktrees asynchronously.
     *
     * This method executes service.listWorktrees() and invokes the appropriate
     * callback on the EDT when the operation completes.
     *
     * @param project The project context
     * @param service The GitWorktreeService instance
     * @param onSuccess Callback invoked on EDT with the list of worktrees when the operation succeeds
     * @param onError Optional callback invoked on EDT with the exception if the operation fails.
     *                If not provided, shows a default error notification.
     */
    fun loadWorktrees(
        project: Project,
        service: GitWorktreeService,
        onSuccess: (worktrees: List<WorktreeInfo>) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    ) {
        service.listWorktrees()
            .whenComplete { worktrees, error ->
                ApplicationManager.getApplication().invokeLater({
                    if (error != null || worktrees == null) {
                        val exception = error ?: IllegalStateException("Worktrees list was null")
                        if (onError != null) {
                            onError(exception)
                        } else {
                            WorktreeNotifications.showError(
                                project,
                                "Failed to Load Worktrees",
                                exception.message ?: "Unknown error"
                            )
                        }
                    } else {
                        onSuccess(worktrees)
                    }
                }, ModalityState.nonModal())
            }
    }
}

