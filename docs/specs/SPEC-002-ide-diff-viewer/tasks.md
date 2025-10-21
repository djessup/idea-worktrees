# Implementation Plan

## Overview

This implementation plan breaks down the IDE diff viewer integration into discrete, testable coding tasks. Each task builds incrementally on previous work, following test-driven development practices where appropriate.

## Tasks

- [ ] 1. Create data models for structured comparison results

    - Create `FileStatus` enum with values: MODIFIED, ADDED, DELETED
    - Create `ChangedFile` data class with `path: String` and `status: FileStatus` properties
    - Create sealed class `WorktreeComparisonResult` with three subclasses:
        - `Success(source: WorktreeInfo, target: WorktreeInfo, changedFiles: List<ChangedFile>)`
        - `Failure(error: String, details: String? = null)`
        - `NoChanges(source: WorktreeInfo, target: WorktreeInfo)`
    - Place these in `au.id.deejay.ideaworktrees.model` package
    - _Requirements: 1.1, 5.1_

- [ ] 2. Write unit tests for Git diff output parsing

    - Create test class `GitWorktreeServiceDiffParsingTest`
    - Write test for parsing `git diff --name-status` output with modified files
    - Write test for parsing output with added files (status 'A')
    - Write test for parsing output with deleted files (status 'D')
    - Write test for parsing output with renamed files (status 'R')
    - Write test for parsing output with copied files (status 'C')
    - Write test for handling empty output (no changes)
    - Write test for handling malformed output
    - _Requirements: 1.1, 2.1_

- [ ] 3. Implement Git diff parsing logic in GitWorktreeService

    - Add private method `parseChangedFiles(output: String): List<ChangedFile>`
    - Implement parsing of `git diff --name-status` format
    - Map Git status codes to FileStatus enum values
    - Handle edge cases (empty output, malformed lines)
    - Verify all tests from task 2 pass
    - _Requirements: 1.1, 2.1_

- [ ] 4. Modify GitWorktreeService.compareWorktrees() to return structured data

    - Change return type from `CompletableFuture<WorktreeOperationResult>` to `CompletableFuture<WorktreeComparisonResult>`
    - Replace `git diff --stat` and `git diff` commands with `git diff --name-status`
    - Call `parseChangedFiles()` to convert Git output to `List<ChangedFile>`
    - Return `WorktreeComparisonResult.Success` with changed files list
    - Return `WorktreeComparisonResult.NoChanges` when no files changed
    - Return `WorktreeComparisonResult.Failure` for errors
    - Keep existing validation logic (uncommitted changes check) unchanged
    - _Requirements: 1.1, 2.1, 5.1, 5.2_

- [ ] 5. Update existing GitWorktreeService tests for new return type

    - Update `GitWorktreeServiceTest.testCompareWorktrees()` to expect `WorktreeComparisonResult`
    - Verify test assertions check for `Success` with correct changed files
    - Update any other tests that call `compareWorktrees()`
    - Ensure all existing tests pass with new return type
    - _Requirements: 5.1_

