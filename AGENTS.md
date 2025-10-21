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

## Quick Project Facts
- **Target IDEs:** IntelliJ IDEA & RustRover 2025.2.2 running on Java 21.
- **Primary language:** Kotlin (IntelliJ Platform SDK conventions apply).
- **Key docs:** `docs/IMPLEMENTATION_PLAN.md`, `docs/TESTING_GUIDELINES.md`, `docs/BUG_REPORTS.md`.
- **Coverage target:** ≥80% line coverage for `au.id.deejay.ideaworktrees.services` and `au.id.deejay.ideaworktrees.model` via Kover.

## Build & Test Checklist
1. `./gradlew test --console=plain`
2. `./gradlew koverXmlReport --console=plain` (verifies coverage threshold)
3. `./gradlew runIde` for manual validation when available

> **Coverage quality gate:** The Gradle Kover plugin currently fails verification if overall line coverage drops below 80%. Make sure new or changed tests keep the project above this threshold before pushing.

Need deeper guidance on testing workflows? 

Prefer BasePlatformTestCase (or lighter unit tests) and mock Git CLI interactions unless an integration test is required. Keep tests deterministic and independent.

## Development Guardrails
- **Threading:** Never block the EDT. Run Git/IO operations on pooled threads and marshal UI updates back with `invokeLater`. Avoid blocking inside `ReadAction`.
- **Git Integration:** Use Git4Idea APIs and command-line helpers. Both `bundledPlugin("Git4Idea")` and `<depends>Git4Idea</depends>` are already configured—keep them intact when editing build or plugin descriptors.
- **Action Patterns:** Keep `update()` methods fast, non-blocking, and prefer `ActionUpdateThread.BGT`. Cache expensive state in services.
- **Status Bar Widgets:** Store data in thread-safe caches (e.g., `AtomicReference`) and refresh from background tasks.
- **Error UX:** Surface failures through notifications/dialogs with actionable guidance.

## Architecture Snapshot
1. `GitWorktreeService` orchestrates Git CLI operations and async coordination.
2. Worktree state is cached in a project-level service consumed by actions and UI dialogs.
3. Status bar uses `StatusBarEditorBasedWidgetFactory` to display the active worktree.
4. Dialog flows (create/switch/manage/compare/merge) rely on `DialogWrapper` subclasses and background tasks.
5. Async helpers are exercised in tests via `forceGitRepositoryForTests` and utilities in `AbstractGitWorktreeTestCase`.

## Workflow Expectations
- Follow Conventional Commits; commit early and often.
- Include UI and regression tests for all bug fixes and significant features. See [docs/TESTING_GUIDELINES.md](docs/TESTING_GUIDELINES.md).
- Update this file when architecture or process guidance changes.
- Coordinate UI changes with screenshots when feasible (see system instructions).
- Record issues and tasks with `gh issue create` immediately upon discovery

**Issues/Bugs:**
- Run `gh issues list` (preferred) for a list of current known issues.
- Report newly discovered issues via `gh issue create`.
- Endeavour to address issues at the earliest opportunity.
- Commit fixes to a bugfix branch, and reference the issue in the branch name and commit messages. Open a PR when the branch is ready for review.


## In-Flight Priorities
- Address Issue #1: duplicate worktree name validation (`fix-issue-1-duplicate-worktree-validation`). Ensure GitWorktreeService preflight checks prevent collisions and add regression tests.
- Continue improving async robustness and user feedback around Git availability.

## Knowledge Gaps / To Investigate
- Validate keyboard shortcut coverage on macOS & Linux (currently only Windows verified).
- Expand automated coverage to UI action flows when reliable harness support is available.

## Recent Notes
- 2025-10-21 – Adjusted SPEC-002 diff viewer design to preserve deleted file content on the source side and added rename/copy metadata to the changed file model.

_End of file._
