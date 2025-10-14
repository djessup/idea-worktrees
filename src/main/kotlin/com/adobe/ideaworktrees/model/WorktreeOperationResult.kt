package com.adobe.ideaworktrees.model

/**
 * Result of a worktree operation.
 */
sealed class WorktreeOperationResult {
    /**
     * Operation completed successfully.
     */
    data class Success(val message: String = "Operation completed successfully") : WorktreeOperationResult()
    
    /**
     * Operation failed with an error.
     */
    data class Failure(val errorMessage: String, val details: String? = null) : WorktreeOperationResult()
    
    /**
     * Check if the operation was successful.
     */
    val isSuccess: Boolean
        get() = this is Success
    
    /**
     * Check if the operation failed.
     */
    val isFailure: Boolean
        get() = this is Failure
    
    /**
     * Get the success message if available.
     */
    fun getSuccessMessage(): String? = (this as? Success)?.message
    
    /**
     * Get the error message if available.
     */
    fun getErrorMessage(): String? = (this as? Failure)?.errorMessage
    
    /**
     * Get the error details if available.
     */
    fun getErrorDetails(): String? = (this as? Failure)?.details
}

