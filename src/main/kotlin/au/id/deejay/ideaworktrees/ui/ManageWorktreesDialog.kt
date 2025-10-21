package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.utils.WorktreeOperations
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import javax.swing.*
import javax.swing.table.AbstractTableModel
import org.jetbrains.annotations.TestOnly

/**
 * Dialog for managing Git worktrees with a table view.
 */
class ManageWorktreesDialog(
    private val project: Project,
    private val service: GitWorktreeService
) : DialogWrapper(project) {

    private val worktrees = mutableListOf<WorktreeInfo>()
    private var currentWorktree: WorktreeInfo? = null
    private val tableModel = WorktreeTableModel()
    private val table = JBTable(tableModel)

    // Buttons
    private val createButton = JButton("New")
    private val openButton = JButton("Open")
    private val deleteButton = JButton("Delete")
    private val refreshButton = JButton("Refresh")

    init {
        title = "Manage Git Worktrees"
        setOKButtonText("Close")

        // Configure table
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.selectionModel.addListSelectionListener {
            updateButtonStates()
        }

        // Set up button actions
        createButton.addActionListener { createNewWorktree() }
        openButton.addActionListener { openSelectedWorktree() }
        deleteButton.addActionListener { deleteSelectedWorktree() }
        refreshButton.addActionListener { refreshWorktrees() }

        init()

        // Load worktrees after dialog is initialized
        refreshWorktrees()
    }

    private fun createNewWorktree() {
        val modality = ModalityState.stateForComponent(table)

        // Get parent directory from current project
        val parentPath = project.basePath?.let { Paths.get(it).parent }

        WorktreeOperations.createWorktree(
            project = project,
            service = service,
            parentPath = parentPath,
            promptToOpen = true,
            modalityState = modality,
            callbacks = WorktreeOperations.CreateWorktreeCallbacks(
                onSuccess = { refreshWorktrees() }
            )
        )
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        // Table with scroll pane
        val scrollPane = JBScrollPane(table)
        scrollPane.preferredSize = Dimension(700, 300)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Button panel
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.X_AXIS)
        buttonPanel.add(Box.createHorizontalGlue())
        buttonPanel.add(createButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(openButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(deleteButton)
        buttonPanel.add(Box.createHorizontalStrut(5))
        buttonPanel.add(refreshButton)

        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun refreshWorktrees() {
        val application = ApplicationManager.getApplication()
        val modality = ModalityState.stateForComponent(table)

        if (!service.isGitRepository()) {
            application.invokeLater({
                worktrees.clear()
                currentWorktree = null
                tableModel.fireTableDataChanged()
                updateButtonStates()
            }, modality)
            return
        }

        service.listWorktrees()
            .thenCombine(service.getCurrentWorktree()) { loaded, current -> loaded to current }
            .whenComplete { result, error ->
                application.invokeLater({
                    if (error != null || result == null) {
                        worktrees.clear()
                        currentWorktree = null
                        tableModel.fireTableDataChanged()
                        updateButtonStates()
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Failed to load worktrees",
                                error?.message ?: "Unknown error",
                                NotificationType.ERROR
                            )
                            .notify(project)
                    } else {
                        val (loadedWorktrees, current) = result
                        worktrees.clear()
                        worktrees.addAll(loadedWorktrees)
                        currentWorktree = current
                        tableModel.fireTableDataChanged()
                        updateButtonStates()
                    }
                }, modality)
            }
    }

    private fun updateButtonStates() {
        val isGitRepo = service.isGitRepository()
        val selectedRow = table.selectedRow
        val hasSelection = selectedRow >= 0

        createButton.isEnabled = isGitRepo
        openButton.isEnabled = hasSelection

        // Can't delete current worktree or main worktree
        deleteButton.isEnabled = hasSelection && selectedRow < worktrees.size && run {
            val selected = worktrees[selectedRow]
            selected.path != currentWorktree?.path && !selected.isMain
        }
    }

    @TestOnly
    fun refreshForTest() {
        refreshWorktrees()
    }

    @TestOnly
    fun createWorktreeForTest(
        path: Path,
        branch: String,
        allowInitialCommit: Boolean = true
    ): CompletableFuture<WorktreeOperationResult> {
        val modality = ModalityState.stateForComponent(table)
        return service.createWorktree(
            path,
            branch,
            createBranch = true,
            allowCreateInitialCommit = allowInitialCommit
        ).also { future ->
            future.whenComplete { result, error ->
                if (error == null && result is WorktreeOperationResult.Success) {
                    ApplicationManager.getApplication().invokeLater(
                        { refreshWorktrees() },
                        modality
                    )
                }
            }
        }
    }

    @TestOnly
    fun snapshotWorktrees(): List<WorktreeInfo> = synchronized(worktrees) { worktrees.toList() }

    @TestOnly
    fun currentWorktreeForTest(): WorktreeInfo? = currentWorktree

    private fun openSelectedWorktree() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0 || selectedRow >= worktrees.size) return

        val worktree = worktrees[selectedRow]

        WorktreeOperations.openWorktree(
            project = project,
            worktree = worktree,
            confirmBeforeOpen = true
        )
    }

    private fun deleteSelectedWorktree() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0 || selectedRow >= worktrees.size) return

        val worktree = worktrees[selectedRow]
        val modality = ModalityState.stateForComponent(table)

        WorktreeOperations.deleteWorktree(
            project = project,
            service = service,
            worktree = worktree,
            modalityState = modality,
            callbacks = WorktreeOperations.DeleteWorktreeCallbacks(
                onSuccess = { refreshWorktrees() }
            )
        )
    }

    /**
     * Table model for displaying worktrees.
     */
    private inner class WorktreeTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("Current", "Name", "Branch", "Path", "Commit", "Status")

        override fun getRowCount(): Int = worktrees.size

        override fun getColumnCount(): Int = columnNames.size

        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            if (rowIndex >= worktrees.size) return ""

            val worktree = worktrees[rowIndex]
            return when (columnIndex) {
                0 -> if (worktree.path == currentWorktree?.path) "→" else ""
                1-> worktree.displayName
                2 -> worktree.branch ?: "(detached)"
                3 -> worktree.path.toString()
                4 -> worktree.commit.take(7)
                5 -> buildString {
                    if (worktree.isMain) append("MAIN ")
                    if (worktree.isLocked) append("LOCKED ")
                    if (worktree.isPrunable) append("PRUNABLE ")
                }.trim().ifEmpty { "-" }
                else -> ""
            }
        }

        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java
    }
}
