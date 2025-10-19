package com.adobe.ideaworktrees.services

import com.intellij.openapi.util.io.FileUtil
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
}
