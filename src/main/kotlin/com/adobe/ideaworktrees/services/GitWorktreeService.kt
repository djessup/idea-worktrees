package com.adobe.ideaworktrees.services

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.adobe.ideaworktrees.model.WorktreeOperationResult
import com.adobe.ideaworktrees.model.WorktreeOperationResult.Failure
import com.adobe.ideaworktrees.model.WorktreeOperationResult.RequiresInitialCommit
import com.adobe.ideaworktrees.model.WorktreeOperationResult.Success
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import git4idea.repo.GitRepository
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

/**
 * Service for managing Git worktrees.
 * This is a project-level service that provides operations for working with Git worktrees.
 */
@Service(Service.Level.PROJECT)
class GitWorktreeService(private val project: Project) {
    
    companion object {
        private val LOG = Logger.getInstance(GitWorktreeService::class.java)
        private const val DEFAULT_INITIAL_COMMIT_MESSAGE = "Initial commit created by Git Worktree Manager"

        val WORKTREE_TOPIC: Topic<WorktreeChangeListener> =
            Topic.create("GitWorktreeChanges", WorktreeChangeListener::class.java)
        
        fun getInstance(project: Project): GitWorktreeService = project.service()
    }

    /**
     * Lists all worktrees for the current project's Git repository.
     * Returns an empty list if the project is not a Git repository or if Git is not available.
     */
    fun listWorktrees(): List<WorktreeInfo> {
        val projectPath = getProjectPath() ?: return emptyList()
        
        return try {
            val output = executeGitCommand(projectPath, "worktree", "list", "--porcelain")
            if (output.exitCode != 0) {
                LOG.warn("Failed to list worktrees: ${output.stderr}")
                return emptyList()
            }
            
            parseWorktreeList(output.stdout)
        } catch (e: Exception) {
            LOG.error("Error listing worktrees", e)
            emptyList()
        }
    }

    /**
     * Gets the current worktree for the project.
     * Returns null if the project is not in a Git worktree.
     */
    fun getCurrentWorktree(): WorktreeInfo? {
        val projectPath = getProjectPath() ?: return null
        val worktrees = listWorktrees()
        
        return worktrees.find { worktree ->
            projectPath.startsWith(worktree.path)
        }
    }

