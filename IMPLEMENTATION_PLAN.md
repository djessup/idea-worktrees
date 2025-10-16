# Git Worktree Plugin - Implementation Plan

## Overview
This document outlines the implementation plan for the IntelliJ Git Worktree plugin.

## Architecture

### Core Components

1. **GitWorktreeService** (Project-level service)
   - Manages Git worktree operations
   - Provides API for listing, creating, deleting, renaming worktrees
   - Caches worktree information
   - Notifies listeners of worktree changes

2. **WorktreeStatusBarWidget** (Status bar widget)
   - Displays current worktree name
   - Shows popup menu on click with worktree operations
   - Updates when worktree changes

3. **Action Group** (Worktree operations)
   - CreateWorktreeAction
   - SwitchWorktreeAction
   - DeleteWorktreeAction
   - RenameWorktreeAction
   - CompareWorktreeAction
   - MergeWorktreeAction

4. **Data Models**
   - WorktreeInfo: Represents a Git worktree (path, branch, locked status)

### Technology Stack
- Kotlin for implementation
- IntelliJ Platform SDK 2025.1.4.1
- Java 21
- Git CLI via GeneralCommandLine

## Implementation Phases

### Phase 1: Setup and Core Service
- [x] Update AGENTS.md with understanding
- [x] Create data models (WorktreeInfo)
- [x] Implement GitWorktreeService
  - [x] Execute git worktree commands
  - [x] Parse git worktree list output
  - [x] Detect current worktree
  - [x] Create new worktree
- [x] Delete worktree
- [x] Rename worktree (move)
- [x] Register service in plugin.xml
- [x] Write unit tests for service
- [x] Commit: "feat: add GitWorktreeService with core operations"

### Phase 2: Status Bar Widget
- [x] Create WorktreeStatusBarWidgetFactory
- [x] Implement WorktreeStatusBarWidget
  - [x] Display current worktree name
  - [x] Handle click to show popup
  - [x] Update on worktree changes
- [x] Register widget factory in plugin.xml
- [x] Write tests for widget
- [x] Commit: "feat: add status bar widget for current worktree"

### Phase 3: Actions
- [x] Create WorktreeActionGroup
- [x] Implement CreateWorktreeAction
  - [x] Show dialog to input worktree name and branch
  - [x] Create worktree via service
  - [x] Optionally switch to new worktree
- [x] Implement SwitchWorktreeAction
  - [x] Show list of available worktrees
  - [x] Switch project to selected worktree
- [x] Implement DeleteWorktreeAction
  - [x] Show confirmation dialog
  - [x] Delete worktree via service
- [ ] Implement RenameWorktreeAction
  - [ ] Show dialog to input new name
  - [ ] Rename worktree via service
- [ ] Implement CompareWorktreeAction
  - [ ] Show diff between worktrees
- [ ] Implement MergeWorktreeAction
  - [ ] Merge worktree into current or another worktree
- [x] Register actions in plugin.xml
- [ ] Write tests for actions
- [x] Commit: "feat: add worktree management actions"

### Phase 4: Testing
- [ ] Write integration tests
- [ ] Test with real Git repositories
- [ ] Test edge cases (no git repo, no worktrees, etc.)
- [ ] Manual testing in IDE
- [ ] Commit: "test: add comprehensive tests for worktree plugin"

### Phase 5: Documentation and Polish
- [ ] Update plugin.xml with proper metadata
  - [ ] Plugin name and description
  - [ ] Vendor information
  - [ ] Version compatibility
- [ ] Add icons for actions
- [ ] Add keyboard shortcuts
- [ ] Create README.md
- [ ] Commit: "docs: add plugin metadata and documentation"

## Git Worktree Commands Reference

```bash
# List all worktrees
git worktree list --porcelain

# Add new worktree
git worktree add <path> <branch>

# Remove worktree
git worktree remove <path>

# Move/rename worktree
git worktree move <old-path> <new-path>

# Prune stale worktree information
git worktree prune
```

## Testing Strategy

1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test interaction between components
3. **Manual Tests**: Test in real IDE with actual Git repositories

## Dependencies

- Git must be installed and available in PATH
- Project must be a Git repository
- IntelliJ Platform SDK dependencies already configured in build.gradle.kts

## Notes

- Worktree switching will require reopening the project in the new worktree location
- Need to handle cases where Git is not available
- Need to handle non-Git projects gracefully
- Consider performance implications of frequent git commands
