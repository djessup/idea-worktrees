package au.id.deejay.ideaworktrees.ui

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
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
    private val createButton = JButton("Create") // TODO: Wire up create button
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
        TODO("Not yet implemented")
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
        val selectedRow = table.selectedRow
        val hasSelection = selectedRow >= 0

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
    fun snapshotWorktrees(): List<WorktreeInfo> = synchronized(worktrees) { worktrees.toList() }

    @TestOnly
    fun currentWorktreeForTest(): WorktreeInfo? = currentWorktree

    private fun openSelectedWorktree() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0 || selectedRow >= worktrees.size) return

        val worktree = worktrees[selectedRow]

        // TODO: Offer to open in the same window, new window, or cancel
        val result = Messages.showYesNoCancelDialog(
            project,
            "Open worktree '${worktree.displayName}' in a new window?",
            "Open Worktree",
            Messages.getQuestionIcon(),

        )

        if (result == Messages.YES) {
            ProjectUtil.openOrImport(worktree.path, project, false)
        }
    }

    private fun deleteSelectedWorktree() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0 || selectedRow >= worktrees.size) return

        val worktree = worktrees[selectedRow]

        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete the worktree at:\n${worktree.path}\n\n" +
                "This will remove the worktree directory and all its contents.\n\n" +
                "<b>Uncommitted changes will be lost!</b> " +
                "This operation cannot be undone.",
            "Confirm Delete Worktree",
            Messages.getWarningIcon()
        )

        if (result != Messages.YES) return

        val force = if (worktree.isLocked) {
            val forceResult = Messages.showYesNoDialog(
                project,
                "The worktree is locked. Force deletion?",
                "Worktree Locked",
                Messages.getWarningIcon()
            )
            forceResult == Messages.YES
        } else {
            false
        }

        val application = ApplicationManager.getApplication()
        val modality = ModalityState.stateForComponent(table)

        service.deleteWorktree(worktree.path, force)
            .whenComplete { result, error ->
                application.invokeLater({
                    if (error != null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to delete worktree: ${error.message ?: "Unknown error"}",
                            "Error Deleting Worktree"
                        )
                        return@invokeLater
                    }

                    if (result == null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to delete worktree: Unknown error",
                            "Error Deleting Worktree"
                        )
                        return@invokeLater
                    }

                    if (result.isSuccess) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Worktree Deleted",
                                result.successMessage() ?: "Deleted worktree successfully",
                                NotificationType.INFORMATION
                            )
                            .notify(project)

                        refreshWorktrees()
                    } else {
                        val errorMsg = result.errorMessage() ?: "Failed to delete worktree"
                        val details = result.errorDetails()
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
                    }
                }, modality)
            }
    }

    /**
     * Table model for displaying worktrees.
     */
    private inner class WorktreeTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("s", "Name", "Branch", "Path", "Commit", "Status")

        override fun getRowCount(): Int = worktrees.size

        override fun getColumnCount(): Int = columnNames.size

        override fun getColumnName(column: Int): String = columnNames[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            if (rowIndex >= worktrees.size) return ""

            val worktree = worktrees[rowIndex]
            return when (columnIndex) {
                0 -> worktree.path == currentWorktree?.path
                0 -> {
                    val prefix = if (worktree.path == currentWorktree?.path) "* " else ""
                    "<b>$prefix${worktree.displayName}</b>"
                }
                1 -> worktree.branch ?: "(detached)"
                2 -> worktree.path.toString()
                3 -> worktree.commit.take(7)
                4 -> buildString {
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
