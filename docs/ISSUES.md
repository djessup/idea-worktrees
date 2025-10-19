## Known Issues
- [ ] Creating new worktree with the same name as an existing one is not detected or handled gracefully
- [ ] Creating new worktree from another worktree formats default folder name as `[current-worktree-name]-[new-worktree-name]` instead of `[project name]-[new-worktree-name]`
- [ ] Rename allows user to attempt to rename main worktree, then errors with "Failed to move worktree. Details: fatal: '[worktree-path]' is a main working tree"
- [ ] Compare does not attempt to handle or warn about uncommitted changes
- [ ] Compare shows a text-based diff output from CLI instead of using IntelliJ's VCS compare viewWo
- [ ] Manage Worktrees dialog shows an empty table when the dialog opens, until "Refresh" button is pressed. Started after GitWorktreeService.kt async work was completed.