    /**
     * Creates a new worktree at the specified path with the given branch.
     *
     * @param path The path where the worktree should be created
     * @param branch The branch to checkout in the new worktree
     * @param createBranch Whether to create a new branch (true) or use an existing one (false)
     * @return WorktreeOperationResult indicating success or failure with details
     */
    fun createWorktree(
        path: Path,
        branch: String,
        createBranch: Boolean = true,
        allowCreateInitialCommit: Boolean = false,
        initialCommitMessage: String = DEFAULT_INITIAL_COMMIT_MESSAGE
    ): WorktreeOperationResult {
        val projectPath = getProjectPath()
            ?: return WorktreeOperationResult.Failure("Project path not found")

        return try {
            // Check if repository has commits
            var hasCommits = hasCommits(projectPath)
            var initialCommitCreated = false

            if (!hasCommits) {
                if (!allowCreateInitialCommit) {
                    return RequiresInitialCommit("Repository has no commits")
                }

                val commitResult = createInitialCommit(projectPath, initialCommitMessage)
                if (commitResult is Failure) {
                    return commitResult
                }

                initialCommitCreated = commitResult is Success
                hasCommits = hasCommits(projectPath)
            }

            val args = mutableListOf("worktree", "add")

            if (createBranch) {
                args.add("-b")
                args.add(branch)
                args.add(path.toString())
                if (hasCommits) {
                    args.add("HEAD")
                }
            } else {
                // Checkout existing branch
                args.add(path.toString())
                args.add(branch)
            }

            val output = executeGitCommand(projectPath, *args.toTypedArray())
            if (output.exitCode != 0) {
                val errorMsg = output.stderr.trim().ifEmpty { "Unknown error" }
                LOG.warn("Failed to create worktree: $errorMsg")
                return WorktreeOperationResult.Failure(
                    "Failed to create worktree",
                    errorMsg
                )
            }

            LOG.info("Created worktree at $path for branch $branch")
            notifyWorktreesChanged()

            val successMessage = if (initialCommitCreated) {
                "Created initial commit and worktree '$branch' at $path"
            } else {
                "Created worktree '$branch' at $path"
            }

            WorktreeOperationResult.Success(successMessage)
        } catch (e: Exception) {
            LOG.error("Error creating worktree", e)
            WorktreeOperationResult.Failure(
                "Error creating worktree",
                e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Checks if the repository has any commits.
     */
    private fun hasCommits(projectPath: Path): Boolean {
        return try {
            val output = executeGitCommand(projectPath, "rev-parse", "HEAD")
            output.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes the worktree at the specified path.
     *
     * @param path The path of the worktree to delete
     * @param force Whether to force deletion even if the worktree is dirty
     * @return WorktreeOperationResult indicating success or failure with details
     */
    fun deleteWorktree(path: Path, force: Boolean = false): WorktreeOperationResult {
        val projectPath = getProjectPath()
            ?: return WorktreeOperationResult.Failure("Project path not found")

        return try {
            val args = mutableListOf("worktree", "remove")
            if (force) {
                args.add("--force")
            }
            args.add(path.toString())

            val output = executeGitCommand(projectPath, *args.toTypedArray())
            if (output.exitCode != 0) {
                val errorMsg = output.stderr.trim().ifEmpty { "Unknown error" }
                LOG.warn("Failed to delete worktree: $errorMsg")
                return WorktreeOperationResult.Failure(
                    "Failed to delete worktree",
                    errorMsg
                )
            }

            LOG.info("Deleted worktree at $path")
            notifyWorktreesChanged()
            WorktreeOperationResult.Success("Deleted worktree at $path")
        } catch (e: Exception) {
            LOG.error("Error deleting worktree", e)
            WorktreeOperationResult.Failure(
                "Error deleting worktree",
                e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Moves/renames a worktree from one path to another.
     *
     * @param oldPath The current path of the worktree
     * @param newPath The new path for the worktree
     * @return WorktreeOperationResult indicating success or failure with details
     */
    fun moveWorktree(oldPath: Path, newPath: Path): WorktreeOperationResult {
        val projectPath = getProjectPath()
            ?: return WorktreeOperationResult.Failure("Project path not found")

        return try {
            val output = executeGitCommand(
                projectPath,
                "worktree", "move",
                oldPath.toString(),
                newPath.toString()
            )

            if (output.exitCode != 0) {
                val errorMsg = output.stderr.trim().ifEmpty { "Unknown error" }
                LOG.warn("Failed to move worktree: $errorMsg")
                return WorktreeOperationResult.Failure(
                    "Failed to move worktree",
                    errorMsg
                )
            }

            LOG.info("Moved worktree from $oldPath to $newPath")
            notifyWorktreesChanged()
            WorktreeOperationResult.Success("Moved worktree from $oldPath to $newPath")
        } catch (e: Exception) {
            LOG.error("Error moving worktree", e)
            WorktreeOperationResult.Failure(
                "Error moving worktree",
                e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Generates a diff between two worktrees.
     *
     * @param source The worktree providing the changes.
     * @param target The worktree to compare against.
     * @return A [WorktreeOperationResult] containing diff summary and details.
     */
    fun compareWorktrees(source: WorktreeInfo, target: WorktreeInfo): WorktreeOperationResult {
        val projectPath = getProjectPath()
            ?: return WorktreeOperationResult.Failure("Project path not found")

        return try {
            val range = "${worktreeRef(source)}..${worktreeRef(target)}"

            val statOutput = executeGitCommand(
                projectPath,
                "diff", "--stat", "--no-color", range
            )
            if (statOutput.exitCode != 0) {
                val errorMsg = statOutput.stderr.trim().ifEmpty { "Unknown error" }
                return WorktreeOperationResult.Failure(
                    "Failed to compare worktrees",
                    errorMsg
                )
            }

            val diffOutput = executeGitCommand(
                projectPath,
                "diff", "--no-color", range
            )
            if (diffOutput.exitCode != 0) {
                val errorMsg = diffOutput.stderr.trim().ifEmpty { "Unknown error" }
                return WorktreeOperationResult.Failure(
                    "Failed to compare worktrees",
                    errorMsg
                )
            }

            val statText = statOutput.stdout.trim()
            val diffText = diffOutput.stdout.trim()

            val summary = if (statText.isEmpty()) {
                "No differences between ${source.displayName} and ${target.displayName}"
            } else {
                statText
            }

            WorktreeOperationResult.Success(summary, diffText.ifBlank { null })
        } catch (e: Exception) {
            WorktreeOperationResult.Failure(
                "Error comparing worktrees",
                e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Merges the source worktree into the target worktree.
     *
     * @param source The worktree to merge from.
     * @param target The worktree that will receive the changes.
     * @param fastForwardOnly Whether to allow only fast-forward merges.
     */
    fun mergeWorktree(
        source: WorktreeInfo,
        target: WorktreeInfo,
        fastForwardOnly: Boolean = false
    ): WorktreeOperationResult {
        val targetPath = target.path
        if (!targetPath.exists()) {
            return WorktreeOperationResult.Failure("Target worktree path does not exist: $targetPath")
        }

        return try {
            val args = mutableListOf("merge")
            if (fastForwardOnly) {
                args.add("--ff-only")
            }
            args.add(worktreeRef(source))

            val output = executeGitCommand(targetPath, *args.toTypedArray())
            if (output.exitCode != 0) {
                val errorMsg = output.stderr.trim().ifEmpty { "Unknown error" }
                return WorktreeOperationResult.Failure(
                    "Failed to merge ${source.displayName} into ${target.displayName}",
                    errorMsg
                )
            }

            notifyWorktreesChanged()

            WorktreeOperationResult.Success(
                "Merged ${source.displayName} into ${target.displayName}",
                output.stdout.trim().ifBlank { null }
            )
        } catch (e: Exception) {
            WorktreeOperationResult.Failure(
                "Error merging worktree",
                e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Checks if Git is available in the system.
     */
    fun isGitAvailable(): Boolean {
        return try {
            val output = executeGitCommand(null, "--version")
            output.exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the current project is a Git repository.
     * This method is safe to call from a ReadAction as it uses IntelliJ's VCS APIs.
     */
    fun isGitRepository(): Boolean {
        return try {
            val repositoryManager = VcsRepositoryManager.getInstance(project)
            // Check if there's at least one Git repository in the project
            val projectPath = getProjectPath() ?: return false
            val repository = repositoryManager.getRepositoryForRootQuick(
                com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(projectPath.toString())
            )
            repository is GitRepository
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Executes a Git command and returns the output.
     */
    private fun executeGitCommand(workingDir: Path?, vararg args: String): ProcessOutput {
        val commandLine = GeneralCommandLine("git")
        commandLine.addParameters(*args)
        
        if (workingDir != null) {
            commandLine.setWorkDirectory(workingDir.toFile())
        }
        
        return ExecUtil.execAndGetOutput(commandLine, 30000)
    }

    /**
     * Parses the output of `git worktree list --porcelain`.
     */
    private fun parseWorktreeList(output: String): List<WorktreeInfo> {
        val worktrees = mutableListOf<WorktreeInfo>()
        val lines = output.lines()
        
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            
            if (line.startsWith("worktree ")) {
                val path = Paths.get(line.substring(9))
                var branch: String? = null
                var commit = ""
                var isLocked = false
                var isPrunable = false
                var isBare = false
                
                // Parse subsequent lines for this worktree
                i++
                while (i < lines.size && lines[i].isNotBlank()) {
                    val attrLine = lines[i].trim()
                    when {
                        attrLine.startsWith("HEAD ") -> commit = attrLine.substring(5)
                        attrLine.startsWith("branch ") -> branch = attrLine.substring(7)
                        attrLine == "bare" -> isBare = true
                        attrLine == "locked" -> isLocked = true
                        attrLine == "prunable" -> isPrunable = true
                    }
                    i++
                }
                
                if (commit.isNotEmpty()) {
                    worktrees.add(
                        WorktreeInfo(
                            path = path,
                            branch = branch,
                            commit = commit,
                            isLocked = isLocked,
                            isPrunable = isPrunable,
                            isBare = isBare
                        )
                    )
                }
            } else {
                i++
            }
        }
        
        return worktrees
    }

    /**
     * Gets the project's base path.
     */
    private fun getProjectPath(): Path? {
        val basePath = project.basePath ?: return null
        val path = Paths.get(basePath)
        return if (path.exists()) path else null
    }

    private fun notifyWorktreesChanged() {
        project.messageBus.syncPublisher(WORKTREE_TOPIC).worktreesChanged()
    }

    private fun worktreeRef(worktree: WorktreeInfo): String {
        val branch = worktree.branch
        return branch?.ifBlank { null } ?: worktree.commit
    }

    private fun createInitialCommit(projectPath: Path, message: String): WorktreeOperationResult {
        return try {
            val output = executeGitCommand(projectPath, "commit", "--allow-empty", "-m", message)
            if (output.exitCode != 0) {
                val errorMsg = output.stderr.trim().ifEmpty { "Unknown error" }
                LOG.warn("Failed to create initial commit: $errorMsg")
                Failure("Failed to create initial commit", errorMsg)
            } else {
                LOG.info("Created initial commit in repository at $projectPath")
                Success("Created initial commit")
            }
        } catch (e: Exception) {
            LOG.error("Error creating initial commit", e)
            Failure("Error creating initial commit", e.message ?: "Unknown error")
        }
    }
}