- [ ] 6. Write unit tests for DiffRequest creation

    - Create test class `CompareWorktreeActionDiffRequestTest`
    - Write test for creating DiffRequest for modified file
    - Write test for creating DiffRequest for added file (empty source content)
    - Write test for creating DiffRequest for deleted file (empty target content)
    - Write test for handling missing file (file path doesn't exist)
    - Write test for creating multiple DiffRequests from comparison result
    - Mock DiffContentFactory and verify correct factory methods are called
    - _Requirements: 1.2, 2.2, 4.1_

- [ ] 7. Implement file content retrieval in CompareWorktreeAction

    - Add private method `getFileContent(worktree: WorktreeInfo, file: ChangedFile, factory: DiffContentFactory): DiffContent`
    - Handle FileStatus.DELETED by returning `factory.createEmpty()`
    - Handle FileStatus.ADDED and MODIFIED by reading file from worktree path
    - Use `LocalFileSystem.getInstance().findFileByNioFile()` to get VirtualFile
    - Return `factory.create(project, virtualFile)` for existing files
    - Return `factory.create("File not found: ${file.path}")` for missing files
    - Verify tests from task 6 pass
    - _Requirements: 1.2, 4.1, 4.4_

- [ ] 8. Implement DiffRequest creation in CompareWorktreeAction

    - Add private method `createDiffRequestForFile(project: Project, result: WorktreeComparisonResult.Success, file: ChangedFile): DiffRequest?`
    - Get DiffContentFactory instance
    - Call `getFileContent()` for source and target worktrees
    - Create title string: "${source.displayName} vs ${target.displayName}: ${file.path}"
    - Return `SimpleDiffRequest` with title, source content, target content, and labels
    - Handle null returns gracefully
    - Verify tests from task 6 pass
    - _Requirements: 1.2, 2.3_

- [ ] 9. Implement DiffRequest list creation in CompareWorktreeAction

    - Add private method `createDiffRequests(project: Project, result: WorktreeComparisonResult.Success): List<DiffRequest>`
    - Iterate through `result.changedFiles`
    - Call `createDiffRequestForFile()` for each changed file
    - Collect non-null DiffRequest objects into list
    - Return list of DiffRequest objects
    - Verify tests from task 6 pass
    - _Requirements: 1.3, 2.1, 2.2_

- [ ] 10. Write integration test for diff viewer invocation

    - Create test class `CompareWorktreeActionIntegrationTest` extending `BasePlatformTestCase`
    - Set up test with two worktrees containing known differences
    - Mock DiffManager to verify `showDiff()` is called
    - Execute compare action
    - Verify DiffManager.showDiff() called with correct project
    - Verify DiffRequestChain contains expected number of requests
    - Verify each DiffRequest has correct title and labels
    - _Requirements: 1.1, 1.2, 2.1_

- [ ] 11. Implement diff viewer display logic in CompareWorktreeAction

    - Add private method `showDiffViewer(project: Project, result: WorktreeComparisonResult.Success)`
    - Use `ApplicationManager.getApplication().executeOnPooledThread()` for background work
    - Call `createDiffRequests()` to build list of DiffRequest objects
    - Use `invokeLater()` to marshal back to EDT
    - If diffRequests is empty, show info message "No differences found"
    - If diffRequests is not empty, create `SimpleDiffRequestChain(diffRequests)`
    - Call `DiffManager.getInstance().showDiff(project, chainRequest)`
    - Wrap in try-catch and show error dialog on exception
    - Verify integration test from task 10 passes
    - _Requirements: 1.1, 1.2, 3.1, 3.2, 6.1, 6.2_

- [ ] 12. Update CompareWorktreeAction.actionPerformed() to use new flow

    - Modify `compareWorktrees().whenComplete()` callback to handle `WorktreeComparisonResult`
    - For `WorktreeComparisonResult.Success`, call `showDiffViewer()`
    - For `WorktreeComparisonResult.NoChanges`, show info message
    - For `WorktreeComparisonResult.Failure`, show error dialog with message and details
    - Remove all references to `DiffResultDialog`
    - Keep existing error handling for `listWorktrees()` unchanged
    - _Requirements: 1.1, 1.4, 4.2, 5.3, 5.4_

- [ ] 13. Remove DiffResultDialog class

    - Delete the `DiffResultDialog` inner class from `CompareWorktreeAction.kt`
    - Verify no other code references DiffResultDialog
    - Verify all tests still pass
    - _Requirements: 5.3_

- [ ] 14. Add error handling tests for diff viewer

    - Write test for handling Git command failure in compareWorktrees()
    - Write test for handling missing file during content retrieval
    - Write test for handling DiffManager.showDiff() exception
    - Write test for handling uncommitted changes (existing validation)
    - Verify error dialogs show appropriate messages
    - _Requirements: 4.1, 4.2, 4.3, 4.4_

- [ ] 15. Add progress indicator for diff viewer loading

    - Modify `showDiffViewer()` to show progress indicator during file content retrieval
    - Use `ProgressManager.getInstance().run()` with `Task.Backgroundable`
    - Set progress text to "Loading diff viewer..."
    - Make task cancellable
    - Handle cancellation gracefully (don't show diff viewer)
    - _Requirements: 6.3, 6.4_

- [ ] 16. Write integration test for complete comparison workflow

    - Create test with two worktrees: one with modified file, one with added file, one with deleted file
    - Execute compare action through UI action system
    - Verify no errors occur
    - Verify DiffManager.showDiff() called
    - Verify correct number of DiffRequests created
    - Verify each DiffRequest has correct content type (empty for added/deleted)
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2_

- [ ] 17. Add test for performance with many changed files

    - Create test with two worktrees containing 100+ changed files
    - Execute compare action
    - Verify operation completes within reasonable time (< 5 seconds)
    - Verify EDT is not blocked during file content retrieval
    - Verify progress indicator is shown
    - _Requirements: 6.1, 6.2, 6.3_

- [ ] 18. Update plugin.xml if needed

    - Verify no changes needed to plugin.xml (DiffManager is part of platform)
    - Verify Git4Idea dependency is still declared (already present)
    - _Requirements: 5.1_

- [ ] 19. Manual testing and verification

    - Build plugin and run in development IDE
    - Create two worktrees with various types of changes
    - Execute "Compare Worktrees" action
    - Verify diff viewer opens with side-by-side comparison
    - Verify syntax highlighting works for different file types
    - Verify navigation between files works
    - Verify keyboard shortcuts work (Next/Previous Difference)
    - Verify line numbers are displayed
    - Test with binary files
    - Test with very large files
    - Test with files containing special characters
    - _Requirements: 1.1, 1.2, 1.3, 2.1, 2.2, 2.3, 2.4, 3.1, 3.2, 3.3, 3.4_

- [ ] 20. Update documentation

    - Update README.md to mention IDE diff viewer integration
    - Update AGENTS.md if any architectural decisions need recording
    - Add code comments explaining DiffRequest creation logic
    - _Requirements: N/A (documentation)_

