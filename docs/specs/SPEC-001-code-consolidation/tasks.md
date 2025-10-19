# Implementation Plan

<important>

> [!NOTICE] IMPORTANT Reminder 
> - Keep the tasks.md checklist `[ ]` up to date at all times. 
> - Mark `[~]` when selecting an item to work on, and `[x]` when it is complete.
> - Repeat this reminder out loud between EVERY task to keep it front-of-mind.

</important>

## Phase 1: Create Utility Classes

- [x] 1. Create WorktreeNotifications utility class with comprehensive tests
    - Create `src/main/kotlin/com/adobe/ideaworktrees/utils/WorktreeNotifications.kt`
    - Implement `showSuccess()`, `showError()`, `showWarning()`, `showInfo()` methods
    - Implement `showErrorWithDetails()` with proper formatting logic
    - Add comprehensive KDoc documentation for all public methods
    - Create `src/test/kotlin/com/adobe/ideaworktrees/utils/WorktreeNotificationsTest.kt`
    - Write tests for each notification type (success, error, warning, info)
    - Write tests for error with details formatting (with details, null details, blank details)
    - Write test to verify correct notification group ID is used
    - Verify >85% code coverage
    - Run tests: `./gradlew test --tests "WorktreeNotificationsTest"`
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10, 6.1, 6.4, 6.5, 6.6, 7.2, 7.3_

- [x] 2. Create WorktreeResultHandler utility class with comprehensive tests
    - Create `src/main/kotlin/com/adobe/ideaworktrees/utils/WorktreeResultHandler.kt`
    - Implement `handle()` method with all parameters (successTitle, errorTitle, promptToOpen, worktreePath, onInitialCommitRequired)
    - Implement Success result handling (show notification, optional prompt to open)
    - Implement Failure result handling (show error with details)
    - Implement RequiresInitialCommit handling (custom handler or default prompt)
    - Implement `promptToOpenWorktree()` method with Yes/No dialog and error handling
    - Add parameter validation (throw IllegalArgumentException if promptToOpen=true but worktreePath=null)
    - Add comprehensive KDoc documentation for all public methods
    - Create `src/test/kotlin/com/adobe/ideaworktrees/utils/WorktreeResultHandlerTest.kt`
    - Write tests for Success result handling (with and without promptToOpen)
    - Write tests for Failure result handling (with and without details)
    - Write tests for RequiresInitialCommit (with custom handler and default handler)
    - Write test for parameter validation (promptToOpen without worktreePath)
    - Write test for error handling when opening worktree fails
    - Verify >85% code coverage
    - Run tests: `./gradlew test --tests "WorktreeResultHandlerTest"`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 2.9, 2.10, 6.2, 6.4, 6.5, 6.6, 7.2, 7.3_

- [x] 3. Create WorktreeAsyncOperations utility class with comprehensive tests
    - Create `src/main/kotlin/com/adobe/ideaworktrees/utils/WorktreeAsyncOperations.kt`
    - Implement `loadWorktreesWithCurrent()` method using thenCombine for parallel execution
    - Implement `loadWorktrees()` method for simple worktree loading
    - Ensure all callbacks are invoked on EDT via ApplicationManager.getApplication().invokeLater
    - Use ModalityState.nonModal() for all invokeLater calls
    - Implement default error handling (show notification if onError not provided)
    - Add comprehensive KDoc documentation for all public methods
    - Document threading model and EDT requirements
    - Create `src/test/kotlin/com/adobe/ideaworktrees/utils/WorktreeAsyncOperationsTest.kt`
    - Write tests for loadWorktreesWithCurrent success path
    - Write tests for loadWorktreesWithCurrent error path
    - Write tests for loadWorktrees success path
    - Write tests for loadWorktrees error path
    - Write test for default error handling (no onError callback)
    - Write test to verify callbacks are invoked on EDT
    - Verify >85% code coverage
    - Run tests: `./gradlew test --tests "WorktreeAsyncOperationsTest"`
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 3.10, 6.3, 6.4, 6.5, 6.6, 7.2, 7.3_

- [x] 4. Commit utility classes
    - Run full test suite: `./gradlew test`
    - Verify all new tests pass
    - Verify no compiler warnings introduced
    - Commit with message: `refactor: add utility classes for code consolidation (SPEC-001)`
    - _Requirements: 4.7, 7.4, 7.5_

## Phase 2: Migrate Action Classes

