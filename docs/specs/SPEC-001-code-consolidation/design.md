# Design Document

## Overview

This design document describes the architecture and implementation approach for consolidating duplicated code patterns across the Git Worktree Manager plugin. The refactoring introduces three utility classes that centralize notification management, result handling, and async operations.

**Design Goals:**
- Eliminate 250+ lines of duplicated code
- Standardize UX across all worktree operations
- Ensure correct EDT threading throughout the plugin
- Maintain 100% backward compatibility
- Achieve >85% test coverage for new utilities

**Design Principles:**
- **Single Responsibility**: Each utility has one clear purpose
- **Stateless**: All utilities are `object` singletons with no mutable state
- **Composable**: Utilities can be used independently or together
- **Testable**: Clear inputs/outputs for unit testing
- **Thread-Safe**: Proper EDT marshalling throughout

---

## Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Action Classes                            │
│  (Create, Switch, Delete, Rename, Compare, Merge, Manage)   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ├──► WorktreeAsyncOperations
                     │    └──► WorktreeNotifications (error handling)
                     │
                     ├──► WorktreeResultHandler
                     │    └──► WorktreeNotifications (success/error)
                     │
                     └──► WorktreeNotifications (direct calls)
                     
┌─────────────────────────────────────────────────────────────┐
│                    UI Components                             │
│         (WorktreeStatusBarWidget, Dialogs)                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     └──► Same utility dependencies as Actions
```

### Package Structure

```
src/main/kotlin/com/adobe/ideaworktrees/
├── utils/                          (NEW)
│   ├── WorktreeNotifications.kt    (NEW)
│   ├── WorktreeResultHandler.kt    (NEW)
│   └── WorktreeAsyncOperations.kt  (NEW)
├── actions/                        (MODIFIED)
│   ├── CreateWorktreeAction.kt
│   ├── SwitchWorktreeAction.kt
│   ├── DeleteWorktreeAction.kt
│   ├── RenameWorktreeAction.kt
│   ├── CompareWorktreeAction.kt
│   ├── MergeWorktreeAction.kt
│   └── ManageWorktreesAction.kt
├── ui/                             (MODIFIED)
│   ├── WorktreeStatusBarWidget.kt
│   └── ManageWorktreesDialog.kt
├── services/                       (UNCHANGED)
│   └── GitWorktreeService.kt
└── model/                          (UNCHANGED)
    ├── WorktreeInfo.kt
    └── WorktreeOperationResult.kt
```

---

## Components and Interfaces

### 1. WorktreeNotifications Utility

**Purpose:** Centralize all notification creation and display logic.

**Interface:**
```kotlin
object WorktreeNotifications {
    fun showSuccess(project: Project, title: String, message: String)
    fun showError(project: Project, title: String, message: String)
    fun showWarning(project: Project, title: String, message: String)
    fun showInfo(project: Project, title: String, message: String)
    fun showErrorWithDetails(project: Project, title: String, error: String, details: String?)
}
```

**Key Design Decisions:**
- **Singleton object**: No state needed, all methods are pure functions
- **Notification group ID**: Hardcoded constant "Git Worktree" (single source of truth)
- **Thread safety**: IntelliJ platform handles EDT marshalling for notifications automatically
- **Error details formatting**: Consistent "\n\nDetails: " separator when details are present

**Dependencies:**
- `com.intellij.notification.NotificationGroupManager`
- `com.intellij.notification.NotificationType`
- `com.intellij.openapi.project.Project`

---

### 2. WorktreeResultHandler Utility

**Purpose:** Standardize handling of `WorktreeOperationResult` across all operations.

**Interface:**
```kotlin
object WorktreeResultHandler {
    fun handle(
        project: Project,
        result: WorktreeOperationResult,
        successTitle: String = "Operation Successful",
        errorTitle: String = "Operation Failed",
        promptToOpen: Boolean = false,
        worktreePath: Path? = null,
        onInitialCommitRequired: ((WorktreeOperationResult.RequiresInitialCommit) -> Unit)? = null
    )
    
