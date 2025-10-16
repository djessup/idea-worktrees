package com.adobe.ideaworktrees.actions

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.services.GitWorktreeService
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JCheckBox
import javax.swing.JPanel

/**
 * Action that merges one worktree into another (or into the current/main worktree).
 */
class MergeWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)
        val worktrees = service.listWorktrees()

        if (worktrees.size < 2) {
            Messages.showInfoMessage(
                project,
                "You need at least two worktrees to perform a merge.",
                "Merge Worktrees"
            )
            return
        }

        val dialog = MergeWorktreesDialog(project, worktrees)
        if (!dialog.showAndGet()) {
            return
        }

        val (source, target, fastForwardOnly) = dialog.getSelection()

        val confirm = Messages.showYesNoDialog(
            project,
            "Merge '${source.displayName}' into '${target.displayName}'?\n\n" +
                "This will run 'git merge${if (fastForwardOnly) " --ff-only" else ""} ${source.displayName}'.",
            "Merge Worktrees",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) {
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = service.mergeWorktree(source, target, fastForwardOnly)
            ApplicationManager.getApplication().invokeLater({
                if (result.isSuccess) {
                    notify(
                        project,
                        "Worktree Merge",
                        result.successMessage() ?: "Merge completed successfully.",
                        NotificationType.INFORMATION
                    )
                } else {
                    val message = result.errorMessage() ?: "Failed to merge worktrees."
                    val details = result.errorDetails()
                    val fullMessage = if (details != null) "$message\n\nDetails: $details" else message
                    Messages.showErrorDialog(project, fullMessage, "Merge Worktrees")
                }
            }, ModalityState.nonModal())
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val service = GitWorktreeService.getInstance(project)
        e.presentation.isEnabledAndVisible = service.isGitRepository() && service.listWorktrees().size >= 2
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun notify(project: com.intellij.openapi.project.Project, title: String, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Git Worktree")
            .createNotification(title, message, type)
            .notify(project)
    }
}

private class MergeWorktreesDialog(
    project: com.intellij.openapi.project.Project,
    private val worktrees: List<WorktreeInfo>
) : DialogWrapper(project) {

    private val sourceModel = DefaultComboBoxModel(worktrees.toTypedArray())
    private val targetModel = DefaultComboBoxModel(worktrees.toTypedArray())
    private val sourceCombo = ComboBox(sourceModel)
    private val targetCombo = ComboBox(targetModel)
    private val fastForwardOnlyCheckbox = JCheckBox("Fast-forward only", true)

    init {
        title = "Merge Worktrees"
        sourceCombo.renderer = WorktreeComboBoxRenderer()
        targetCombo.renderer = WorktreeComboBoxRenderer()
        if (worktrees.size >= 2) {
            sourceCombo.selectedIndex = 0
            targetCombo.selectedIndex = 1
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(5)
        }

        panel.add(JLabel("Merge from:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(sourceCombo, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JLabel("Into:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(targetCombo, gbc)

        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.insets = JBUI.insets(10, 5, 5, 5)
        panel.add(fastForwardOnlyCheckbox, gbc)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val source = sourceCombo.selectedItem as? WorktreeInfo
            ?: return ValidationInfo("Select a source worktree.", sourceCombo)
        val target = targetCombo.selectedItem as? WorktreeInfo
            ?: return ValidationInfo("Select a target worktree.", targetCombo)
        if (source.path == target.path) {
            return ValidationInfo("Source and target must be different.", targetCombo)
        }
        return null
    }

    fun getSelection(): Triple<WorktreeInfo, WorktreeInfo, Boolean> {
        val source = sourceCombo.selectedItem as WorktreeInfo
        val target = targetCombo.selectedItem as WorktreeInfo
        return Triple(source, target, fastForwardOnlyCheckbox.isSelected)
    }
}
