# Project Structure

## Package Organization

The project follows standard IntelliJ plugin structure with domain-driven organization:

```
src/main/kotlin/au/id/deejay/ideaworktrees/
├── actions/          # User-facing actions and menu items
├── model/           # Data models and domain objects
├── services/        # Core business logic and Git operations
├── ui/              # User interface components and dialogs
└── utils/           # Utility classes and helper functions
```

## Key Architectural Components

### Services Layer
- **GitWorktreeService**: Project-level service for all Git worktree operations
- **WorktreeChangeListener**: Event notification system for worktree changes

### Model Layer
- **WorktreeInfo**: Core data class representing worktree metadata
- **WorktreeOperationResult**: Result wrapper for async operations

### Actions Layer
- **WorktreeActionGroup**: Main menu group under VCS
- Individual action classes for each operation (Create, Switch, Delete, etc.)
- Keyboard shortcuts follow `Ctrl+Alt+W` + letter pattern

### UI Layer
- **WorktreeStatusBarWidget**: Status bar integration
- **ManageWorktreesDialog**: Table view for worktree management
- **WorktreeComboBoxRenderer**: Custom rendering for worktree selection

## Configuration Files

- **plugin.xml**: Plugin manifest with extensions and actions
- **build.gradle.kts**: Build configuration and dependencies
- **gradle.properties**: Gradle optimization settings

## Testing Structure

Tests mirror the main source structure under `src/test/kotlin/` with comprehensive coverage for:
- Service layer operations
- UI component behavior
- Async operations and error handling
- Git command integration

## Naming Conventions

- Classes use PascalCase
- Package names follow reverse domain notation
- Action IDs use full package path for uniqueness
- Test classes append "Test" suffix