    fun promptToOpenWorktree(project: Project, path: Path)
}
```

**Key Design Decisions:**
- **Default parameters**: Sensible defaults for common cases (successTitle, errorTitle)
- **Optional prompt**: `promptToOpen` flag controls "open worktree" dialog
- **Custom handlers**: `onInitialCommitRequired` allows action-specific behavior
- **Validation**: Throws `IllegalArgumentException` if `promptToOpen=true` but `worktreePath=null`
- **Error handling**: Catches exceptions when opening worktree, shows error notification

**Dependencies:**
- `WorktreeNotifications` (for all notifications)
- `com.intellij.openapi.ui.Messages` (for dialogs)
- `com.intellij.platform.ProjectUtil` (for opening worktrees)
- `com.adobe.ideaworktrees.model.WorktreeOperationResult`

**Behavior by Result Type:**
- **Success**: Show success notification, optionally prompt to open worktree
- **Failure**: Show error notification with details (using `showErrorWithDetails`)
- **RequiresInitialCommit**: Invoke custom handler or show default info message

---

### 3. WorktreeAsyncOperations Utility

**Purpose:** Consolidate async operation patterns for loading worktrees with correct EDT threading.

**Interface:**
```kotlin
object WorktreeAsyncOperations {
    fun loadWorktreesWithCurrent(
        project: Project,
        service: GitWorktreeService,
        onSuccess: (worktrees: List<WorktreeInfo>, current: WorktreeInfo?) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    )
    
    fun loadWorktrees(
        project: Project,
        service: GitWorktreeService,
        onSuccess: (worktrees: List<WorktreeInfo>) -> Unit,
        onError: ((Throwable) -> Unit)? = null
    )
}
```

**Key Design Decisions:**
- **Callback-based**: Uses callbacks instead of returning `CompletableFuture` (simpler for callers)
- **EDT marshalling**: All callbacks invoked via `ApplicationManager.getApplication().invokeLater`
- **Modality**: Uses `ModalityState.nonModal()` for all EDT invocations
- **Default error handling**: Shows notification if `onError` is not provided
- **Parallel execution**: `loadWorktreesWithCurrent` uses `thenCombine` for parallel execution

**Dependencies:**
- `WorktreeNotifications` (for default error handling)
- `com.intellij.openapi.application.ApplicationManager`
- `com.intellij.openapi.application.ModalityState`
- `com.adobe.ideaworktrees.services.GitWorktreeService`
- `com.adobe.ideaworktrees.model.WorktreeInfo`

**Threading Model:**
```
[Caller Thread (EDT)]
    ↓
WorktreeAsyncOperations.loadWorktreesWithCurrent()
    ↓
[Background Thread] service.listWorktrees() + service.getCurrentWorktree()
    ↓
whenComplete { result, error -> ... }
    ↓
