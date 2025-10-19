package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.services.WorktreeChangeListener
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
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import org.jetbrains.annotations.TestOnly

/**
 * Status bar widget that displays the current Git worktree.
 */
class WorktreeStatusBarWidget(project: Project) : EditorBasedStatusBarPopup(project, false) {

    companion object {
        const val ID = "au.id.deejay.ideaworktrees.WorktreeStatusBarWidget"
        private const val EMPTY_TEXT = "No Worktree"
        // Visible for tests: compute suggested directory name for new worktrees
        @JvmStatic
        fun suggestDirectoryName(projectPath: java.nio.file.Path?, branchName: String): String {
            val projectFolderName = projectPath?.fileName?.toString() ?: "project"
            // Replace any characters that are unsafe in file names (including slashes) with hyphens
            // Preserve underscores, dots, and hyphens as they are valid in directory names
            val sanitizedBranch = branchName.replace(Regex("[^A-Za-z0-9._-]+"), "-")
                .trim { it == '-' || it == '.' }
            return "$projectFolderName-$sanitizedBranch"
        }
    }

    private val service: GitWorktreeService = GitWorktreeService.getInstance(project)

    // Cache for worktree information to avoid blocking calls in ReadAction
    private val cachedWorktrees = AtomicReference<List<WorktreeInfo>>(emptyList())
    private val cachedCurrentWorktree = AtomicReference<WorktreeInfo?>(null)

    init {
        project.messageBus.connect(this).apply {
            subscribe(
                ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED,
                VcsMappingListener { updateCacheAsync() }
            )
            subscribe(
                GitWorktreeService.WORKTREE_TOPIC,
                WorktreeChangeListener { updateCacheAsync() }
            )
            subscribe(
                GitRepository.GIT_REPO_CHANGE,
                GitRepositoryChangeListener { repository ->
                    if (repository.project == project) {
                        updateCacheAsync()
                    }
                }
            )
        }

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
        val application = ApplicationManager.getApplication()

        if (!service.isGitRepository()) {
            cachedWorktrees.set(emptyList())
            cachedCurrentWorktree.set(null)
            application.invokeLater({
                update()
                onComplete?.invoke()
            }, ModalityState.nonModal())
            return
        }

        service.listWorktrees()
            .thenCombine(service.getCurrentWorktree()) { loaded, current -> loaded to current }
            .whenComplete { result, error ->
                if (error != null || result == null) {
                    cachedWorktrees.set(emptyList())
                    cachedCurrentWorktree.set(null)
                } else {
                    val (loadedWorktrees, current) = result
                    cachedWorktrees.set(loadedWorktrees)
                    cachedCurrentWorktree.set(current)
                }

                application.invokeLater({
                    update()
                    onComplete?.invoke()
                }, ModalityState.nonModal())
            }
    }

    @TestOnly
    fun refreshCacheForTest(onComplete: (() -> Unit)? = null) {
        updateCacheAsync(onComplete)
    }

    @TestOnly
    fun getCachedWorktreesForTest(): List<WorktreeInfo> = cachedWorktrees.get()

    @TestOnly
    fun getCachedCurrentWorktreeForTest(): WorktreeInfo? = cachedCurrentWorktree.get()

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

            val projectPath = Paths.get(project.basePath ?: return)
            // Suggest directory name using the project folder name to keep naming consistent
            val defaultDirName = suggestDirectoryName(projectPath, branchName)

            val dirName = Messages.showInputDialog(
                project,
                "Enter the directory name for the new worktree:",
                "Create New Worktree",
                Messages.getQuestionIcon(),
                defaultDirName,
                null
            ) ?: return

            val parentPath = projectPath.parent
            if (parentPath == null) {
                Messages.showErrorDialog(
                    project,
                    "Cannot determine a parent directory for the new worktree. Please create one manually.",
                    "Invalid Worktree Location"
                )
                return
            }
            val worktreePath = parentPath.resolve(dirName)

            val service = GitWorktreeService.getInstance(project)
            val application = ApplicationManager.getApplication()
            lateinit var submitCreateRequest: (Boolean) -> Unit

            fun handleResult(result: WorktreeOperationResult) {
                when (result) {
                    is WorktreeOperationResult.Success -> {
                        widget.updateCacheAsync()
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Worktree Created",
                                result.message,
                                NotificationType.INFORMATION
                            )
                            .notify(project)

                        val openResult = Messages.showYesNoDialog(
                            project,
                            "Do you want to open the new worktree in a new window?",
                            "Open Worktree",
                            Messages.getQuestionIcon()
                        )

                        if (openResult == Messages.YES) {
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
                        }
                    }
                    is WorktreeOperationResult.Failure -> {
                        val errorMsg = result.error
                        val details = result.details
                        val fullMessage = if (details != null && details.isNotBlank()) {
                            "$errorMsg\n\nDetails: $details"
                        } else {
                            errorMsg
                        }

                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Error Creating Worktree",
                                fullMessage,
                                NotificationType.ERROR
                            )
                            .notify(project)
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
                        if (error != null) {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("Git Worktree")
                                .createNotification(
                                    "Error Creating Worktree",
                                    error.message ?: "Unknown error",
                                    NotificationType.ERROR
                                )
                                .notify(project)
                            return@invokeLater
                        }

                        if (result != null) {
                            handleResult(result)
                        } else {
                            NotificationGroupManager.getInstance()
                                .getNotificationGroup("Git Worktree")
                                .createNotification(
                                    "Error Creating Worktree",
                                    "Failed to create worktree: Unknown error",
                                    NotificationType.ERROR
                                )
                                .notify(project)
                        }
                    }, ModalityState.nonModal())
                }
            }

            submitCreateRequest(false)
        }

        private fun manageWorktrees() {
            val service = GitWorktreeService.getInstance(project)
            val dialog = ManageWorktreesDialog(project, service)
            dialog.show()
        }

        override fun isSelectable(value: WorktreePopupItem): Boolean {
            return value !is WorktreePopupItem.Separator
        }
    }
}
