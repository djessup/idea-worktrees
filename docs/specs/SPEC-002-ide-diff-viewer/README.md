# SPEC-002: IDE Diff Viewer Integration for Worktree Comparison

## Overview

This specification addresses GitHub issue #5: "Use IDE diff viewer for worktree compare"

**Issue Description:** The compare workflow currently displays raw text diff output from the Git CLI, which is hard to read and lacks IDE integrations such as navigation and merge assistance.

**Solution:** Replace the plain-text DiffResultDialog with IntelliJ's native diff viewer (DiffManager API) to provide users with a familiar side-by-side diff experience including syntax highlighting, navigation, and standard IDE diff features.

## Specification Documents

1. **[requirements.md](requirements.md)** - Detailed requirements in EARS format with user stories and acceptance criteria
2. **[design.md](design.md)** - Technical design including architecture, component changes, and API usage
3. **[tasks.md](tasks.md)** - Step-by-step implementation plan with 20 discrete coding tasks

## Key Changes

### Modified Components

1. **GitWorktreeService.compareWorktrees()**
   - Changes return type from `WorktreeOperationResult` to `WorktreeComparisonResult`
   - Returns structured data (list of changed files) instead of raw diff text
   - Parses `git diff --name-status` output to identify modified/added/deleted files

2. **CompareWorktreeAction**
   - Removes `DiffResultDialog` class (plain-text display)
   - Adds logic to create `DiffRequest` objects for each changed file
   - Uses `DiffManager.getInstance().showDiff()` to display results
   - Handles file content retrieval on background threads

### New Data Models

1. **WorktreeComparisonResult** - Sealed class with Success/Failure/NoChanges variants
2. **ChangedFile** - Data class representing a file change with path and status
3. **FileStatus** - Enum for MODIFIED/ADDED/DELETED file states

## Requirements Summary

The spec defines 6 main requirements:

1. Display worktree comparison in IDE diff viewer (side-by-side, navigable)
2. Preserve file-level diff context (file tree, navigation between files)
3. Support IDE diff viewer features (syntax highlighting, keyboard shortcuts)
4. Handle errors gracefully (clear error messages, fallback behavior)
5. Maintain backward compatibility (existing validation, dialog flow)
6. Optimize performance for large diffs (background threads, progress indicators)

## Implementation Approach

The implementation follows a test-driven approach with 20 tasks:

1. **Tasks 1-5:** Create data models and modify GitWorktreeService to return structured data
2. **Tasks 6-9:** Implement DiffRequest creation logic in CompareWorktreeAction
3. **Tasks 10-13:** Integrate DiffManager and remove old DiffResultDialog
4. **Tasks 14-17:** Add comprehensive error handling and performance optimizations
5. **Tasks 18-20:** Final verification, manual testing, and documentation

## Testing Strategy

- **Unit Tests:** Data model creation, Git output parsing, DiffRequest creation
- **Integration Tests:** End-to-end comparison flow, diff viewer invocation
- **Performance Tests:** Large number of changed files (100+)
- **Manual Tests:** UI verification, syntax highlighting, navigation, edge cases

## Success Criteria

The implementation will be considered successful when:

1. Users can compare worktrees and see results in IDE's native diff viewer
2. All changed files are displayed in a navigable list/tree
3. Syntax highlighting and IDE diff features work correctly
4. Performance remains acceptable with many changed files
5. All existing functionality (validation, error handling) continues to work
6. Test coverage meets or exceeds 80% threshold

## Related Issues

- GitHub Issue #5: https://github.com/djessup/idea-worktrees/issues/5

## Next Steps

To begin implementation:

1. Review this specification and provide feedback
2. Once approved, start with Task 1 in tasks.md
3. Follow the tasks sequentially, committing after each completed task
4. Run tests frequently to ensure nothing breaks
5. Perform manual testing after Task 19 before considering the feature complete

