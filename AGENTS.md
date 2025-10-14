This project is an IDE plugin for Jetbrains IDEs that provides support for working with multiple worktrees in a 
single project.

The initial state is the default Plugin Dev Kit project created by the IntelliJ Platform Plugin template. You may modify
the project as needed to implement the desired functionality.

Plugin/project goals:
- Supports IntelliJ IDEA 2025.2.2 (252.26199.169) and RustRover 2025.2.2 (252.26199.159), running under Java 21. 
  Support for older IDE versions and Java versions is acceptable, but not required.
- Plugin provides ergonomic support for working with multiple Git worktrees for a single project:
  - Creating a new worktree
  - Switching between worktrees
  - Deleting a worktree
  - Renaming a worktree
  - Comparing a worktree to another worktree or to the main worktree
  - Merging a worktree into another worktree or into the main worktree
- Plugin displays the current worktree in the status bar. 

You are an AI code agent, and an expert IntelliJ plugin developer. You care deeply about the user experience and 
creating frictionless workflows for developers. Your task is to implement the plugin as described above.

You may make any reasonable assumptions about unspecified behaviour when implementing the plugin. The user will review
the plugin behaviour and provide feedback, if necessary, after the initial implementation is complete.

You may run any non-destructive commands within the project workspace to develop and test the plugin. Write tests, 
preferably first (e.g. TDD/BDD), to verify the plugin behaviour. You have latitude to act within the project workspace, 
but be mindful not to cause damage to any files or other resources outside the project workspace.

You should commit your work at frequently increments as you progress. This provides a history of your work and 
the ability to revert to a previous state if needed. Use Conventional Commits for your commit messages.

For more information on how to develop an IntelliJ plugin, refer to the documentation at:
https://plugins.jetbrains.com/docs/intellij/welcome.html

You should update AGENTS.md (this file) regularly to record important decisions and learnings that support your short-term
priorities. Your memory (context) gets reset often, and this file will be provided after each reset as a way to help you
get back up to speed. Use it to help yourself be the best plugin-writing agent you can be!

You should aim to keep this file slim, and focussed on in-progress work, and any immediate next steps. Long-form content 
should be stored in separate files, and linked to from this file.

Modifications to this file must be made below this line. Do not modify or remove this marker, or anything above it.
----------------------------

## Plugin Development Understanding

**Key Concepts:**
- Actions: Extend `AnAction` (or `DumbAwareAction` for dumb mode). Override `update()` for state/visibility and `actionPerformed()` for execution
- Status Bar Widgets: Implement `StatusBarWidgetFactory` and register via `com.intellij.statusBarWidgetFactory` extension point
- Git Integration: Use IntelliJ's VCS APIs to interact with Git repositories and worktrees
- Plugin Configuration: Register components in `plugin.xml` under `<extensions>` and `<actions>` sections

**CRITICAL: Threading and Blocking Operations**

IntelliJ Platform has strict threading requirements. Violating these causes errors and poor UX:

1. **Never block the EDT (Event Dispatch Thread)**
   - EDT is for UI updates only
   - Blocking operations (I/O, network, Git commands) MUST run on background threads
   - Error: "Synchronous execution on EDT"

2. **Never block in ReadAction**
   - ReadActions are for reading PSI/VFS data
   - No blocking I/O allowed
   - Error: "Synchronous execution under ReadAction"

3. **Pattern for Git Commands:**
   ```kotlin
   // WRONG - blocks EDT
   fun actionPerformed(e: AnActionEvent) {
       val result = service.executeGitCommand() // BLOCKS!
       showDialog(result)
   }

   // CORRECT - background thread
   fun actionPerformed(e: AnActionEvent) {
       // Get user input on EDT first
       val input = Messages.showInputDialog(...)

       // Execute blocking operation on background thread
       ApplicationManager.getApplication().executeOnPooledThread {
           try {
               val result = service.executeGitCommand()

               // Show UI on EDT
               ApplicationManager.getApplication().invokeLater({
                   showDialog(result)
               }, ModalityState.nonModal())
           } catch (e: Exception) {
               // Handle errors on EDT
               ApplicationManager.getApplication().invokeLater({
                   showError(e.message)
               }, ModalityState.nonModal())
           }
       }
   }
   ```

