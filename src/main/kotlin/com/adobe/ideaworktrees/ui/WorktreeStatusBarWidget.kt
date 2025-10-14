package com.adobe.ideaworktrees.ui

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import javax.swing.Icon

/**
 * Status bar widget that displays the current Git worktree.
 */
class WorktreeStatusBarWidget(project: Project) : EditorBasedStatusBarPopup(project, false) {

    companion object {
        const val ID = "com.adobe.ideaworktrees.WorktreeStatusBarWidget"
        private const val EMPTY_TEXT = "No Worktree"
    }

    private val service: GitWorktreeService = GitWorktreeService.getInstance(project)

    override fun ID(): String = ID

    override fun createInstance(project: Project): StatusBarWidget {
        return WorktreeStatusBarWidget(project)
    }

    override fun getWidgetState(file: com.intellij.openapi.vfs.VirtualFile?): WidgetState {
        if (!service.isGitRepository()) {
            return WidgetState.HIDDEN
        }

        val currentWorktree = service.getCurrentWorktree()
        val text = currentWorktree?.displayName ?: EMPTY_TEXT
        val tooltip = currentWorktree?.let {
            buildTooltip(it)
        } ?: "Not in a Git worktree"

        return WidgetState(tooltip, text, true)
    }

    override fun createPopup(context: com.intellij.openapi.actionSystem.DataContext): ListPopup? {
        if (!service.isGitRepository()) {
            return null
        }

        val worktrees = service.listWorktrees()
        val currentWorktree = service.getCurrentWorktree()

        val items = mutableListOf<WorktreePopupItem>()

        // Add worktree items
        worktrees.forEach { worktree ->
            val isCurrent = worktree.path == currentWorktree?.path
            items.add(WorktreePopupItem.WorktreeItem(worktree, isCurrent))
        }

        // Add separator and actions
        if (items.isNotEmpty()) {
            items.add(WorktreePopupItem.Separator)
        }
        items.add(WorktreePopupItem.Action("Create New Worktree..."))
        items.add(WorktreePopupItem.Action("Manage Worktrees..."))

        return JBPopupFactory.getInstance().createListPopup(
            WorktreePopupStep(items, project)
        )
    }

    private fun buildTooltip(worktree: WorktreeInfo): String {
        return buildString {
            append("Worktree: ${worktree.name}")
            if (worktree.branch != null) {
                append("\nBranch: ${worktree.branch}")
            }
            append("\nCommit: ${worktree.commit.take(7)}")
            append("\nPath: ${worktree.path}")
        }
    }

    /**
     * Sealed class representing items in the worktree popup menu.
     */
    private sealed class WorktreePopupItem {
        data class WorktreeItem(val worktree: WorktreeInfo, val isCurrent: Boolean) : WorktreePopupItem()
        data class Action(val text: String) : WorktreePopupItem()
        object Separator : WorktreePopupItem()
    }

    /**
     * Popup step for the worktree selection menu.
     */
    private class WorktreePopupStep(
        items: List<WorktreePopupItem>,
        private val project: Project
    ) : BaseListPopupStep<WorktreePopupItem>("Git Worktrees", items) {

        override fun getTextFor(value: WorktreePopupItem): String {
            return when (value) {
                is WorktreePopupItem.WorktreeItem -> {
                    val prefix = if (value.isCurrent) "✓ " else "  "
                    val name = value.worktree.displayName
                    val path = value.worktree.name
                    "$prefix$name ($path)"
                }
                is WorktreePopupItem.Action -> value.text
                is WorktreePopupItem.Separator -> ""
            }
        }

        override fun getIconFor(value: WorktreePopupItem): Icon? {
            // TODO: Add icons for worktrees and actions
            return null
        }

        override fun getSeparatorAbove(value: WorktreePopupItem): com.intellij.openapi.ui.popup.ListSeparator? {
            return if (value is WorktreePopupItem.Separator) {
                com.intellij.openapi.ui.popup.ListSeparator()
            } else {
                null
            }
        }

        override fun hasSubstep(selectedValue: WorktreePopupItem): Boolean {
            return false
        }

        override fun onChosen(selectedValue: WorktreePopupItem, finalChoice: Boolean): PopupStep<*>? {
            when (selectedValue) {
                is WorktreePopupItem.WorktreeItem -> {
                    if (!selectedValue.isCurrent) {
                        // TODO: Implement worktree switching
                        // For now, just show a notification
                        com.intellij.notification.NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Worktree switching not yet implemented",
                                com.intellij.notification.NotificationType.INFORMATION
                            )
                            .notify(project)
                    }
                }
                is WorktreePopupItem.Action -> {
                    // TODO: Implement actions
                    com.intellij.notification.NotificationGroupManager.getInstance()
                        .getNotificationGroup("Git Worktree")
                        .createNotification(
                            "Action '${selectedValue.text}' not yet implemented",
                            com.intellij.notification.NotificationType.INFORMATION
                        )
                        .notify(project)
                }
                is WorktreePopupItem.Separator -> {
                    // Do nothing for separator
                }
            }
            return PopupStep.FINAL_CHOICE
        }

        override fun isSelectable(value: WorktreePopupItem): Boolean {
            return value !is WorktreePopupItem.Separator
        }
    }
}


