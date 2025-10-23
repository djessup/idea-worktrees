package au.id.deejay.ideaworktrees

import au.id.deejay.ideaworktrees.model.WorktreeInfo
import au.id.deejay.ideaworktrees.services.GitWorktreeService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import git4idea.GitVcs
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.io.path.exists
import kotlin.io.path.writeText

/**
 * Shared test fixture that provisions a git repository in the test project and
 * offers helper utilities for exercising worktree behaviour.
 */
abstract class AbstractGitWorktreeTestCase : BasePlatformTestCase() {

    protected lateinit var worktreesRoot: Path
    private val gitExecutable: String by lazy { selectGitExecutable() }

    protected val projectPath: Path
        get() = Paths.get(requireNotNull(project.basePath) { "Project basePath should not be null" })

    /**
     * Prepares the base Git repository and registers VCS mappings before each test.
     */
    override fun setUp() {
        super.setUp()
        worktreesRoot = Files.createTempDirectory("git-worktree-tests")
        initGitRepository()
        registerGitMapping()
        GitWorktreeService.getInstance(project).forceGitRepositoryForTests(true)
    }

    /**
     * Cleans up VCS mappings and worktree data while tolerating cleanup exceptions.
     */
    override fun tearDown() {
        try {
            // Clean up any untracked files to prevent test contamination
            cleanupUntrackedFiles()

            ApplicationManager.getApplication().invokeAndWait {
                ProjectLevelVcsManager.getInstance(project).setDirectoryMappings(emptyList())
            }
            FileUtil.delete(worktreesRoot.toFile())
            GitWorktreeService.getInstance(project).forceGitRepositoryForTests(false)
        } finally {
            super.tearDown()
        }
    }

    /**
     * Initializes a git repository with standard configuration and default ignores.
     */
    protected fun initGitRepository() {
        FileUtil.delete(projectPath.resolve(".git").toFile())
        runGit("init")
        runGit("config", "user.name", "Test User")
        runGit("config", "user.email", "test@example.com")

        // Ignore IDE/test artifacts so worktrees are clean unless tests explicitly create files
        val gitignore = projectPath.resolve(".gitignore")
        val ignoreContents = sequenceOf(
            "# IntelliJ project files",
            ".idea/",
            "*.iml",
            "out/",
            "# Gradle/Build output (in case the fixture places anything nearby)",
            "build/",
            ".gradle/",
            "# Misc",
            "*.log",
            ".DS_Store"
        ).joinToString(separator = "\n", postfix = "\n")
        Files.writeString(gitignore, ignoreContents)
        // Stage .gitignore but do not commit here (some tests verify behavior with no commits yet)
        runGit("add", ".gitignore")
    }

    /**
     * Removes any untracked files left behind by tests to keep fixtures clean.
     */
    private fun cleanupUntrackedFiles() {
        try {
            // Only clean up if .git directory exists
            if (!projectPath.resolve(".git").exists()) {
                return
            }

            // Get list of untracked files
            val statusOutput = runGit("status", "--porcelain")
            val untrackedFiles = statusOutput.lines()
                .filter { it.startsWith("??") }
                .map { it.substring(3).trim() }
                .filter { it.isNotBlank() && it != ".gitignore" }

            // Delete untracked files to prevent contamination
            for (file in untrackedFiles) {
                val filePath = projectPath.resolve(file)
                if (filePath.exists()) {
                    FileUtil.delete(filePath.toFile())
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors - tearDown should not fail the test
        }
    }

    /**
     * Creates an empty commit with the provided message.
     */
    protected fun createEmptyCommit(message: String) {
        runGit("commit", "--allow-empty", "-m", message)
    }

    /**
     * Executes a git command in the provided working directory and captures stdout.
     *
     * @param args Git arguments to pass.
     * @param workingDir Directory to run the command in (defaults to project root).
     */
    protected fun runGit(vararg args: String, workingDir: Path = projectPath): String {
        val command = mutableListOf(gitExecutable)
        command.addAll(args)

        Files.createDirectories(workingDir)

        val processBuilder = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)

        processBuilder.environment()["PATH"] = System.getenv("PATH")

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            throw AssertionError("Git command failed: ${command.joinToString(" ")}\n$output")
        }
        return output
    }

    /**
     * @return A path under the temporary worktree root for the supplied name.
     */
    protected fun worktreePath(name: String): Path = worktreesRoot.resolve(name)

    /**
     * Normalizes a path for comparison, accounting for macOS `/private` prefixes.
     */
    protected fun normalizePath(path: Path): String {
        return path.toAbsolutePath().normalize().toString().removePrefix("/private")
    }

    /**
     * Locates the primary worktree (matching the project path or flagged as main).
     */
    protected fun findMainWorktree(worktrees: List<WorktreeInfo>): WorktreeInfo {
        return worktrees.firstOrNull { normalizePath(it.path) == normalizePath(projectPath) }
            ?: worktrees.firstOrNull { it.isMain }
            ?: throw IllegalStateException("Main worktree not found among ${worktrees.map { it.path }}")
    }

    /**
     * Resolves a worktree by branch name, optionally falling back to path matching.
     */
    protected fun findWorktreeByBranch(
        worktrees: List<WorktreeInfo>,
        branchName: String,
        fallbackPath: Path? = null
    ): WorktreeInfo {
        val normalizedBranch = branchName.removePrefix("refs/heads/")
        for (info in worktrees) {
            val rawBranch = info.branch?.trim()
            val normalized = rawBranch?.removePrefix("refs/heads/")
            val display = info.displayName.trim()
            val matches = normalized == normalizedBranch ||
                rawBranch == branchName ||
                rawBranch == normalizedBranch ||
                display == normalizedBranch ||
                display == branchName
            if (matches) {
                return info
            }
        }

        if (fallbackPath != null) {
            val normalizedPath = normalizePath(fallbackPath)
            for (info in worktrees) {
                val infoPath = normalizePath(info.path)
                if (infoPath == normalizedPath) {
                    return info
                }
            }
        }

        throw NoSuchElementException(
            "Worktree not found with branch $branchName. Available: ${worktrees.map { it.branch ?: it.path }}"
        )
    }

    /**
     * Waits for a [CompletableFuture] with a 30 second timeout, failing the test on timeout.
     */
    protected fun <T> CompletableFuture<T>.await(): T = get(30, TimeUnit.SECONDS)

    /**
     * Writes a file relative to the worktree path, creating directories as needed.
     */
    protected fun Path.writeFile(relative: String, content: String) {
        val file = resolve(relative)
        Files.createDirectories(file.parent)
        file.writeText(content)
    }

    /**
     * Selects an appropriate git executable for test runs.
     */
    private fun selectGitExecutable(): String {
        val explicit = System.getenv("GIT_EXECUTABLE")?.takeIf { it.isNotBlank() }
        if (explicit != null && Files.isExecutable(Paths.get(explicit))) {
            return explicit
        }

        val candidates = listOf(
            Paths.get("/usr/bin/git"),
            Paths.get("/usr/local/bin/git")
        )

        for (candidate in candidates) {
            // Use the first executable Git binary available on the test machine.
            if (Files.isExecutable(candidate)) {
                return candidate.toString()
            }
        }

        return "git"
    }

    /**
     * Registers the project directory as a Git root with the VCS subsystem.
     */
    private fun registerGitMapping() {
        ApplicationManager.getApplication().invokeAndWait {
            val manager = ProjectLevelVcsManager.getInstance(project)
            manager.setDirectoryMappings(
                listOf(VcsDirectoryMapping(projectPath.toString(), GitVcs.NAME))
            )
        }
    }
}
