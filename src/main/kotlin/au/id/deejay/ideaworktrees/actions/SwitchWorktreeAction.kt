package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.utils.WorktreeOperations
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

    /**
     * Presents a popup of available worktrees and opens the selected entry.
     */
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)
        val application = ApplicationManager.getApplication()

        service.listWorktrees()
            .thenCombine(service.getCurrentWorktree()) { worktrees, current -> worktrees to current }
            .whenComplete { result, error ->
                application.invokeLater({
                    if (error != null || result == null) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Failed to list worktrees: ${error?.message ?: "Unknown error"}",
                                NotificationType.ERROR
                            )
                            .notify(project)
                        return@invokeLater
                    }

                    val (worktrees, currentWorktree) = result

                    if (worktrees.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No worktrees found in this repository.",
                            "No Worktrees"
                        )
                        return@invokeLater
                    }

                    val otherWorktrees = worktrees.filter { it.path != currentWorktree?.path }

                    if (otherWorktrees.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No other worktrees available to switch to.",
                            "No Other Worktrees"
                        )
                        return@invokeLater
                    }

                    val popup = JBPopupFactory.getInstance().createListPopup(
                        object : BaseListPopupStep<WorktreeInfo>("Switch to Worktree", otherWorktrees) {
                            /**
                             * Formats each entry with both display and directory name.
                             */
                            override fun getTextFor(value: WorktreeInfo): String {
                                return "${value.displayName} (${value.name})"
                            }

                            /**
                             * Initiates the switch when a worktree is chosen.
                             */
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
            }
    }

    /**
     * Prompts the user to confirm switching before opening the selected worktree.
     */
    private fun switchToWorktree(project: com.intellij.openapi.project.Project, worktree: WorktreeInfo) {
        // Check if worktree directory exists before prompting
        val worktreeFile = worktree.path.toFile()
        if (!worktreeFile.exists()) {
            Messages.showErrorDialog(
                project,
                "Worktree directory does not exist: ${worktree.path}",
                "Directory Not Found"
            )
            return
        }

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

        WorktreeOperations.openWorktree(
            project = project,
            worktree = worktree,
            confirmBeforeOpen = false
        )
    }

    /**
     * Keeps the action disabled when the project lacks Git metadata.
     */
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

    /**
     * Schedules update calculations on a background thread.
     */
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
