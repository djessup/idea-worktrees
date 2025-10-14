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

Modifications to this file must be made below this line. Do not modify or remove this marker, or anything above it.
----------------------------

It is recommended that you first review the documentation extensively and replace this sentence with a summary of your 
understanding of the plugin development process, to accelerate future work.

You should update this AGENTS.md file regularly to record important decisions and learnings that support your short-term
priorities. Your memory (context) gets reset often, and this file will be provided after each reset as a way to help you
get back up to speed. Use it to help yourself be the best plugin-writing agent you can be!

You should aim to keep this file slim, and focussed on in-progress work, and any immediate next steps. Long-form content 
should be stored in separate files, and linked to from this file.
