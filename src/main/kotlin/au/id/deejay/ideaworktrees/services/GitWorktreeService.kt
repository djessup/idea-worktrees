package au.id.deejay.ideaworktrees.services

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult.Failure
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult.RequiresInitialCommit
import au.id.deejay.ideaworktrees.model.WorktreeOperationResult.Success
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import git4idea.repo.GitRepository
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import kotlin.io.path.exists
import org.jetbrains.annotations.TestOnly

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

        /**
         * Returns the project-scoped [GitWorktreeService] instance.
         */
        fun getInstance(project: Project): GitWorktreeService = project.service()
    }

    @Volatile
    private var treatAsGitRepositoryInTests: Boolean = false

    /**
     * Lists all worktrees for the current project's Git repository.
     * Returns an empty list if the project is not a Git repository or if Git is not available.
     */
    fun listWorktrees(): CompletableFuture<List<WorktreeInfo>> {
        return runAsync("listWorktrees") { listWorktreesInternal() }
    }

    /**
     * Synchronously queries Git for the current set of worktrees.
     *
     * @return List of [WorktreeInfo] parsed from `git worktree list --porcelain`, or empty when unavailable.
     */
    private fun listWorktreesInternal(): List<WorktreeInfo> {
        val projectPath = getProjectPath() ?: return emptyList()

        return try {
            // Capture the raw porcelain representation so we can parse worktree metadata.
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
    fun getCurrentWorktree(): CompletableFuture<WorktreeInfo?> {
        return runAsync("getCurrentWorktree") { getCurrentWorktreeInternal() }
    }

    /**
     * Resolves the current worktree by matching the project path against the known worktree roots.
     *
     * @return The [WorktreeInfo] whose path contains the project base directory, or null if none match.
     */
    private fun getCurrentWorktreeInternal(): WorktreeInfo? {
        val projectPath = getProjectPath() ?: return null
        val worktrees = listWorktreesInternal()

        return worktrees.find { worktree ->
            // The active worktree is the one that encloses the project's base path.
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
    ): CompletableFuture<WorktreeOperationResult> {
        return runAsync("createWorktree") {
            createWorktreeInternal(
                path,
                branch,
                createBranch,
                allowCreateInitialCommit,
                initialCommitMessage
            )
        }
    }

    /**
     * Performs the synchronous worktree creation logic shared by async callers.
     *
     * @param path Destination directory for the worktree.
     * @param branch Branch to check out in the new worktree.
     * @param createBranch Whether the branch should be created when absent.
     * @param allowCreateInitialCommit Whether to bootstrap a repository without commits.
     * @param initialCommitMessage Message used when seeding an initial commit.
     */
    private fun createWorktreeInternal(
        path: Path,
        branch: String,
        createBranch: Boolean = true,
        allowCreateInitialCommit: Boolean = false,
        initialCommitMessage: String = DEFAULT_INITIAL_COMMIT_MESSAGE
    ): WorktreeOperationResult {
        val projectPath = getProjectPath()
            ?: return WorktreeOperationResult.Failure("Project path not found")

        return try {
            // Gather existing worktrees early to enforce both path and name uniqueness.
            val worktrees = listWorktreesInternal()
            val normalizedTarget = normalizePath(path)

            val conflictingPath = worktrees.firstOrNull { isSamePath(normalizedTarget, it.path) }
            if (conflictingPath != null) {
                return WorktreeOperationResult.Failure(
                    error = "A worktree already exists at '${conflictingPath.path}'.",
                    details = "Choose a different folder name for the new worktree."
                )
            }

            val targetName = normalizedTarget.fileName?.toString()
            if (!targetName.isNullOrBlank()) {
                val conflictingName = worktrees.firstOrNull { existing ->
                    existing.name.equals(targetName, ignoreCase = isFileSystemCaseInsensitive())
                }
                if (conflictingName != null) {
                    return WorktreeOperationResult.Failure(
                        error = "A worktree named '$targetName' already exists.",
                        details = "Existing worktree location: ${conflictingName.path}"
                    )
                }
            }

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
     * Normalizes a path for comparison by resolving symlinks where possible.
     *
     * @param path Candidate path to normalize.
     * @return Real path when accessible, otherwise an absolute normalized variant.
     */
    private fun normalizePath(path: Path): Path {
        return runCatching { path.toRealPath() }.getOrElse {
            // Fallback to a best-effort normalization when we lack permissions to resolve symlinks.
            path.toAbsolutePath().normalize()
        }
    }

    /**
     * Determines whether two paths reference the same location, honoring filesystem case sensitivity.
     *
     * @param target The canonical target path.
     * @param other The comparison path.
     * @return True when both paths represent the same file system location.
     */
    private fun isSamePath(target: Path, other: Path): Boolean {
        val normalizedOther = normalizePath(other)
        return if (isFileSystemCaseInsensitive()) {
            target.toString().equals(normalizedOther.toString(), ignoreCase = true)
        } else {
            target == normalizedOther
        }
    }

    /**
     * Detects whether the underlying operating system performs case-insensitive path comparisons.
     */
    private fun isFileSystemCaseInsensitive(): Boolean {
        val osName = System.getProperty("os.name").lowercase()
        return osName.contains("win") || osName.contains("mac") || osName.contains("darwin")
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
    fun deleteWorktree(path: Path, force: Boolean = false): CompletableFuture<WorktreeOperationResult> {
        return runAsync("deleteWorktree") { deleteWorktreeInternal(path, force) }
    }

    /**
     * Performs the synchronous deletion of a worktree directory.
     *
     * @param path Worktree location to delete.
     * @param force Whether to pass `--force` to Git to remove dirty worktrees.
     */
    private fun deleteWorktreeInternal(path: Path, force: Boolean): WorktreeOperationResult {
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
    fun moveWorktree(oldPath: Path, newPath: Path): CompletableFuture<WorktreeOperationResult> {
        return runAsync("moveWorktree") { moveWorktreeInternal(oldPath, newPath) }
    }

    /**
     * Performs the synchronous move/rename of a worktree on disk.
     *
     * @param oldPath Source location of the worktree.
     * @param newPath Destination location requested by the caller.
     */
    private fun moveWorktreeInternal(oldPath: Path, newPath: Path): WorktreeOperationResult {
        val projectPath = getProjectPath()
            ?: return WorktreeOperationResult.Failure("Project path not found")

        return try {
            // Snapshot the current worktrees so we can confirm the target represents a safe candidate.
            val worktrees = listWorktreesInternal()
            val normalizedOld = normalizePath(oldPath)
            val targetWorktree = worktrees.firstOrNull { isSamePath(normalizedOld, it.path) }
            if (targetWorktree?.isMain == true) {
                return WorktreeOperationResult.Failure(
                    "Renaming the main worktree is not supported.",
                    "Git requires the primary worktree to remain at ${targetWorktree.path}."
                )
            }

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
    fun compareWorktrees(source: WorktreeInfo, target: WorktreeInfo): CompletableFuture<WorktreeOperationResult> {
        return runAsync("compareWorktrees") { compareWorktreesInternal(source, target) }
    }

    /**
     * Produces a diff summary between two worktrees after validating their cleanliness.
     *
     * @param source Worktree providing changes.
     * @param target Worktree to diff against.
     * @return [WorktreeOperationResult] describing the comparison outcome.
     */
    private fun compareWorktreesInternal(
        source: WorktreeInfo,
        target: WorktreeInfo
    ): WorktreeOperationResult {
        val projectPath = getProjectPath()
            ?: return WorktreeOperationResult.Failure("Project path not found")

        return try {
            val dirtyWorktrees = mutableListOf<WorktreeInfo>()
            val inspected = listOf(source, target)
            for (worktree in inspected) {
                // Validate that both worktrees are present on disk before running Git commands.
                if (!worktree.path.exists()) {
                    return WorktreeOperationResult.Failure(
                        "Failed to compare worktrees",
                        "Worktree path does not exist: ${worktree.path}"
                    )
                }

                val statusOutput = executeGitCommand(
                    worktree.path,
                    "status",
                    "--porcelain"
                )

                if (statusOutput.exitCode != 0) {
                    val errorMsg = statusOutput.stderr.trim().ifEmpty { "Unknown error" }
                    return WorktreeOperationResult.Failure(
                        "Failed to compare worktrees",
                        "Unable to inspect ${worktree.displayName}: $errorMsg"
                    )
                }

                if (statusOutput.stdout.isNotBlank()) {
                    // Track worktrees with uncommitted changes so we can instruct the user to clean them up.
                    dirtyWorktrees.add(worktree)
                }
            }

            if (dirtyWorktrees.isNotEmpty()) {
                val summary = dirtyWorktrees.joinToString(separator = "\n") { candidate ->
                    "\"${candidate.displayName}\" at ${candidate.path}"
                }
                return WorktreeOperationResult.Failure(
                    "Uncommitted changes detected.",
                    "Commit, stash, or discard changes in:\n$summary"
                )
            }

            // Build a commit range using the worktree references to compare their histories.
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
    ): CompletableFuture<WorktreeOperationResult> {
        return runAsync("mergeWorktree") {
            mergeWorktreeInternal(
                source,
                target,
                fastForwardOnly
            )
        }
    }

    /**
     * Executes the merge command inside the target worktree.
     *
     * @param source Worktree contributing changes.
     * @param target Worktree receiving the merge.
     * @param fastForwardOnly Whether to forbid merge commits.
     */
    private fun mergeWorktreeInternal(
        source: WorktreeInfo,
        target: WorktreeInfo,
        fastForwardOnly: Boolean
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
        if (treatAsGitRepositoryInTests) {
            return true
        }
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
     * Executes a Git command via the IntelliJ CLI utilities.
     *
     * @param workingDir Optional working directory for Git.
     * @param args Arguments passed to the Git binary.
     * @return [ProcessOutput] containing stdout/stderr/exitCode from the command.
     */
    private fun executeGitCommand(workingDir: Path?, vararg args: String): ProcessOutput {
        assertBackgroundThread()

        val commandLine = GeneralCommandLine("git")
        commandLine.addParameters(*args)

        if (workingDir != null) {
            commandLine.setWorkDirectory(workingDir.toFile())
        }

        // Use a generous timeout—Git operations against large repositories may take a few seconds.
        return ExecUtil.execAndGetOutput(commandLine, 30000)
    }

    /**
     * Converts the porcelain output from `git worktree list` into structured [WorktreeInfo] instances.
     *
     * @param output Raw stdout from the Git command.
     * @return Parsed worktree metadata with best-effort main-worktree detection.
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

                val isMainWorktree = determineIfMainWorktree(
                    path = path,
                    isBare = isBare,
                    defaultIfUnknown = worktrees.isEmpty()
                )

                if (commit.isNotEmpty()) {
                    worktrees.add(
                        WorktreeInfo(
                            path = path,
                            branch = branch,
                            commit = commit,
                            isLocked = isLocked,
                            isPrunable = isPrunable,
                            isBare = isBare,
                            isMain = isMainWorktree
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
     * Resolves the project's base directory as a [Path], returning null when inaccessible.
     */
    private fun getProjectPath(): Path? {
        val basePath = project.basePath ?: return null
        val path = Paths.get(basePath)
        return if (path.exists()) path else null
    }

    /**
     * Broadcasts a worktree change event to interested listeners.
     */
    private fun notifyWorktreesChanged() {
        project.messageBus.syncPublisher(WORKTREE_TOPIC).worktreesChanged()
    }

    /**
     * Derives a Git ref representing the supplied worktree for diff/merge invocations.
     */
    private fun worktreeRef(worktree: WorktreeInfo): String {
        val branch = worktree.branch
        return branch?.ifBlank { null } ?: worktree.commit
    }

    /**
     * Creates an allow-empty commit used to bootstrap repositories with no history.
     *
     * @param projectPath Repository root path.
     * @param message Commit message to author.
     */
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

    /**
     * Runs the provided task on the shared application executor and logs failures consistently.
     *
     * @param operation Name used for logging context.
     * @param task Work to execute off the EDT.
     */
    private fun <T> runAsync(operation: String, task: () -> T): CompletableFuture<T> {
        return CompletableFuture.supplyAsync({
            try {
                task()
            } catch (t: Throwable) {
                LOG.warn("Async operation '$operation' failed", t)
                throw t
            }
        }, AppExecutorUtil.getAppExecutorService())
    }

    /**
     * Asserts that the current thread is not the Event Dispatch Thread to avoid UI freezes.
     */
    private fun assertBackgroundThread() {
        ApplicationManager.getApplication().assertIsNonDispatchThread()
    }

    /**
     * Overrides Git repository detection for tests that use in-memory fixtures.
     */
    @TestOnly
    fun forceGitRepositoryForTests(force: Boolean) {
        treatAsGitRepositoryInTests = force
    }
}

/**
 * Determines whether the supplied path represents the main (non-worktree) checkout.
 *
 * @param path Candidate worktree path.
 * @param isBare True when the repository is bare, which implies main state.
 * @param defaultIfUnknown Value returned when heuristics cannot classify the path.
 */
internal fun determineIfMainWorktree(path: Path, isBare: Boolean, defaultIfUnknown: Boolean = false): Boolean {
    if (isBare) return true

    val gitLocation = path.resolve(".git")

    // The presence of a `.git` directory indicates the root checkout.
    if (Files.isDirectory(gitLocation)) {
        return true
    }

    // A symlink typically points to the gitdir inside the main repository structure.
    if (Files.isSymbolicLink(gitLocation)) {
        val linkTarget = runCatching { Files.readSymbolicLink(gitLocation) }.getOrNull()
        val resolved = resolveGitdirCandidate(gitLocation, linkTarget)
        return resolved?.let { !it.containsWorktreesSegment() } ?: defaultIfUnknown
    }

    // Plain files store a `gitdir:` pointer; inspect it to detect worktree metadata.
    if (Files.isRegularFile(gitLocation)) {
        val rawContents = runCatching { Files.readString(gitLocation) }.getOrNull()
            ?: return defaultIfUnknown

        val gitDirLine = rawContents.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("gitdir:", ignoreCase = true) }
            ?: return defaultIfUnknown

        val gitDirValue = gitDirLine.substringAfter(":", "").trim()
        if (gitDirValue.isEmpty()) {
            return defaultIfUnknown
        }

        val resolved = resolveGitdirCandidate(gitLocation, Paths.get(gitDirValue))
        return resolved?.let { !it.containsWorktreesSegment() } ?: defaultIfUnknown
    }

    return defaultIfUnknown
}

/**
 * Resolves a relative gitdir entry discovered inside `.git` files or symlinks.
 *
 * @param gitLocation Location of the `.git` metadata.
 * @param candidate Raw path read from the gitdir reference.
 * @return Normalized absolute path, or null when it cannot be resolved.
 */
private fun resolveGitdirCandidate(gitLocation: Path, candidate: Path?): Path? {
    candidate ?: return null
    val normalized = if (candidate.isAbsolute) {
        candidate
    } else {
        // Relative entries are resolved against the `.git` location discovered earlier.
        gitLocation.parent?.resolve(candidate) ?: return null
    }
    return runCatching { normalized.normalize() }.getOrNull()
}

/**
 * Checks whether the path contains a worktrees segment, indicating a linked worktree checkout.
 */
private fun Path.containsWorktreesSegment(): Boolean {
    for (segment in this) {
        // Git stores linked worktrees under a `worktrees` directory; detecting that segment identifies non-main roots.
        if (segment.toString().equals("worktrees", ignoreCase = true)) {
            return true
        }
    }
    return false
}
