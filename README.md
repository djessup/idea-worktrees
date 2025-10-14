# Git Worktree Manager

An IntelliJ IDEA plugin that provides ergonomic support for working with multiple Git worktrees in a single project.

## Features

### Status Bar Widget
- Displays the current worktree name in the status bar
- Click to see a popup menu with all available worktrees
- Quick access to worktree operations

### Worktree Operations
- **Create New Worktree**: Create a new worktree with a custom branch
  - Visual folder browser for selecting worktree location
  - Auto-suggests path as `../[project-name]-[branch-name]`
  - Input validation for branch name and path
- **Switch Worktree**: Switch to a different worktree (opens in new window)
- **Delete Worktree**: Safely delete a worktree with confirmation
- **Manage Worktrees**: View all worktrees in a table with detailed information
  - Table view showing name, branch, path, commit, and status
  - Open, delete, and refresh operations
  - Visual indication of current worktree

### Keyboard Shortcuts
- `Ctrl+Alt+W, C` - Create New Worktree
- `Ctrl+Alt+W, S` - Switch Worktree
- `Ctrl+Alt+W, D` - Delete Worktree
- `Ctrl+Alt+W, M` - Manage Worktrees

### Error Handling
- Detailed error messages from Git operations
- Clear success/failure feedback
- Handles edge cases like repositories with no commits
- Automatically offers to create an initial commit (with user consent) when needed for new worktrees

## What are Git Worktrees?

Git worktrees allow you to check out multiple branches simultaneously in different directories. This is useful when you need to:
- Work on multiple features or bug fixes without switching branches
- Review code in one branch while working on another
- Run tests on one branch while developing on another
- Maintain multiple versions of your codebase simultaneously

## Usage

### Creating a Worktree
1. Go to **VCS > Git Worktrees > Create New Worktree...** or press `Ctrl+Alt+W, C`
2. Enter the branch name for the new worktree
3. Choose the worktree path using the folder browser (defaults to `../[project-name]-[branch-name]`)
4. Click OK to create the worktree
5. Optionally open the new worktree in a new window

### Switching Worktrees
1. Click on the worktree name in the status bar, or
2. Go to **VCS > Git Worktrees > Switch Worktree...** or press `Ctrl+Alt+W, S`
3. Select the worktree you want to switch to
4. The plugin will open the worktree in a new window

### Deleting a Worktree
1. Go to **VCS > Git Worktrees > Delete Worktree...** or press `Ctrl+Alt+W, D`
2. Select the worktree you want to delete
3. Confirm the deletion
4. The plugin will remove the worktree directory and clean up Git metadata

### Managing Worktrees
1. Go to **VCS > Git Worktrees > Show All Worktrees** or press `Ctrl+Alt+W, M`
2. A table will show all worktrees with detailed information:
   - Name (with asterisk marking current worktree)
   - Branch
   - Path
   - Commit hash
   - Status (MAIN, LOCKED, PRUNABLE)
3. Use the buttons to:
   - **Open**: Open the selected worktree in a new window
   - **Delete**: Delete the selected worktree
   - **Refresh**: Reload the worktree list

## Requirements

- IntelliJ IDEA 2025.1.4.1 or later (or compatible JetBrains IDE)
- Git must be installed and available in your system PATH
- Your project must be a Git repository

## Installation

### From Source
1. Clone this repository
2. Run `./gradlew buildPlugin`
3. Install the plugin from disk in IntelliJ IDEA:
   - Go to **Settings > Plugins > ⚙️ > Install Plugin from Disk...**
   - Select the generated ZIP file from `build/distributions/`

## Development

### Building
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Running the Plugin in a Development IDE
```bash
./gradlew runIde
```

## Technical Details

- **Language**: Kotlin
- **Build System**: Gradle with IntelliJ Platform Gradle Plugin
- **Target Platform**: IntelliJ Platform 2025.1.4.1
- **Java Version**: 21

## Architecture

- **GitWorktreeService**: Core service for Git worktree operations
- **WorktreeStatusBarWidget**: Status bar widget for displaying current worktree
- **Actions**: Individual actions for worktree operations (create, switch, delete, manage)

## Roadmap

### Completed
- ✅ Use folder browser for worktree path input, default to "../[project name]-[branch name]"
- ✅ Detect failures and show proper error messages
- ✅ Improve manage worktrees to list worktrees in a table/list view with buttons
- ✅ Add keyboard shortcuts for common actions

### Future Enhancements
- Use IntelliJ VCS APIs instead of `GeneralCommandLine` for Git operations
- Add support for comparing and merging worktrees using VCS compare view
- Add full test coverage
- Add rename worktree functionality
- Add worktree pruning action
- Add support for worktree locking/unlocking

## Known Issues
None currently known. Please report any problems via issues or pull requests.

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

[Add your license here]

## Credits

Developed by Adobe