[EDT via invokeLater] onSuccess(worktrees, current) or onError(exception)
```

---

## Data Models

### Existing Models (Unchanged)

**WorktreeInfo:**
```kotlin
data class WorktreeInfo(
    val path: Path,
    val branch: String,
    val commit: String,
    val isLocked: Boolean,
    val isPrunable: Boolean,
    val isBare: Boolean,
    val isMain: Boolean
)
```

**WorktreeOperationResult:**
```kotlin
sealed class WorktreeOperationResult {
    data class Success(val message: String, val details: String? = null) : WorktreeOperationResult()
    data class Failure(val error: String, val details: String? = null) : WorktreeOperationResult()
    data class RequiresInitialCommit(val message: String) : WorktreeOperationResult()
}
```

These models remain unchanged. The new utilities consume these models but do not modify them.

---

## Error Handling

### Error Handling Strategy

**1. Notification Errors:**
- **Scenario**: Notification group not found
- **Handling**: IntelliJ platform logs error, notification fails silently
- **Mitigation**: Ensure "Git Worktree" group is registered in plugin.xml

**2. Async Operation Errors:**
- **Scenario**: Git command fails, service throws exception
- **Handling**: `whenComplete` receives error, invokes `onError` callback or shows default notification
- **Mitigation**: Always provide meaningful error messages from service layer

**3. Result Handling Errors:**
- **Scenario**: `promptToOpen=true` but `worktreePath=null`
- **Handling**: Throw `IllegalArgumentException` immediately
- **Mitigation**: Validate parameters at call site

**4. EDT Threading Errors:**
- **Scenario**: Blocking operation called on EDT
- **Handling**: IntelliJ platform logs "Synchronous execution on EDT" warning
- **Mitigation**: All blocking operations run via `WorktreeAsyncOperations`

### Error Message Consistency

All error messages follow this format:
- **Title**: Short, action-specific (e.g., "Failed to Create Worktree")
- **Message**: Main error description
- **Details**: Optional technical details (git output, stack trace)

Example:
```
Title: "Failed to Create Worktree"
Message: "Cannot create worktree: branch 'feature' already exists"
Details: "git worktree add /path/to/worktree feature\nfatal: branch 'feature' already exists"
```

---

## Testing Strategy

### Unit Tests

**WorktreeNotificationsTest:**
- Test each notification type (success, error, warning, info)
- Test error with details formatting
- Test error with null/blank details
- Verify correct notification group ID is used
- Coverage target: 100%

**WorktreeResultHandlerTest:**
- Test Success result handling
- Test Failure result handling with/without details
- Test RequiresInitialCommit with custom handler
- Test RequiresInitialCommit with default handler
- Test promptToOpen flow (Yes/No responses)
- Test validation (promptToOpen without worktreePath)
- Test error handling when opening worktree fails
- Coverage target: >90%

**WorktreeAsyncOperationsTest:**
- Test loadWorktreesWithCurrent success path
- Test loadWorktreesWithCurrent error path
- Test loadWorktrees success path
- Test loadWorktrees error path
- Test default error handling (no onError callback)
- Test callbacks are invoked on EDT
- Coverage target: >85%

### Integration Tests

**Existing Test Suites (Must Pass):**
- `GitWorktreeServiceTest` - Core service tests
- `CreateWorktreeActionTest` - Action tests
- `ManageWorktreesDialogTest` - UI tests
- `WorktreeStatusBarWidgetTest` - Widget tests
- `WorktreeStatusBarWidgetAsyncTest` - Async widget tests

**Regression Testing:**
- All existing tests must pass without modification
- No new EDT violations in test execution
- No new compiler warnings

### Manual Testing

**Test Scenarios:**
1. Create worktree → Verify success notification and "open worktree" prompt
2. Delete worktree → Verify success notification
3. Rename worktree → Verify success notification
4. Compare worktrees → Verify results dialog
5. Merge worktrees → Verify success/error notification
6. Error cases → Verify error notifications with details
7. Status bar widget → Verify update doesn't freeze UI
8. Manage dialog refresh → Verify table updates correctly

---

## Migration Strategy

### Phase 1: Create Utilities (Day 1, Morning)

**Order:**
1. Create `WorktreeNotifications.kt` with tests
2. Create `WorktreeResultHandler.kt` with tests (depends on WorktreeNotifications)
3. Create `WorktreeAsyncOperations.kt` with tests (depends on WorktreeNotifications)

**Commit:** `refactor: add utility classes for code consolidation`

### Phase 2: Migrate Actions (Day 1, Afternoon)

**Order (simplest to most complex):**
1. ManageWorktreesAction
2. SwitchWorktreeAction
3. DeleteWorktreeAction
4. RenameWorktreeAction
5. CompareWorktreeAction
6. MergeWorktreeAction
7. CreateWorktreeAction

**Per-file process:**
1. Replace notification calls with `WorktreeNotifications.*`
2. Replace async patterns with `WorktreeAsyncOperations.*`
3. Replace result handling with `WorktreeResultHandler.handle()`
4. Remove unused imports
5. Run tests
6. Commit: `refactor(actions): migrate [ActionName] to use new utilities`

### Phase 3: Migrate UI Components (Day 2, Morning)

**Order:**
1. ManageWorktreesDialog
2. WorktreeStatusBarWidget

**Commit:** `refactor(ui): migrate UI components to use new utilities`

### Phase 4: Cleanup (Day 2, Afternoon)

1. Remove duplicate `notify()` helpers from RenameWorktreeAction and MergeWorktreeAction
2. Search for remaining `NotificationGroupManager.getInstance()` calls
3. Run full test suite
4. Update AGENTS.md
5. Commit: `refactor: complete code consolidation cleanup`

---

## Performance Considerations

**No Performance Impact Expected:**

1. **Notification creation**: Same underlying API, just wrapped
2. **Async operations**: Same `CompletableFuture` usage, just centralized
3. **Result handling**: Same logic, just extracted to utility

**Potential Improvements:**

1. **Reduced code size**: Smaller bytecode, faster class loading
2. **Better JIT optimization**: Centralized code paths easier to optimize
3. **Reduced memory**: Fewer duplicate lambda allocations

---

## Security Considerations

**No Security Impact:**

This refactoring is purely internal code reorganization. No changes to:
- Git command execution
- File system access
- Network operations
- User permissions
- Data validation

---

## Rollback Strategy

**Per-file rollback:**
```bash
git revert <commit-hash>
```

**Full rollback:**
```bash
git reset --hard <commit-before-refactoring>
```

**Partial rollback:**
- Keep utility classes
- Revert problematic migrations
- Utilities remain available for future use

---

## Future Enhancements (Out of Scope)

**Phase 2 Items (Deferred):**
- `BaseWorktreeAction` abstract class (eliminate action boilerplate)
- `WorktreeFilters` utility (centralize filtering logic)
- `BaseWorktreeSelectionDialog` abstract class (reduce dialog boilerplate)

**Phase 3 Items (Deferred):**
- Extract common test utilities
- Type-safe dialog result types
- Additional notification features (actions, expiration, sticky notifications)