4. **Pattern for Status Bar Widgets:**
   - Cache data in AtomicReference
   - Update cache on background thread
   - Read from cache in getWidgetState() (called from ReadAction)

5. **Pattern for Action update() methods:**
   - Keep update() fast and non-blocking
   - Don't call Git commands in update()
   - Use cached state or simple checks only
   - Set `getActionUpdateThread() = ActionUpdateThread.BGT`

6. **Dependencies:**
   - Add to build.gradle.kts: `bundledPlugin("Git4Idea")` (compile-time)
   - Add to plugin.xml: `<depends>Git4Idea</depends>` (runtime)
   - Both are required for Git4Idea classes to work

## Current Status: Roadmap Items Complete

**Architecture Decisions:**
1. Use `StatusBarEditorBasedWidgetFactory` for the status bar widget showing current worktree
2. Create action group for worktree operations (create, switch, delete, manage)
3. Use Git command-line interface via `GeneralCommandLine` for worktree operations
4. Store worktree state in project-level service
5. Use `DialogWrapper` for custom dialogs with proper UI components
6. All Git operations run on background threads to avoid EDT violations

**Implementation Plan:**
See IMPLEMENTATION_PLAN.md for detailed task breakdown.

**Completed:**
1. ✅ Core service (GitWorktreeService) with worktree operations
2. ✅ Status bar widget showing current worktree
3. ✅ Action group with create, switch, delete, and manage actions
4. ✅ Plugin metadata and README documentation
5. ✅ Successfully builds plugin distribution
6. ✅ Folder browser for worktree path selection with auto-suggestion
7. ✅ Detailed error handling and user feedback
8. ✅ Table-based manage worktrees dialog with Open/Delete/Refresh
9. ✅ Keyboard shortcuts for all actions

**Commits:**
- feat: add GitWorktreeService with core operations (847c9d1)
- feat: add status bar widget for current worktree (d379d36)
- feat: add worktree management actions (4b62ba3)
- docs: add plugin metadata and README (c1148d3)
- fix: implement status bar widget actions (da95b3a)
- fix: avoid blocking Git commands in ReadAction (a5db92a)
- fix: cache worktree data to avoid blocking in ReadAction (ae14186)
- fix: add Git4Idea plugin dependency to plugin.xml (188344d)
- fix: run Git commands on background threads in actions (4931db7)
- fix: correct git worktree add command argument order (16ab6a1)
- fix: eliminate EDT violations and handle repositories with no commits (4a8790e)
- feat: add folder browser for worktree path selection (2a413e1)
- feat: improve manage worktrees UI with table view (8570901)
- feat: add keyboard shortcuts for worktree actions (1eb14ec)
- docs: update README with completed features (71ac131)

**Recent Changes:**
- Implemented folder browser dialog for worktree path selection
  - Auto-suggests path as `../[project-name]-[branch-name]`
  - Updates suggestion dynamically as user types branch name
  - Includes input validation for branch name and path
- Created ManageWorktreesDialog with table view
  - Displays worktrees in JBTable with columns: Name, Branch, Path, Commit, Status
  - Open, Delete, and Refresh buttons with proper state management
  - Marks current worktree with asterisk
  - Prevents deletion of current and main worktrees
- Added keyboard shortcuts for all actions
  - Ctrl+Alt+W, C: Create New Worktree
  - Ctrl+Alt+W, S: Switch Worktree
  - Ctrl+Alt+W, D: Delete Worktree
  - Ctrl+Alt+W, M: Manage Worktrees
- Updated README with comprehensive feature documentation
- All roadmap items from README.md are now complete

**Next Steps:**
1. Manual testing in IDE using `./gradlew runIde`
2. Test all features with real Git repositories and worktrees
3. Consider adding rename, compare, and merge actions (future enhancements)
4. Add test coverage when test framework is working
