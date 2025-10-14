package com.adobe.ideaworktrees.ui

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
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

/**
 * Dialog for managing Git worktrees with a table view.
 */
class ManageWorktreesDialog(
    private val project: Project,
    private val service: GitWorktreeService
) : DialogWrapper(project) {
    
    private val worktrees = mutableListOf<WorktreeInfo>()
    private val tableModel = WorktreeTableModel()
    private val table = JBTable(tableModel)
    
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
        openButton.addActionListener { openSelectedWorktree() }
        deleteButton.addActionListener { deleteSelectedWorktree() }
        refreshButton.addActionListener { refreshWorktrees() }
        
        // Load worktrees
        refreshWorktrees()
        
        init()
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
        // Execute on background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val loadedWorktrees = service.listWorktrees()
            
            // Update UI on EDT
            ApplicationManager.getApplication().invokeLater({
                worktrees.clear()
                worktrees.addAll(loadedWorktrees)
                tableModel.fireTableDataChanged()
                updateButtonStates()
            }, ModalityState.stateForComponent(table))
        }
    }
    
    private fun updateButtonStates() {
        val selectedRow = table.selectedRow
        val hasSelection = selectedRow >= 0
        val currentWorktree = service.getCurrentWorktree()
        
        openButton.isEnabled = hasSelection
        
        // Can't delete current worktree or main worktree
        deleteButton.isEnabled = hasSelection && selectedRow < worktrees.size && run {
            val selected = worktrees[selectedRow]
            selected.path != currentWorktree?.path && !selected.isMain
        }
    }
    
    private fun openSelectedWorktree() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0 || selectedRow >= worktrees.size) return
        
        val worktree = worktrees[selectedRow]
        
        val result = Messages.showYesNoDialog(
            project,
            "Open worktree '${worktree.displayName}' in a new window?",
            "Open Worktree",
            Messages.getQuestionIcon()
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
                    "This will remove the worktree directory and all its contents.",
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
        
        // Delete on background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val deleteResult = service.deleteWorktree(worktree.path, force)
            
            // Show result on EDT
            ApplicationManager.getApplication().invokeLater({
                if (deleteResult.isSuccess) {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Git Worktree")
                        .createNotification(
                            "Worktree Deleted",
                            deleteResult.successMessage() ?: "Deleted worktree successfully",
                            NotificationType.INFORMATION
                        )
                        .notify(project)
                    
                    // Refresh the list
                    refreshWorktrees()
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
                }
            }, ModalityState.stateForComponent(table))
        }
    }
    
    /**
     * Table model for displaying worktrees.
     */
    private inner class WorktreeTableModel : AbstractTableModel() {
        private val columnNames = arrayOf("Name", "Branch", "Path", "Commit", "Status")
        
        override fun getRowCount(): Int = worktrees.size
        
        override fun getColumnCount(): Int = columnNames.size
        
        override fun getColumnName(column: Int): String = columnNames[column]
        
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            if (rowIndex >= worktrees.size) return ""
            
            val worktree = worktrees[rowIndex]
            val currentWorktree = service.getCurrentWorktree()
            
            return when (columnIndex) {
                0 -> {
                    val prefix = if (worktree.path == currentWorktree?.path) "* " else ""
                    "$prefix${worktree.displayName}"
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

