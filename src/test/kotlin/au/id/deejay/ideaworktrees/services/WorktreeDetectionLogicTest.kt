package au.id.deejay.ideaworktrees.services

import com.intellij.openapi.util.io.FileUtil
import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class WorktreeDetectionLogicTest {

    @Test
    fun detectMainWorktreeWhenGitdirFilePointsToRepositoryRoot() {
        val worktreeRoot = Files.createTempDirectory("main-worktree")
        val gitDirRoot = Files.createTempDirectory("main-worktree-gitdir")

        try {
            val gitFile = worktreeRoot.resolve(".git")
            Files.writeString(gitFile, "gitdir: ${gitDirRoot}\n")

            val detected = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = false
            )

            assertTrue(
                "Expected main worktree detection to succeed when gitdir does not point to a worktrees folder.",
                detected
            )
        } finally {
            FileUtil.delete(worktreeRoot.toFile())
            FileUtil.delete(gitDirRoot.toFile())
        }
    }

    @Test
    fun detectSecondaryWorktreeWhenGitdirPointsToWorktreesFolder() {
        val worktreeRoot = Files.createTempDirectory("secondary-worktree")
        val gitDirRoot = Files.createTempDirectory("secondary-worktree-gitdir")

        try {
            val nestedGitDir = gitDirRoot.resolve("worktrees").resolve("secondary")
            Files.createDirectories(nestedGitDir)

            val gitFile = worktreeRoot.resolve(".git")
            Files.writeString(gitFile, "gitdir: ${nestedGitDir}\n")

            val detected = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = false
            )

            assertFalse(
                "Expected detection to treat gitdir pointing to a worktrees folder as a secondary worktree.",
                detected
            )
        } finally {
            FileUtil.delete(worktreeRoot.toFile())
            FileUtil.delete(gitDirRoot.toFile())
        }
    }

    @Test
    fun detectMainWorktreeWhenGitDirectoryExists() {
        val worktreeRoot = Files.createTempDirectory("main-directory-worktree")
        try {
            Files.createDirectories(worktreeRoot.resolve(".git"))

            val detected = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = false
            )

            assertTrue("Expected directory-based .git to be treated as main worktree", detected)
        } finally {
            FileUtil.delete(worktreeRoot.toFile())
        }
    }

    @Test
    fun detectSecondaryWorktreeWhenGitdirSymlinkTargetsWorktrees() {
        val worktreeRoot = Files.createTempDirectory("symlink-worktree")
        val gitDirRoot = Files.createTempDirectory("symlink-worktree-gitdir")

        try {
            val symlinkTargetDir = gitDirRoot.resolve("worktrees").resolve("linked")
            Files.createDirectories(symlinkTargetDir)
            val symlinkTarget = symlinkTargetDir.resolve("config")
            Files.writeString(symlinkTarget, "gitdir: placeholder")

            val gitLink = worktreeRoot.resolve(".git")
            try {
                Files.createSymbolicLink(gitLink, symlinkTarget)
            } catch (ex: IOException) {
                if (ex.message?.contains("Permission denied", ignoreCase = true) == true) {
                    assumeTrue("Skipping symlink test: symbolic links not permitted", false)
                } else {
                    throw ex
                }
            } catch (ex: UnsupportedOperationException) {
                assumeTrue("Skipping symlink test: symbolic links unsupported", false)
            }

            if (!Files.isSymbolicLink(gitLink)) {
                assumeTrue("Skipping symlink test: filesystem did not create symbolic link", false)
            }

            val rawLink = Files.readSymbolicLink(gitLink)
            if (!rawLink.toString().contains("worktrees", ignoreCase = true)) {
                assumeTrue("Skipping symlink test: link target did not include worktrees segment", false)
            }

            val normalizedCandidate = if (rawLink.isAbsolute) {
                rawLink.normalize()
            } else {
                gitLink.parent?.resolve(rawLink)?.normalize()
            }
            val normalizedPath = normalizedCandidate ?: run {
                assumeTrue("Skipping symlink test: normalized path missing worktrees segment", false)
                return
            }
            if (!normalizedPath.toString().contains("worktrees", ignoreCase = true)) {
                assumeTrue("Skipping symlink test: normalized path missing worktrees segment", false)
            }
            val hasSegment = normalizedPath.iterator().asSequence().any {
                it.toString().equals("worktrees", ignoreCase = true)
            }
            if (!hasSegment) {
                assumeTrue("Skipping symlink test: iterator did not expose worktrees segment", false)
            }

            val detected = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = false
            )

            assertFalse("Symlink pointing into worktrees folder should mark as secondary", detected)
        } finally {
            FileUtil.delete(worktreeRoot.toFile())
            FileUtil.delete(gitDirRoot.toFile())
        }
    }

    @Test
    fun defaultValueUsedWhenGitdirLineMissing() {
        val worktreeRoot = Files.createTempDirectory("missing-directive-worktree")
        try {
            val gitFile = worktreeRoot.resolve(".git")
            Files.writeString(gitFile, "core.repositoryformatversion=0\n")

            val detectedWithTrue = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = true
            )
            assertTrue("Default true flag should be respected when gitdir directive missing", detectedWithTrue)

            val detectedWithFalse = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = false
            )
            assertFalse("Default false flag should be respected when gitdir directive missing", detectedWithFalse)
        } finally {
            FileUtil.delete(worktreeRoot.toFile())
        }
    }

    @Test
    fun detectSecondaryWorktreeWhenGitdirRelativePathPointsToWorktrees() {
        val worktreeRoot = Files.createTempDirectory("relative-worktree")
        try {
            val gitFile = worktreeRoot.resolve(".git")
            Files.writeString(gitFile, "gitdir: worktrees/relative\n")

            val detected = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = false
            )

            assertFalse(
                "Relative gitdir pointing inside worktrees should be treated as secondary",
                detected
            )
        } finally {
            FileUtil.delete(worktreeRoot.toFile())
        }
    }

    @Test
    fun detectMainWorktreeWithUppercaseGitdirDirective() {
        val worktreeRoot = Files.createTempDirectory("uppercase-gitdir-worktree")
        val gitDirRoot = Files.createTempDirectory("uppercase-gitdir-target")

        try {
            val gitFile = worktreeRoot.resolve(".git")
            Files.writeString(gitFile, "GITDIR: ${gitDirRoot}\n")

            val detected = determineIfMainWorktree(
                path = worktreeRoot,
                isBare = false,
                defaultIfUnknown = false
            )

            assertTrue("Uppercase gitdir directive should still be recognised", detected)
        } finally {
            FileUtil.delete(worktreeRoot.toFile())
            FileUtil.delete(gitDirRoot.toFile())
        }
    }
}
