package au.id.deejay.ideaworktrees.actions

import au.id.deejay.ideaworktrees.services.GitWorktreeService
import au.id.deejay.ideaworktrees.utils.WorktreeOperations
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.DialogWrapper
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.io.File
import java.nio.file.InvalidPathException
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
        val targetPathText = dialog.getWorktreePath()
        val worktreePath = try {
            Paths.get(targetPathText)
        } catch (e: InvalidPathException) {
            Messages.showErrorDialog(
                project,
                "Invalid worktree path: ${e.message}",
                "Invalid Path"
            )
            return
        }

        val service = GitWorktreeService.getInstance(project)

        WorktreeOperations.createWorktree(
            project = project,
            service = service,
            worktreePath = worktreePath,
            branchName = branchName,
            promptToOpen = true,
            modalityState = ModalityState.nonModal()
        )
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
internal class CreateWorktreeDialog(
    private val project: com.intellij.openapi.project.Project
) : DialogWrapper(project) {

    private val branchNameField = JTextField(20)
    private val pathField = TextFieldWithBrowseButton()

    init {
        title = "Create New Worktree"

        // Set up path browser
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Select Worktree Location"
            description = "Choose the parent directory for the new worktree"
        }
        @Suppress("DEPRECATION")
        pathField.addBrowseFolderListener(
            descriptor.title,
            descriptor.description,
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
        if (branchName.isEmpty()) {
            return
        }

        val projectPath = project.basePath?.let { File(it) }
        val defaultParent = projectPath?.parentFile ?: return
        val projectPathAsPath = project.basePath?.let { java.nio.file.Paths.get(it) }
        val suggestedName = au.id.deejay.ideaworktrees.ui.WorktreeStatusBarWidget
            .suggestDirectoryName(projectPathAsPath, branchName)

        pathField.text = File(defaultParent, suggestedName).absolutePath
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
        }

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
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

        // Validate path length (Windows has a 260 character limit for paths)
        if (path.length > MAX_PATH_LENGTH) {
            return com.intellij.openapi.ui.ValidationInfo(
                "Path is too long (${path.length} characters). Maximum is $MAX_PATH_LENGTH characters for Windows compatibility.",
                pathField
            )
        }

        // Validate against Windows reserved filenames
        val pathFile = File(path)
        val directoryName = pathFile.name
        if (isWindowsReservedName(directoryName)) {
            return com.intellij.openapi.ui.ValidationInfo(
                "Directory name '$directoryName' is a reserved Windows filename and cannot be used.",
                pathField
            )
        }

        return null
    }

    fun getBranchName(): String = branchNameField.text.trim()

    fun getWorktreePath(): String = pathField.text.trim()

    companion object {
        // Windows MAX_PATH constant
        private const val MAX_PATH_LENGTH = 260

        // Windows reserved filenames (case-insensitive)
        private val WINDOWS_RESERVED_NAMES = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )

        /**
         * Checks if a filename is a Windows reserved name.
         * Reserved names are case-insensitive and can optionally have an extension.
         */
        private fun isWindowsReservedName(name: String): Boolean {
            // Check the name without extension
            val nameWithoutExtension = name.substringBeforeLast('.', name).uppercase()
            return nameWithoutExtension in WINDOWS_RESERVED_NAMES
        }
    }
}
