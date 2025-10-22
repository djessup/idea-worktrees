package au.id.deejay.ideaworktrees.utils

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Utility class providing reusable worktree operations with standardized UX.
 *
 * This class centralizes common worktree workflows (create, open, delete) to ensure
 * consistent behavior across different UI contexts (dialogs, actions, widgets).
 *
 * All operations handle threading correctly:
 * - Git operations run on background threads via GitWorktreeService
 * - UI updates are marshaled back to EDT via invokeLater
 * - Callbacks are invoked on EDT after operations complete
 */
object WorktreeOperations {

    /**
     * Callbacks for create worktree operation.
     */
    data class CreateWorktreeCallbacks(
        /**
         * Called on EDT after successful worktree creation.
         * Use this to refresh UI state (tables, caches, etc.).
         */
        val onSuccess: (() -> Unit)? = null,

        /**
         * Called on EDT after worktree creation fails.
         * Use this for additional error handling beyond the default error dialog.
         */
        val onFailure: ((WorktreeOperationResult.Failure) -> Unit)? = null,

        /**
         * Called on EDT when user cancels the operation.
         */
        val onCancel: (() -> Unit)? = null
    )

    /**
     * Callbacks for delete worktree operation.
     */
    data class DeleteWorktreeCallbacks(
        /**
         * Called on EDT after successful worktree deletion.
         * Use this to refresh UI state (tables, caches, etc.).
         */
        val onSuccess: (() -> Unit)? = null,

        /**
         * Called on EDT after worktree deletion fails.
         */
        val onFailure: ((WorktreeOperationResult.Failure) -> Unit)? = null,

        /**
         * Called on EDT when user cancels the operation.
         */
        val onCancel: (() -> Unit)? = null
    )

    /**
     * Creates a new worktree with standardized UX flow including user input dialog.
     *
     * This operation:
     * 1. Shows a dialog to collect branch name and worktree path from the user
     * 2. Validates the target path doesn't already exist
     * 3. Calls GitWorktreeService.createWorktree on a background thread
     * 4. Handles the RequiresInitialCommit flow with user confirmation
     * 5. Shows success/error notifications
     * 6. Optionally prompts to open the new worktree
     * 7. Invokes callbacks on EDT for UI updates
     *
     * @param project The current project
     * @param service The GitWorktreeService instance
     * @param parentPath Optional parent directory path for the new worktree (defaults to project parent)
     * @param defaultBranchName Optional default branch name to pre-fill in the dialog
     * @param defaultWorktreeName Optional default worktree directory name to pre-fill in the dialog
     * @param promptToOpen If true, prompts user to open the new worktree after creation
     * @param modalityState The modality state for EDT callbacks (use ModalityState.stateForComponent for dialogs)
     * @param callbacks Optional callbacks for success/failure/cancel
     */
    fun createWorktree(
        project: Project,
        service: GitWorktreeService,
        parentPath: Path? = null,
        defaultBranchName: String = "",
        defaultWorktreeName: String = "",
        promptToOpen: Boolean = true,
        modalityState: ModalityState = ModalityState.nonModal(),
        callbacks: CreateWorktreeCallbacks = CreateWorktreeCallbacks()
    ) {
        // Show dialog to get user input (on EDT)
        val dialog = CreateWorktreeDialog(
            project = project,
            defaultParentPath = parentPath,
            defaultBranchName = defaultBranchName,
            defaultWorktreeName = defaultWorktreeName
        )

        if (!dialog.showAndGet()) {
            callbacks.onCancel?.invoke()
            return
        }

        val branchName = dialog.getBranchName()
        val targetPathText = dialog.getWorktreePath()
        val worktreePath = try {
            Paths.get(targetPathText)
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Invalid worktree path: ${e.message}",
                "Invalid Path"
            )
            callbacks.onCancel?.invoke()
            return
        }

