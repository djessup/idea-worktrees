package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.model.WorktreeOperationResult
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.DialogWrapper
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Action to create a new Git worktree.
 */
class CreateWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Show dialog to get branch name and worktree path
        val dialog = CreateWorktreeDialog(project)
        if (!dialog.showAndGet()) {
            return
        }

        val branchName = dialog.getBranchName()
        val worktreePath = Paths.get(dialog.getWorktreePath())

        // Check if path already exists
        if (worktreePath.toFile().exists()) {
            Messages.showErrorDialog(
                project,
                "A directory with this name already exists: $worktreePath",
                "Directory Exists"
            )
            return
        }

        val service = GitWorktreeService.getInstance(project)

        fun publishResult(result: WorktreeOperationResult) {
            ApplicationManager.getApplication().invokeLater({
                when (result) {
                    is WorktreeOperationResult.Success -> {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Worktree Created",
                                result.message,
                                NotificationType.INFORMATION
                            )
                            .notify(project)

                        val openWorktree = Messages.showYesNoDialog(
                            project,
                            "Would you like to open the new worktree in a new window?",
                            "Open Worktree",
                            Messages.getQuestionIcon()
                        )

                        if (openWorktree == Messages.YES) {
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
                            ApplicationManager.getApplication().executeOnPooledThread {
                                val retryResult = service.createWorktree(
                                    worktreePath,
                                    branchName,
                                    createBranch = true,
                                    allowCreateInitialCommit = true
                                )
                                publishResult(retryResult)
                            }
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
                        val fullMessage = if (details != null) {
                            "$errorMsg\n\nDetails: $details"
                        } else {
                            errorMsg
                        }

                        Messages.showErrorDialog(
                            project,
                            fullMessage,
                            "Error Creating Worktree"
                        )
                    }
                }
            }, ModalityState.nonModal())
        }

        // Execute Git command on background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.createWorktree(worktreePath, branchName, createBranch = true)
            publishResult(result)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val service = GitWorktreeService.getInstance(project)
        e.presentation.isEnabledAndVisible = service.isGitRepository()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}

/**
 * Dialog for creating a new worktree with branch name and path selection.
 */
private class CreateWorktreeDialog(private val project: com.intellij.openapi.project.Project) : DialogWrapper(project) {
    private val branchNameField = JTextField(20)
    private val pathField = TextFieldWithBrowseButton()

    init {
        title = "Create New Worktree"

        // Set up path browser
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Worktree Location"
        descriptor.description = "Choose the parent directory for the new worktree"
        @Suppress("DEPRECATION")
        pathField.addBrowseFolderListener(
            "Select Worktree Location",
            "Choose the parent directory for the new worktree",
            project,
            descriptor
        )

        // Set default path to parent of current project
        val projectPath = project.basePath?.let { File(it) }
        val defaultParent = projectPath?.parentFile
        if (defaultParent != null) {
            pathField.text = defaultParent.absolutePath
        }

        // Update path when branch name changes
        branchNameField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateDefaultPath()
        })

        init()
    }

    private fun updateDefaultPath() {
        val branchName = branchNameField.text.trim()
        if (branchName.isNotEmpty()) {
            val projectPath = project.basePath?.let { File(it) }
            val projectName = projectPath?.name ?: "project"
            val defaultParent = projectPath?.parentFile
            if (defaultParent != null) {
                val suggestedName = "$projectName-$branchName"
                pathField.text = File(defaultParent, suggestedName).absolutePath
            }
        }
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(5, 5, 5, 5)
        panel.add(JLabel("Branch name:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(branchNameField, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JLabel("Worktree path:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(pathField, gbc)

        return panel
    }

    override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
        val branchName = branchNameField.text.trim()
        if (branchName.isEmpty()) {
            return com.intellij.openapi.ui.ValidationInfo("Branch name cannot be empty", branchNameField)
        }

        val path = pathField.text.trim()
        if (path.isEmpty()) {
            return com.intellij.openapi.ui.ValidationInfo("Worktree path cannot be empty", pathField)
        }

        return null
    }

    fun getBranchName(): String = branchNameField.text.trim()
    fun getWorktreePath(): String = pathField.text.trim()
}
