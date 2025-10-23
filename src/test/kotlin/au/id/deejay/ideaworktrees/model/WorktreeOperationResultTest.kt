package au.id.deejay.ideaworktrees.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests covering the convenience helpers on [WorktreeOperationResult].
 */
class WorktreeOperationResultTest {

    /**
     * Ensures success results expose success messaging and no error content.
     */
    @Test
    fun successExposeMessages() {
        val result = WorktreeOperationResult.Success(
            message = "Created worktree",
            details = "extra"
        )

        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("Created worktree", result.successMessage())
        assertEquals("extra", result.successDetails())
        assertNull(result.errorMessage())
        assertNull(result.errorDetails())
    }

    /**
     * Verifies failure results surface error details and suppress success metadata.
     */
    @Test
    fun failureExposeErrors() {
        val result = WorktreeOperationResult.Failure(
            error = "Boom",
            details = "stack"
        )

        assertFalse(result.isSuccess)
        assertTrue(result.isFailure)
        assertEquals("Boom", result.errorMessage())
        assertEquals("stack", result.errorDetails())
        assertNull(result.successMessage())
        assertNull(result.successDetails())
    }

    /**
     * Checks the `RequiresInitialCommit` variant mirrors failure semantics for error accessors.
     */
    @Test
    fun requiresInitialCommitBehavesLikeFailureForErrors() {
        val result = WorktreeOperationResult.RequiresInitialCommit("Need commit")

        assertFalse(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("Need commit", result.errorMessage())
        assertNull(result.errorDetails())
        assertNull(result.successMessage())
        assertNull(result.successDetails())
    }
}