- [x] 5. Migrate ManageWorktreesAction to use new utilities (no changes needed - delegates to dialog)
    - Open `src/main/kotlin/com/adobe/ideaworktrees/actions/ManageWorktreesAction.kt`
    - Replace any direct notification calls with `WorktreeNotifications.*` calls
    - Remove unused imports (NotificationGroupManager, NotificationType if present)
    - Run tests: `./gradlew test --tests "*ManageWorktreesAction*"`
    - Verify tests pass
    - Commit: `refactor(actions): migrate ManageWorktreesAction to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 4.5, 5.1, 5.2, 5.3, 5.8, 7.4, 7.5_

- [x] 6. Migrate SwitchWorktreeAction to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/actions/SwitchWorktreeAction.kt`
    - Replace `service.listWorktrees().thenCombine(service.getCurrentWorktree()).whenComplete` pattern with `WorktreeAsyncOperations.loadWorktreesWithCurrent`
    - Replace notification calls with `WorktreeNotifications.*` calls
    - Remove unused imports and ApplicationManager.getApplication() boilerplate
    - Verify EDT threading is preserved (callbacks in onSuccess)
    - Run tests: `./gradlew test --tests "*SwitchWorktreeAction*"`
    - Verify tests pass
    - Commit: `refactor(actions): migrate SwitchWorktreeAction to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 3.1, 3.2, 3.7, 3.8, 3.9, 4.3, 4.5, 5.1, 5.2, 5.8, 7.4, 7.5_

- [x] 7. Migrate DeleteWorktreeAction to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/actions/DeleteWorktreeAction.kt`
    - Replace async pattern with `WorktreeAsyncOperations.loadWorktreesWithCurrent`
    - Replace notification calls with `WorktreeNotifications.*` calls
    - Replace result handling with `WorktreeResultHandler.handle()` (no promptToOpen needed)
    - Remove unused imports
    - Run tests: `./gradlew test --tests "*DeleteWorktreeAction*"`
    - Verify tests pass
    - Commit: `refactor(actions): migrate DeleteWorktreeAction to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 2.1, 2.2, 3.1, 3.2, 3.9, 4.2, 4.3, 4.5, 5.1, 5.2, 5.3, 5.8, 7.4, 7.5_

- [x] 8. Migrate RenameWorktreeAction to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/actions/RenameWorktreeAction.kt`
    - Replace async pattern with `WorktreeAsyncOperations.loadWorktreesWithCurrent`
    - Replace notification calls with `WorktreeNotifications.*` calls
    - Replace result handling with `WorktreeResultHandler.handle()`
    - Remove duplicate `notify()` helper method
    - Remove unused imports
    - Run tests: `./gradlew test --tests "*RenameWorktreeAction*"`
    - Verify tests pass
    - Commit: `refactor(actions): migrate RenameWorktreeAction to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 2.1, 2.2, 3.1, 3.2, 3.9, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.8, 7.4, 7.5_

- [x] 9. Migrate CompareWorktreeAction to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/actions/CompareWorktreeAction.kt`
    - Replace async pattern with `WorktreeAsyncOperations.loadWorktrees` (only needs worktrees list)
    - Replace notification calls with `WorktreeNotifications.*` calls
    - Replace result handling in compare callback with `WorktreeResultHandler.handle()` or direct notification calls
    - Remove unused imports
    - Run tests: `./gradlew test --tests "*CompareWorktreeAction*"`
    - Verify tests pass
    - Commit: `refactor(actions): migrate CompareWorktreeAction to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 3.5, 3.6, 3.9, 4.3, 4.5, 5.1, 5.2, 5.4, 5.8, 7.4, 7.5_

- [x] 10. Migrate MergeWorktreeAction to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/actions/MergeWorktreeAction.kt`
    - Replace async pattern with `WorktreeAsyncOperations.loadWorktrees`
    - Replace notification calls with `WorktreeNotifications.*` calls
    - Replace result handling with `WorktreeResultHandler.handle()`
    - Remove duplicate `notify()` helper method
    - Remove unused imports
    - Run tests: `./gradlew test --tests "*MergeWorktreeAction*"`
    - Verify tests pass
    - Commit: `refactor(actions): migrate MergeWorktreeAction to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 2.1, 2.2, 3.5, 3.6, 3.9, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.5, 5.8, 7.4, 7.5_

- [~] 11. Migrate CreateWorktreeAction to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/actions/CreateWorktreeAction.kt`
    - Replace notification calls in `handleResult()` with `WorktreeNotifications.*` calls
    - Replace entire `handleResult()` method body with `WorktreeResultHandler.handle()` call
    - Pass `promptToOpen=true` and `worktreePath` to handler
    - Pass custom `onInitialCommitRequired` handler for initial commit flow
    - Remove unused imports
    - Run tests: `./gradlew test --tests "*CreateWorktreeAction*"`
    - Verify tests pass
    - Commit: `refactor(actions): migrate CreateWorktreeAction to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8, 4.1, 4.2, 4.5, 5.1, 5.2, 5.4, 5.8, 7.4, 7.5_

## Phase 3: Migrate UI Components

- [ ] 12. Migrate ManageWorktreesDialog to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/ui/ManageWorktreesDialog.kt`
    - Replace async pattern in `refreshWorktrees()` with `WorktreeAsyncOperations.loadWorktreesWithCurrent`
    - Replace notification calls with `WorktreeNotifications.*` calls
    - Verify cache update logic is preserved
    - Remove unused imports
    - Run tests: `./gradlew test --tests "*ManageWorktreesDialog*"`
    - Verify tests pass
    - Commit: `refactor(ui): migrate ManageWorktreesDialog to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 3.1, 3.2, 3.9, 4.3, 4.6, 5.1, 5.2, 5.7, 5.8, 7.4, 7.5_

- [ ] 13. Migrate WorktreeStatusBarWidget to use new utilities
    - Open `src/main/kotlin/com/adobe/ideaworktrees/ui/WorktreeStatusBarWidget.kt`
    - Replace notification calls with `WorktreeNotifications.*` calls
    - Replace `handleResult()` method in embedded CreateWorktreeDialog with `WorktreeResultHandler.handle()`
    - Verify cache update logic in `updateCacheAsync()` is preserved
    - Remove unused imports
    - Run tests: `./gradlew test --tests "*WorktreeStatusBarWidget*"`
    - Verify tests pass
    - Commit: `refactor(ui): migrate WorktreeStatusBarWidget to use new utilities (SPEC-001)`
    - _Requirements: 1.1, 1.9, 1.10, 2.1, 2.2, 2.5, 2.6, 4.1, 4.2, 4.6, 5.1, 5.2, 5.4, 5.6, 5.8, 7.4, 7.5_

## Phase 4: Cleanup and Validation

- [ ] 14. Verify all duplicated code has been removed
    - Search for remaining `NotificationGroupManager.getInstance()` calls: `grep -r "NotificationGroupManager.getInstance()" src/main/`
    - Verify only WorktreeNotifications.kt contains this pattern
    - Search for remaining `service.listWorktrees().whenComplete` patterns: `grep -r "listWorktrees().whenComplete" src/main/`
    - Verify only WorktreeAsyncOperations.kt contains this pattern
    - Search for remaining `when (result)` result handling patterns in actions
    - Verify result handling is centralized in WorktreeResultHandler
    - _Requirements: 1.9, 1.10, 3.9, 4.1, 4.2, 4.3, 4.4_

- [ ] 15. Run full test suite and verify no regressions
    - Run all tests: `./gradlew test`
    - Verify 100% of existing tests pass
    - Verify no new compiler warnings: `./gradlew build --warning-mode all`
    - Check for EDT violations in test output
    - Verify >85% coverage for new utility classes: `./gradlew test jacocoTestReport`
    - _Requirements: 3.10, 4.7, 5.8, 5.9, 6.1, 6.2, 6.3, 6.7, 6.8_

- [ ] 16. Update documentation
    - Update `AGENTS.md` with refactoring completion notes
    - Add section documenting the new utility classes and their purpose
    - Add note about code duplication reduction (~250+ lines eliminated)
    - Document any learnings or important decisions made during refactoring
    - _Requirements: 7.1, 7.6_

- [ ] 17. Final commit and cleanup
    - Verify all files are properly formatted: `./gradlew ktlintFormat`
    - Verify no unused imports remain
    - Verify all KDoc is complete and accurate
    - Commit: `refactor: complete code consolidation cleanup (SPEC-001)`
    - _Requirements: 4.7, 4.8, 7.4, 7.5_

## Manual Testing (Optional - if IDE access available)

- [ ] 18. Manual smoke testing of all operations
    - Test create worktree → Verify success notification and "open worktree" prompt
    - Test switch worktree → Verify UI remains responsive during load
    - Test delete worktree → Verify success notification
    - Test rename worktree → Verify success notification
    - Test compare worktrees → Verify results dialog appears
    - Test merge worktrees → Verify success/error notification
    - Test error cases → Verify error notifications with details appear
    - Test status bar widget → Verify update doesn't freeze UI
    - Test manage worktrees dialog → Verify refresh updates table correctly
    - Check idea.log for any EDT violations or errors
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 6.9, 6.10_