        // Delegate to the lower-level function
        createWorktreeWithoutDialog(
            project = project,
            service = service,
            worktreePath = worktreePath,
            branchName = branchName,
            promptToOpen = promptToOpen,
            modalityState = modalityState,
            callbacks = callbacks
        )
    }

    /**
     * Creates a new worktree without showing the input dialog (for programmatic use and testing).
     *
     * This is a lower-level function that bypasses the user input dialog and directly creates
     * a worktree at the specified path. Use this when you already have the path and branch name,
     * such as in tests or when called from other programmatic contexts.
     *
     * @param project The current project
     * @param service The GitWorktreeService instance
     * @param worktreePath The full path where the worktree should be created
     * @param branchName The branch name for the new worktree
     * @param promptToOpen If true, prompts user to open the new worktree after creation
     * @param modalityState The modality state for EDT callbacks
     * @param callbacks Optional callbacks for success/failure/cancel
     */
    fun createWorktreeWithoutDialog(
        project: Project,
        service: GitWorktreeService,
        worktreePath: Path,
        branchName: String,
        promptToOpen: Boolean = true,
        modalityState: ModalityState = ModalityState.nonModal(),
        callbacks: CreateWorktreeCallbacks = CreateWorktreeCallbacks()
    ) {
        // Validate path doesn't exist (on EDT)
        val worktreeFile = worktreePath.toFile()
        if (worktreeFile.exists()) {
            Messages.showErrorDialog(
                project,
                "A directory with this name already exists: $worktreePath",
                "Directory Exists"
            )
            callbacks.onCancel?.invoke()
            return
        }

        val application = ApplicationManager.getApplication()

        // Recursive function to handle initial commit flow
        lateinit var submitCreateRequest: (Boolean) -> Unit

        fun handleResult(result: WorktreeOperationResult) {
            when (result) {
                is WorktreeOperationResult.Success -> {
                    NotificationGroupManager.getInstance()
                        .getNotificationGroup("Git Worktree")
                        .createNotification(
                            "Worktree created",
                            result.message,
                            NotificationType.INFORMATION
                        )
                        .notify(project)

                    callbacks.onSuccess?.invoke()

                    // Optionally prompt to open the new worktree
                    if (promptToOpen) {
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
                        callbacks.onCancel?.invoke()
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
                    callbacks.onFailure?.invoke(result)
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
                    when {
                        error != null -> {
                            Messages.showErrorDialog(
                                project,
                                "Failed to create worktree: ${error.message ?: "Unknown error"}",
                                "Error Creating Worktree"
                            )
                            callbacks.onFailure?.invoke(
                                WorktreeOperationResult.Failure(
                                    error = error.message ?: "Unknown error"
                                )
                            )
                        }
                        result != null -> handleResult(result)
                        else -> {
                            Messages.showErrorDialog(
                                project,
                                "Failed to create worktree: Unknown error",
                                "Error Creating Worktree"
                            )
                            callbacks.onFailure?.invoke(
                                WorktreeOperationResult.Failure(error = "Unknown error")
                            )
                        }
                    }
                }, modalityState)
            }
        }

        // Start the create request
        submitCreateRequest(false)
    }

    /**
     * Opens a worktree in a new window with optional confirmation.
     *
     * @param project The current project
     * @param worktree The worktree to open
     * @param confirmBeforeOpen If true, shows a confirmation dialog before opening
     */
    fun openWorktree(
        project: Project,
        worktree: WorktreeInfo,
        confirmBeforeOpen: Boolean = true
    ) {
        if (confirmBeforeOpen) {
            val result = Messages.showYesNoCancelDialog(
                project,
                "Open worktree '${worktree.displayName}' in a new window?",
                "Open Worktree",
                Messages.getQuestionIcon()
            )

            if (result != Messages.YES) {
                return
            }
        }

        try {
            ProjectUtil.openOrImport(worktree.path, project, false)
        } catch (e: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Git Worktree")
                .createNotification(
                    "Failed to open worktree: ${e.message}",
                    NotificationType.ERROR
                )
                .notify(project)
        }
    }

    /**
     * Deletes a worktree with confirmation and standardized UX flow.
     *
     * This operation:
     * 1. Shows a confirmation dialog with strong warning
     * 2. Handles locked worktrees (prompts for force deletion)
     * 3. Calls GitWorktreeService.deleteWorktree on a background thread
     * 4. Shows success/error notifications
     * 5. Invokes callbacks on EDT for UI updates
     *
     * @param project The current project
     * @param service The GitWorktreeService instance
     * @param worktree The worktree to delete
     * @param modalityState The modality state for EDT callbacks
     * @param callbacks Optional callbacks for success/failure/cancel
     */
    fun deleteWorktree(
        project: Project,
        service: GitWorktreeService,
        worktree: WorktreeInfo,
        modalityState: ModalityState = ModalityState.nonModal(),
        callbacks: DeleteWorktreeCallbacks = DeleteWorktreeCallbacks()
    ) {
        // Confirm deletion (on EDT)
        val result = Messages.showYesNoDialog(
            project,
            "Are you sure you want to delete the worktree at:\n${worktree.path}\n\n" +
                "This will remove the worktree directory and all its contents.\n\n" +
                "<b>Uncommitted changes will be lost!</b> " +
                "This operation cannot be undone.",
            "Confirm Delete Worktree",
            Messages.getWarningIcon()
        )

        if (result != Messages.YES) {
            callbacks.onCancel?.invoke()
            return
        }

        // Check if worktree is locked and prompt for force deletion
        val force = if (worktree.isLocked) {
            val forceResult = Messages.showYesNoDialog(
                project,
                "The worktree is locked. Force deletion?",
                "Worktree Locked",
                Messages.getWarningIcon()
            )
            if (forceResult != Messages.YES) {
                callbacks.onCancel?.invoke()
                return
            }
            true
        } else {
            false
        }

        val application = ApplicationManager.getApplication()

        // Execute deletion on background thread
        service.deleteWorktree(worktree.path, force)
            .whenComplete { deleteResult, error ->
                application.invokeLater({
                    if (error != null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to delete worktree: ${error.message ?: "Unknown error"}",
                            "Error Deleting Worktree"
                        )
                        callbacks.onFailure?.invoke(
                            WorktreeOperationResult.Failure(error = error.message ?: "Unknown error")
                        )
                        return@invokeLater
                    }

                    if (deleteResult == null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to delete worktree: Unknown error",
                            "Error Deleting Worktree"
                        )
                        callbacks.onFailure?.invoke(
                            WorktreeOperationResult.Failure(error = "Unknown error")
                        )
                        return@invokeLater
                    }

                    if (deleteResult.isSuccess) {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Git Worktree")
                            .createNotification(
                                "Worktree deleted",
                                deleteResult.successMessage() ?: "Deleted worktree successfully",
                                NotificationType.INFORMATION
                            )
                            .notify(project)

                        callbacks.onSuccess?.invoke()
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
                        callbacks.onFailure?.invoke(
                            WorktreeOperationResult.Failure(error = errorMsg, details = details)
                        )
                    }
                }, modalityState)
            }
    }

    /**
     * Suggests a directory name for a new worktree based on the project name and branch name.
     *
     * @param projectPath The path to the current project
     * @param branchName The branch name for the new worktree
     * @return A sanitized directory name in the format "projectName-branchName"
     */
    @JvmStatic
    fun suggestDirectoryName(projectPath: Path?, branchName: String): String {
        val projectFolderName = projectPath?.fileName?.toString() ?: "project"
        // Replace any characters that are unsafe in file names (including slashes) with hyphens
        // Preserve underscores, dots, and hyphens as they are valid in directory names
        val sanitizedBranch = branchName.replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim { it == '-' || it == '.' }
        return "$projectFolderName-$sanitizedBranch"
    }
}

