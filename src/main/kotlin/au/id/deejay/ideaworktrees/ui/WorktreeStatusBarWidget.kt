package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.services.WorktreeChangeListener
import au.id.deejay.ideaworktrees.utils.WorktreeOperations
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

    /**
     * @return Stable identifier used by the status bar framework.
     */
    override fun ID(): String = ID

    /**
     * Creates a new instance for additional projects.
     */
    override fun createInstance(project: Project): StatusBarWidget {
        return WorktreeStatusBarWidget(project)
    }

    /**
     * Defines the widget's text and tooltip based on the cached worktree info.
     */
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

    /**
     * Builds the popup menu listing worktrees and quick actions.
     */
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

    /**
     * Creates the tooltip text displayed when hovering over the widget.
     */
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

    /**
     * Testing helper that refreshes the cache and invokes the supplied callback when finished.
     */
    @TestOnly
    fun refreshCacheForTest(onComplete: (() -> Unit)? = null) {
        updateCacheAsync(onComplete)
    }

    /**
     * @return Snapshot of cached worktrees for assertions.
     */
    @TestOnly
    fun getCachedWorktreesForTest(): List<WorktreeInfo> = cachedWorktrees.get()

    /**
     * @return Cached current worktree for test assertions.
     */
    @TestOnly
    fun getCachedCurrentWorktreeForTest(): WorktreeInfo? = cachedCurrentWorktree.get()

    /**
     * Sealed class representing items in the worktree popup menu.
     */
    private sealed class WorktreePopupItem {
        /**
         * Worktree entry that may optionally represent the current worktree.
         */
        data class WorktreeItem(val worktree: WorktreeInfo, val isCurrent: Boolean) : WorktreePopupItem()

        /**
         * Simple action entry rendered in the popup list.
         */
        data class Action(val text: String) : WorktreePopupItem()

        /**
         * Visual separator used to split worktree items from actions.
         */
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

        /**
         * Formats each popup entry for display.
         */
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

        /**
         * Provides an icon for each popup entry (currently none).
         */
        override fun getIconFor(value: WorktreePopupItem): Icon? {
            // TODO: Add icons for worktrees and actions
            return null
        }

        /**
         * Inserts separators where the sentinel value is encountered.
         */
        override fun getSeparatorAbove(value: WorktreePopupItem): com.intellij.openapi.ui.popup.ListSeparator? {
            return if (value is WorktreePopupItem.Separator) {
                com.intellij.openapi.ui.popup.ListSeparator()
            } else {
                null
            }
        }

        /**
         * Indicates that the popup has no nested substeps.
         */
        override fun hasSubstep(selectedValue: WorktreePopupItem): Boolean {
            return false
        }

        /**
         * Handles selection events, triggering worktree switches or actions.
         */
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

        /**
         * Opens the selected worktree in a new window.
         */
        private fun switchToWorktree(worktree: WorktreeInfo) {
            ApplicationManager.getApplication().invokeLater {
                WorktreeOperations.openWorktree(
                    project = project,
                    worktree = worktree,
                    confirmBeforeOpen = false
                )
            }
        }

        /**
         * Routes action entries to the appropriate handler.
         */
        private fun handleAction(actionText: String) {
            ApplicationManager.getApplication().invokeLater {
                when (actionText) {
                    "Create New Worktree..." -> createNewWorktree()
                    "Manage Worktrees..." -> manageWorktrees()
                }
            }
        }

        /**
         * Launches the new worktree workflow from the popup.
         */
        private fun createNewWorktree() {
            val projectPath = Paths.get(project.basePath ?: return)
            val parentPath = projectPath.parent
            if (parentPath == null) {
                Messages.showErrorDialog(
                    project,
                    "Cannot determine a parent directory for the new worktree. Please create one manually.",
                    "Invalid Worktree Location"
                )
                return
            }

            val service = GitWorktreeService.getInstance(project)

            WorktreeOperations.createWorktree(
                project = project,
                service = service,
                parentPath = parentPath,
                promptToOpen = true,
                modalityState = ModalityState.nonModal(),
                callbacks = WorktreeOperations.CreateWorktreeCallbacks(
                    onSuccess = { widget.updateCacheAsync() }
                )
            )
        }

        /**
         * Opens the manage worktrees dialog.
         */
        private fun manageWorktrees() {
            val service = GitWorktreeService.getInstance(project)
            val dialog = ManageWorktreesDialog(project, service)
            dialog.show()
        }

        /**
         * Prevents selection of the visual separator entry.
         */
        override fun isSelectable(value: WorktreePopupItem): Boolean {
            return value !is WorktreePopupItem.Separator
        }
    }
}
