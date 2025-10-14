package com.adobe.ideaworktrees.ui

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
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

    // Cache for worktree information to avoid blocking calls in ReadAction
    private val cachedWorktrees = AtomicReference<List<WorktreeInfo>>(emptyList())
    private val cachedCurrentWorktree = AtomicReference<WorktreeInfo?>(null)

    init {
        // Initialize cache asynchronously
        updateCacheAsync()
    }

    override fun ID(): String = ID

    override fun createInstance(project: Project): StatusBarWidget {
        return WorktreeStatusBarWidget(project)
    }

    override fun getWidgetState(file: com.intellij.openapi.vfs.VirtualFile?): WidgetState {
        if (!service.isGitRepository()) {
            return WidgetState.HIDDEN
        }

        // Use cached value to avoid blocking
        val currentWorktree = cachedCurrentWorktree.get()
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

        // Refresh cache before showing popup
        updateCacheAsync {
            // Cache will be updated, but we'll use current values for this popup
        }

        val worktrees = cachedWorktrees.get()
        val currentWorktree = cachedCurrentWorktree.get()

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
            WorktreePopupStep(items, project, this)
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
     * Updates the worktree cache asynchronously on a background thread.
     */
    private fun updateCacheAsync(onComplete: (() -> Unit)? = null) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (service.isGitRepository()) {
                    val worktrees = service.listWorktrees()
                    val current = service.getCurrentWorktree()

                    cachedWorktrees.set(worktrees)
                    cachedCurrentWorktree.set(current)

                    // Update the widget display on EDT
                    ApplicationManager.getApplication().invokeLater({
                        update()
                        onComplete?.invoke()
                    }, ModalityState.nonModal())
                }
            } catch (e: Exception) {
                // Silently ignore errors - widget will show cached or empty state
            }
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
        private val project: Project,
        private val widget: WorktreeStatusBarWidget
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
                        switchToWorktree(selectedValue.worktree)
                    }
                }
                is WorktreePopupItem.Action -> {
                    handleAction(selectedValue.text)
                }
                is WorktreePopupItem.Separator -> {
                    // Do nothing for separator
                }
            }
            return PopupStep.FINAL_CHOICE
        }

        private fun switchToWorktree(worktree: WorktreeInfo) {
            ApplicationManager.getApplication().invokeLater {
                try {
                    ProjectUtil.openOrImport(worktree.path, project, false)
                } catch (e: Exception) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Git Worktree")
                        .createNotification(
                            "Failed to switch to worktree: ${e.message}",
                            NotificationType.ERROR
                        )
                        .notify(project)
                }
            }
        }

        private fun handleAction(actionText: String) {
            ApplicationManager.getApplication().invokeLater {
                when (actionText) {
                    "Create New Worktree..." -> createNewWorktree()
                    "Manage Worktrees..." -> manageWorktrees()
                }
            }
        }

        private fun createNewWorktree() {
            // Get user input on EDT
            val branchName = Messages.showInputDialog(
                project,
                "Enter the branch name for the new worktree:",
                "Create New Worktree",
                Messages.getQuestionIcon()
            ) ?: return

            val dirName = Messages.showInputDialog(
                project,
                "Enter the directory name for the new worktree:",
                "Create New Worktree",
                Messages.getQuestionIcon(),
                branchName,
                null
            ) ?: return

            val projectPath = Paths.get(project.basePath ?: return)
            val parentPath = projectPath.parent
            val worktreePath = parentPath.resolve(dirName)

            // Execute Git command on background thread
            ApplicationManager.getApplication().executeOnPooledThread {
                val service = GitWorktreeService.getInstance(project)
                try {
                    service.createWorktree(worktreePath, branchName, true)

                    // Refresh the cache after creating worktree
                    widget.updateCacheAsync()

                    // Show success notification and ask about opening on EDT
                    ApplicationManager.getApplication().invokeLater({
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Worktree created successfully at: $worktreePath",
                                NotificationType.INFORMATION
                            )
                            .notify(project)

                        // Ask if user wants to open the new worktree
                        val result = Messages.showYesNoDialog(
                            project,
                            "Worktree created successfully. Do you want to open it in a new window?",
                            "Open Worktree",
                            Messages.getQuestionIcon()
                        )

                        if (result == Messages.YES) {
                            ProjectUtil.openOrImport(worktreePath, project, false)
                        }
                    }, ModalityState.nonModal())
                } catch (e: Exception) {
                    // Show error notification on EDT
                    ApplicationManager.getApplication().invokeLater({
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Failed to create worktree: ${e.message}",
                                NotificationType.ERROR
                            )
                            .notify(project)
                    }, ModalityState.nonModal())
                }
            }
        }

        private fun manageWorktrees() {
            // Execute Git commands on background thread
            ApplicationManager.getApplication().executeOnPooledThread {
                val service = GitWorktreeService.getInstance(project)
                try {
                    val worktrees = service.listWorktrees()

                    // Show dialog on EDT
                    ApplicationManager.getApplication().invokeLater({
                        if (worktrees.isEmpty()) {
                            Messages.showInfoMessage(
                                project,
                                "No worktrees found in this repository.",
                                "No Worktrees"
                            )
                            return@invokeLater
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

        override fun isSelectable(value: WorktreePopupItem): Boolean {
            return value !is WorktreePopupItem.Separator
        }
    }
}


