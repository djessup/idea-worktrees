package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.ui.WorktreeComboBoxRenderer
import au.id.deejay.ideaworktrees.utils.WorktreeAsyncOperations
import au.id.deejay.ideaworktrees.utils.WorktreeNotifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * Action to compare two Git worktrees and display the diff summary/details.
 */
class CompareWorktreeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = GitWorktreeService.getInstance(project)

        WorktreeAsyncOperations.loadWorktrees(
            project = project,
            service = service,
            onSuccess = { worktrees ->
                if (worktrees.size < 2) {
                    Messages.showInfoMessage(
                        project,
                        "You need at least two worktrees to run a comparison.",
                        "Compare Worktrees"
                    )
                    return@loadWorktrees
                }

                val dialog = CompareWorktreesDialog(project, worktrees)
                if (!dialog.showAndGet()) {
                    return@loadWorktrees
                }

                val (source, target) = dialog.getSelection()

                service.compareWorktrees(source, target).whenComplete { result, error ->
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (error != null) {
                            WorktreeNotifications.showError(
                                project = project,
                                title = "Compare Worktrees",
                                message = error.message ?: "Unknown error occurred while comparing worktrees"
                            )
                        } else if (result != null) {
                            if (result.isSuccess) {
                                val message = result.successMessage() ?: "Comparison completed."
                                val details = result.successDetails()
                                if (!details.isNullOrBlank()) {
                                    DiffResultDialog(project, message, details).show()
                                } else {
                                    Messages.showInfoMessage(project, message, "Compare Worktrees")
                                }
                            } else {
                                val message = result.errorMessage() ?: "Failed to compare worktrees."
                                val details = result.errorDetails()
                                val fullMessage = if (details != null) "$message\n\nDetails: $details" else message
                                Messages.showErrorDialog(project, fullMessage, "Compare Worktrees")
                            }
                        } else {
                            WorktreeNotifications.showError(
                                project = project,
                                title = "Compare Worktrees",
                                message = "Worktree comparison failed with an unknown error"
                            )
                        }
                    }
                }
            },
            onError = { error ->
                Messages.showErrorDialog(
                    project,
                    "Failed to list worktrees: ${error.message ?: "Unknown error"}",
                    "Compare Worktrees"
                )
            }
        )
    }

private class DiffResultDialog(
    project: com.intellij.openapi.project.Project,
    private val summary: String,
    private val details: String
) : DialogWrapper(project) {

    private val textArea = JTextArea(details).apply {
        isEditable = false
        lineWrap = false
        wrapStyleWord = false
        font = Font(Font.MONOSPACED, Font.PLAIN, JBUI.scale(12))
        rows = 25
        columns = 100
    }

    init {
        title = "Worktree Comparison"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(8)

        val summaryLabel = JLabel(summary)
        summaryLabel.border = JBUI.Borders.emptyBottom(8)

        panel.add(summaryLabel, BorderLayout.NORTH)
        panel.add(JScrollPane(textArea), BorderLayout.CENTER)

        return panel
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

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private class CompareWorktreesDialog(
    project: com.intellij.openapi.project.Project,
    private val worktrees: List<WorktreeInfo>
) : DialogWrapper(project) {

    private val sourceModel = DefaultComboBoxModel(worktrees.toTypedArray())
    private val targetModel = DefaultComboBoxModel(worktrees.toTypedArray())
    private val sourceCombo = ComboBox(sourceModel)
    private val targetCombo = ComboBox(targetModel)

    init {
        title = "Compare Worktrees"
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

        panel.add(JLabel("Compare:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(sourceCombo, gbc)

        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JLabel("Against:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        panel.add(targetCombo, gbc)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        val source = sourceCombo.selectedItem as? WorktreeInfo
            ?: return ValidationInfo("Select a source worktree.", sourceCombo)
        val target = targetCombo.selectedItem as? WorktreeInfo
            ?: return ValidationInfo("Select a target worktree.", targetCombo)
        if (source.path == target.path) {
            return ValidationInfo("Select two different worktrees.", targetCombo)
        }
        return null
    }

    fun getSelection(): Pair<WorktreeInfo, WorktreeInfo> {
        val source = sourceCombo.selectedItem as WorktreeInfo
        val target = targetCombo.selectedItem as WorktreeInfo
        return source to target
    }
}
