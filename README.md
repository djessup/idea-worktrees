# Git Worktree Manager

An IntelliJ IDEA plugin that provides ergonomic support for working with multiple Git worktrees in a single project.

## Features

### Status Bar Widget
- Displays the current worktree name in the status bar
- Click to see a popup menu with all available worktrees
- Quick access to worktree operations

### Worktree Operations
- **Create New Worktree**: Create a new worktree with a custom branch
- **Switch Worktree**: Switch to a different worktree (opens in new window)
- **Delete Worktree**: Safely delete a worktree
- **Show All Worktrees**: View information about all worktrees in the repository

## What are Git Worktrees?

Git worktrees allow you to check out multiple branches simultaneously in different directories. This is useful when you need to:
- Work on multiple features or bug fixes without switching branches
- Review code in one branch while working on another
- Run tests on one branch while developing on another
- Maintain multiple versions of your codebase simultaneously

## Usage

### Creating a Worktree
1. Go to **VCS > Git Worktrees > Create New Worktree...**
2. Enter the branch name for the new worktree
3. Enter the directory name for the worktree
4. The plugin will create the worktree in a sibling directory to your current project

### Switching Worktrees
1. Click on the worktree name in the status bar, or
2. Go to **VCS > Git Worktrees > Switch Worktree...**
3. Select the worktree you want to switch to
4. The plugin will open the worktree in a new window

### Deleting a Worktree
1. Go to **VCS > Git Worktrees > Delete Worktree...**
2. Select the worktree you want to delete
3. Confirm the deletion
4. The plugin will remove the worktree directory and clean up Git metadata

### Viewing All Worktrees
1. Go to **VCS > Git Worktrees > Show All Worktrees**
2. A dialog will show all worktrees with their paths, branches, and status

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
- Use IntelliJ VCS APIs instead of `GeneralCommandLine` for Git operations
- Use folder browser for worktree path input, default to "../[project name]-[branch name]"
- Detect failures and show proper error messages instead of messages like: "Worktree created successfully.", when it was not created successfully.
- Improve manage worktrees to list worktrees in a table/list view with buttons to add/remove/compare/etc.
- Add support for comparing and merging worktrees using VCS compare view
- Add keyboard shortcuts for common actions

## Contributing

Contributions are welcome! Please feel free to submit issues or pull requests.

## License

[Add your license here]

## Credits

Developed by Adobe

