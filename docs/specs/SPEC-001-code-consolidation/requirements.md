# Requirements Document

## Introduction

This specification defines requirements for eliminating code duplication and standardizing patterns across the Git Worktree Manager plugin. The refactoring addresses three critical areas of duplication: notification management, result handling, and async operations. The goal is to improve maintainability, ensure consistent user experience, and reduce the risk of bugs by centralizing common patterns into reusable utilities.

**Scope:** This refactoring focuses on Phase 1 high-priority items only:
- Notification creation patterns (20+ duplicate call sites)
- WorktreeOperationResult handling patterns (5+ duplicate implementations)
- Async operation and EDT threading patterns (6+ duplicate implementations)

**Out of Scope:** Phase 2 and 3 items (BaseWorktreeAction, WorktreeFilters, dialog base classes) are deferred to future work.

---

## Requirements

### Requirement 1: Centralized Notification Management

**User Story:** As a plugin developer, I want all notification creation to use a single utility class, so that notification behavior is consistent and easy to modify across the entire plugin.

#### Acceptance Criteria

1. WHEN the plugin needs to show a notification THEN the system SHALL use the WorktreeNotifications utility class
2. WHEN WorktreeNotifications is used THEN the system SHALL use the "Git Worktree" notification group ID defined in plugin.xml
3. WHEN showing a success notification THEN the system SHALL use NotificationType.INFORMATION
4. WHEN showing an error notification THEN the system SHALL use NotificationType.ERROR
5. WHEN showing a warning notification THEN the system SHALL use NotificationType.WARNING
6. WHEN showing an error with details THEN the system SHALL append details with format "error\n\nDetails: details"
7. WHEN details are null or blank THEN the system SHALL NOT append the details section
8. WHEN any notification method is called THEN the system SHALL be thread-safe and work from any thread
9. WHEN the refactoring is complete THEN the system SHALL have zero hardcoded "Git Worktree" strings outside of WorktreeNotifications
10. WHEN the refactoring is complete THEN the system SHALL have zero direct NotificationGroupManager.getInstance() calls outside of WorktreeNotifications

---

### Requirement 2: Standardized Result Handling

**User Story:** As a plugin developer, I want WorktreeOperationResult handling to be standardized, so that success/failure notifications and user prompts are consistent across all operations.

#### Acceptance Criteria

1. WHEN a WorktreeOperationResult.Success is received THEN the system SHALL show a success notification with the result message
2. WHEN a WorktreeOperationResult.Failure is received THEN the system SHALL show an error notification with error and details
3. WHEN a WorktreeOperationResult.RequiresInitialCommit is received THEN the system SHALL invoke the custom handler if provided
4. WHEN a WorktreeOperationResult.RequiresInitialCommit is received AND no custom handler is provided THEN the system SHALL show a default info message
5. WHEN promptToOpen is true AND the result is Success THEN the system SHALL show a "Would you like to open the worktree?" dialog
6. WHEN the user selects "Yes" to open worktree THEN the system SHALL call ProjectUtil.openOrImport with the worktree path
7. WHEN the user selects "No" to open worktree THEN the system SHALL take no further action
8. WHEN promptToOpen is true AND worktreePath is null THEN the system SHALL throw IllegalArgumentException
9. WHEN WorktreeResultHandler.handle is called THEN the system SHALL execute on the EDT
10. WHEN opening a worktree fails THEN the system SHALL show an error notification with the exception message

---

### Requirement 3: Consolidated Async Operations

**User Story:** As a plugin developer, I want async worktree loading operations to use a single utility, so that EDT threading is handled correctly and consistently across all actions and UI components.

#### Acceptance Criteria

1. WHEN loadWorktreesWithCurrent is called THEN the system SHALL call service.listWorktrees() and service.getCurrentWorktree() in parallel
2. WHEN both async operations complete successfully THEN the system SHALL invoke onSuccess callback on the EDT with (worktrees, current)
3. WHEN either async operation fails THEN the system SHALL invoke onError callback on the EDT with the exception
4. WHEN onError is not provided AND an error occurs THEN the system SHALL show a default error notification
5. WHEN loadWorktrees is called THEN the system SHALL call service.listWorktrees() asynchronously
6. WHEN listWorktrees completes successfully THEN the system SHALL invoke onSuccess callback on the EDT with worktrees list
7. WHEN all callbacks are invoked THEN the system SHALL use ModalityState.nonModal for invokeLater
8. WHEN any async operation is in progress THEN the system SHALL NOT block the EDT
9. WHEN the refactoring is complete THEN the system SHALL have zero direct service.listWorktrees().whenComplete patterns outside of WorktreeAsyncOperations
10. WHEN the refactoring is complete THEN the system SHALL have zero EDT violations logged in idea.log

