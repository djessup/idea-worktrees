package com.adobe.ideaworktrees.services

import com.adobe.ideaworktrees.model.WorktreeInfo
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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
     * @return true if the worktree was created successfully, false otherwise
     */
    fun createWorktree(path: Path, branch: String, createBranch: Boolean = true): Boolean {
        val projectPath = getProjectPath() ?: return false

        return try {
            // Check if repository has commits
            val hasCommits = hasCommits(projectPath)

            val args = mutableListOf("worktree", "add")

            if (createBranch) {
                if (hasCommits) {
                    // Normal case: create new branch from HEAD
                    args.add("-b")
                    args.add(branch)
                    args.add(path.toString())
                    args.add("HEAD")
                } else {
                    // Repository has no commits: create orphan branch
                    args.add("--orphan")
                    args.add(branch)
                    args.add(path.toString())
                }
            } else {
                // Checkout existing branch
                args.add(path.toString())
                args.add(branch)
            }

            val output = executeGitCommand(projectPath, *args.toTypedArray())
            if (output.exitCode != 0) {
                LOG.warn("Failed to create worktree: ${output.stderr}")
                return false
            }

            LOG.info("Created worktree at $path for branch $branch")
            true
        } catch (e: Exception) {
            LOG.error("Error creating worktree", e)
            false
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
     * @return true if the worktree was deleted successfully, false otherwise
     */
    fun deleteWorktree(path: Path, force: Boolean = false): Boolean {
        val projectPath = getProjectPath() ?: return false
        
        return try {
            val args = mutableListOf("worktree", "remove")
            if (force) {
                args.add("--force")
            }
            args.add(path.toString())
            
            val output = executeGitCommand(projectPath, *args.toTypedArray())
            if (output.exitCode != 0) {
                LOG.warn("Failed to delete worktree: ${output.stderr}")
                return false
            }
            
            LOG.info("Deleted worktree at $path")
            true
        } catch (e: Exception) {
            LOG.error("Error deleting worktree", e)
            false
        }
    }

    /**
     * Moves/renames a worktree from one path to another.
     *
     * @param oldPath The current path of the worktree
     * @param newPath The new path for the worktree
     * @return true if the worktree was moved successfully, false otherwise
     */
    fun moveWorktree(oldPath: Path, newPath: Path): Boolean {
        val projectPath = getProjectPath() ?: return false
        
        return try {
            val output = executeGitCommand(
                projectPath,
                "worktree", "move",
                oldPath.toString(),
                newPath.toString()
            )
            
            if (output.exitCode != 0) {
                LOG.warn("Failed to move worktree: ${output.stderr}")
                return false
            }
            
            LOG.info("Moved worktree from $oldPath to $newPath")
            true
        } catch (e: Exception) {
            LOG.error("Error moving worktree", e)
            false
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
}

