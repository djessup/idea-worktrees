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
 * Action to delete a Git worktree.
 */
class DeleteWorktreeAction : AnAction(), DumbAware {

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

                    val deletableWorktrees = worktrees.filter { it.path != currentWorktree?.path && !it.isMain }

                    if (deletableWorktrees.isEmpty()) {
                        Messages.showInfoMessage(
                            project,
                            "No deletable worktrees available. You cannot delete the current worktree or the main repository.",
                            "No Deletable Worktrees"
                        )
                        return@invokeLater
                    }

                    val popup = JBPopupFactory.getInstance().createListPopup(
                        object : BaseListPopupStep<WorktreeInfo>("Delete Worktree", deletableWorktrees) {
                            override fun getTextFor(value: WorktreeInfo): String {
                                return "${value.displayName} (${value.name})"
                            }

                            override fun onChosen(selectedValue: WorktreeInfo, finalChoice: Boolean): PopupStep<*>? {
                                if (finalChoice) {
                                    deleteWorktree(project, service, selectedValue)
                                }
                                return PopupStep.FINAL_CHOICE
                            }
                        }
                    )

                    popup.showCenteredInCurrentWindow(project)
                }, ModalityState.nonModal())
            }
    }

    private fun deleteWorktree(
        project: com.intellij.openapi.project.Project,
        service: GitWorktreeService,
        worktree: WorktreeInfo
    ) {
        WorktreeOperations.deleteWorktree(
            project = project,
            service = service,
            worktree = worktree,
            modalityState = ModalityState.nonModal()
        )
    }

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

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