/**
 * Dialog for creating a new worktree with branch name and path selection.
 *
 * This dialog provides:
 * - Branch name input field
 * - Worktree path selection with file browser
 * - Automatic path suggestion based on branch name
 * - Validation for empty fields, path length, and Windows reserved names
 */
internal class CreateWorktreeDialog(
    private val project: Project,
    private val defaultParentPath: Path? = null,
    private val defaultBranchName: String = "",
    private val defaultWorktreeName: String = ""
) : DialogWrapper(project) {

    private val branchNameField = JTextField(20)
    private val pathField = TextFieldWithBrowseButton()

    init {
        title = "Create New Worktree"

        // Set up path browser
        pathField.addBrowseFolderListener(project, FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Select Worktree Location"
            description = "Choose the parent directory for the new worktree"
        })

        // Set default values
        branchNameField.text = defaultBranchName

        // Set default path to parent of current project or provided default
        val parentPath = defaultParentPath?.toFile()
            ?: project.basePath?.let { File(it).parentFile }

        if (parentPath != null) {
            val suggestedName = if (defaultWorktreeName.isNotEmpty()) {
                defaultWorktreeName
            } else if (defaultBranchName.isNotEmpty()) {
                val projectPath = project.basePath?.let { Paths.get(it) }
                WorktreeOperations.suggestDirectoryName(projectPath, defaultBranchName)
            } else {
                ""
            }

            pathField.text = if (suggestedName.isNotEmpty()) {
                File(parentPath, suggestedName).absolutePath
            } else {
                parentPath.absolutePath
            }
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

        val projectPath = project.basePath?.let { Paths.get(it) }
        val defaultParent = defaultParentPath?.toFile()
            ?: project.basePath?.let { File(it).parentFile }
            ?: return

        val suggestedName = WorktreeOperations.suggestDirectoryName(projectPath, branchName)
        pathField.text = File(defaultParent, suggestedName).absolutePath
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(5)
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

    override fun doValidate(): ValidationInfo? {
        val branchName = branchNameField.text.trim()
        if (branchName.isEmpty()) {
            return ValidationInfo("Branch name cannot be empty", branchNameField)
        }

        val path = pathField.text.trim()
        if (path.isEmpty()) {
            return ValidationInfo("Worktree path cannot be empty", pathField)
        }

        // Validate path length (Windows has a 260 character limit for paths)
        if (path.length > MAX_PATH_LENGTH) {
            return ValidationInfo(
                "Path is too long (${path.length} characters). Maximum is $MAX_PATH_LENGTH characters for Windows compatibility.",
                pathField
            )
        }

        // Validate against Windows reserved filenames
        val pathFile = File(path)
        val directoryName = pathFile.name
        if (isWindowsReservedName(directoryName)) {
            return ValidationInfo(
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