---

### Requirement 4: Code Duplication Reduction

**User Story:** As a plugin maintainer, I want duplicated code to be eliminated, so that bug fixes and improvements only need to be applied in one location.

#### Acceptance Criteria

1. WHEN the refactoring is complete THEN the system SHALL have reduced notification-related code by at least 50 lines
2. WHEN the refactoring is complete THEN the system SHALL have reduced result handling code by at least 100 lines
3. WHEN the refactoring is complete THEN the system SHALL have reduced async operation code by at least 80 lines
4. WHEN the refactoring is complete THEN the system SHALL have removed duplicate notify() helper methods from RenameWorktreeAction and MergeWorktreeAction
5. WHEN the refactoring is complete THEN the system SHALL have migrated all 7 action classes to use the new utilities
6. WHEN the refactoring is complete THEN the system SHALL have migrated all 2 UI component classes to use the new utilities
7. WHEN the refactoring is complete THEN the system SHALL have zero compiler warnings introduced by the refactoring
8. WHEN the refactoring is complete THEN the system SHALL follow Kotlin coding conventions in all new utility classes

---

### Requirement 5: Backward Compatibility

**User Story:** As a plugin user, I want the refactoring to preserve all existing functionality, so that my workflow is not disrupted.

#### Acceptance Criteria

1. WHEN the refactoring is complete THEN the system SHALL support all existing worktree operations (create, switch, delete, rename, compare, merge)
2. WHEN any worktree operation is performed THEN the system SHALL show the same notifications as before the refactoring
3. WHEN any worktree operation fails THEN the system SHALL show the same error messages as before the refactoring
4. WHEN a worktree is created successfully THEN the system SHALL prompt to open in new window as before
5. WHEN an empty repository requires initial commit THEN the system SHALL show the same prompt as before
6. WHEN the status bar widget is clicked THEN the system SHALL show the same popup menu as before
7. WHEN the manage worktrees dialog is refreshed THEN the system SHALL update the table as before
8. WHEN the refactoring is complete THEN the system SHALL have 100% of existing tests passing without modification
9. WHEN the refactoring is complete THEN the system SHALL have zero functional regressions

---

### Requirement 6: Testing and Quality

**User Story:** As a plugin developer, I want comprehensive tests for the new utilities, so that I can be confident they work correctly and prevent regressions.

#### Acceptance Criteria

1. WHEN WorktreeNotifications is implemented THEN the system SHALL have unit tests with >85% code coverage
2. WHEN WorktreeResultHandler is implemented THEN the system SHALL have unit tests with >85% code coverage
3. WHEN WorktreeAsyncOperations is implemented THEN the system SHALL have unit tests with >85% code coverage
4. WHEN any utility class is implemented THEN the system SHALL have comprehensive KDoc documentation for all public methods
5. WHEN any utility class is implemented THEN the system SHALL document thread safety requirements
6. WHEN any utility class is implemented THEN the system SHALL document all parameters and return values
7. WHEN all migrations are complete THEN the system SHALL pass all existing test suites (GitWorktreeServiceTest, action tests, UI tests)
8. WHEN all migrations are complete THEN the system SHALL have zero EDT violations in test execution
9. WHEN manual testing is performed THEN the system SHALL show correct notifications for all operations
10. WHEN manual testing is performed THEN the system SHALL maintain responsive UI during all async operations

---

### Requirement 7: Documentation and Maintainability

**User Story:** As a future developer, I want clear documentation of the refactoring, so that I understand the architecture and can maintain the code effectively.

#### Acceptance Criteria

1. WHEN the refactoring is complete THEN the system SHALL have updated AGENTS.md with refactoring completion notes
2. WHEN any utility class is created THEN the system SHALL include a KDoc header explaining its purpose
3. WHEN any utility class is created THEN the system SHALL include usage examples in KDoc
4. WHEN any migration commit is made THEN the system SHALL follow Conventional Commits format
5. WHEN any migration commit is made THEN the system SHALL reference this specification in the commit message
6. WHEN the refactoring is complete THEN the system SHALL have a complete specification document in docs/specs/
7. WHEN the refactoring is complete THEN the system SHALL have a quick reference guide for the new utilities
8. WHEN the refactoring is complete THEN the system SHALL have documented rollback procedures for each migration phase

