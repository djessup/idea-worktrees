# IntelliJ Plugin Testing Guidelines

This document distills testing best practices from the [IntelliJ Platform Plugin SDK](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html) for the Git Worktree plugin project.

## Testing Philosophy

**Model-Level Functional Tests**: The IntelliJ Platform favors testing features as a whole rather than isolated unit tests. Tests run in a headless environment with real production implementations, providing stability and requiring minimal maintenance despite internal refactorings.

**Key Benefits**:
- Tests remain stable through major refactorings
- Work directly with underlying models instead of UI
- Use real components instead of mocks

**Trade-offs**: Slower execution and more difficult debugging compared to isolated unit tests, but dramatically better long-term maintainability.

## Test Types Overview

### Light Tests (Preferred)
- **Use when**: Testing doesn't require full project setup
- **Performance**: Reuses project from previous test run
- **Base classes**: `BasePlatformTestCase`, `LightPlatformTestCase`
- **Example for this plugin**: Testing `GitWorktreeService` methods, status bar widget visibility

```kotlin
class GitWorktreeServiceTest : BasePlatformTestCase() {
    private lateinit var service: GitWorktreeService
    
    override fun setUp() {
        super.setUp()
        service = project.service<GitWorktreeService>()
    }
    
    fun testListWorktrees() {
        val worktrees = service.listWorktrees()
        assertNotNull(worktrees)
    }
}
```

### Heavy Tests
- **Use when**: Multi-module projects or complex project setup required
- **Performance**: Creates new project for each test
- **Base class**: `HeavyPlatformTestCase`
- **Example for this plugin**: Testing worktree operations across multiple Git repositories

[Learn more about Light and Heavy Tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html)

## Test Framework Setup

### Dependencies
All test framework dependencies must be declared explicitly in `build.gradle.kts`:

```kotlin
dependencies {
    testImplementation("junit:junit:4.13.2")
    // For Git functionality testing
    testImplementation(bundledPlugin("Git4Idea"))
}
```

### Test Fixtures
Two approaches available:
1. **Standard base classes**: `BasePlatformTestCase`, `LightPlatformTestCase` (handles setup automatically)
2. **Manual fixtures**: `IdeaTestFixtureFactory` (more control, framework-agnostic)

[Learn more about Tests and Fixtures](https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html)

## Test Data Organization

### Directory Structure
```
src/
  test/
    kotlin/           # Test code
    resources/
      testdata/       # Test input/output files
```

### Test Data Path
Override `getTestDataPath()` to specify testdata location:

```kotlin
override fun getTestDataPath(): String {
    return "src/test/resources/testdata"
}
```

### Using Test Method Names
Reuse code by deriving file paths from test method names:

```kotlin
fun testCreateWorktree() {
    val testName = getTestName(true) // "createWorktree"
    // Use testName to load testdata/createWorktree/input.txt, etc.
}
```

[Learn more about Test Project and Testdata](https://plugins.jetbrains.com/docs/intellij/test-project-and-testdata-directories.html)

## Writing Tests

### Common Test Patterns for Worktree Plugin

#### Testing Git Commands
```kotlin
fun testGitWorktreeList() {
    // Setup: Create test Git repository
    val gitRoot = createTestGitRepo()
    
    // Execute: Run git worktree command
    val worktrees = service.listWorktrees()
    
    // Verify: Check results
    assertTrue(worktrees.isNotEmpty())
    assertEquals(gitRoot.path, worktrees[0].path)
}
```

#### Testing Actions
```kotlin
fun testCreateWorktreeAction() {
    val action = CreateWorktreeAction()
    val event = createTestActionEvent()
    
    // Test action availability
    action.update(event)
    assertTrue(event.presentation.isEnabled)
    
    // Test action execution
    action.actionPerformed(event)
    // Verify results...
}
```

#### Testing Status Bar Widget
```kotlin
fun testStatusBarWidgetVisibility() {
    val factory = GitWorktreeStatusBarWidgetFactory()
    
    // Widget should be available for Git projects
    assertTrue(factory.isAvailable(project))
    
    // Widget should show current worktree
    val widget = factory.createWidget(project)
    assertNotNull(widget.getWidgetState())
}
```

[Learn more about Writing Tests](https://plugins.jetbrains.com/docs/intellij/writing-tests.html)

## Best Practices & Common Pitfalls

### Always Call super.tearDown() in finally Block
```kotlin
override fun tearDown() {
    try {
        // Test-specific cleanup
    } catch (e: Throwable) {
        addSuppressedException(e)
    } finally {
        super.tearDown() // CRITICAL: Prevents leaks and side effects
    }
}
```

### Avoid OS-Specific Assumptions
- Use `File.separator` instead of hardcoded `/` or `\`
- Don't assume filesystem case-sensitivity
- Don't assume default encoding or line endings

### Handle Asynchronous Operations
```kotlin
// WRONG: May not execute during test
ApplicationManager.getApplication().invokeLater { doSomething() }

// CORRECT: Tied to project lifecycle
ApplicationManager.getApplication().invokeLater(
    { doSomething() }, 
    project.disposed
)
```

### Wait for Background Tasks
```kotlin
// Wait for indexing to complete
IndexingTestUtil.waitUntilIndexesAreReady(project)

// Wait for project activities (2024.2+)
StartupActivityTestUtil.waitForProjectActivitiesToComplete(project)

// Wait for condition with timeout
WaitFor(5000) { condition() }
```

### Dispatch Pending UI Events
```kotlin
PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
```

### Enable Debug Logging
In `build.gradle.kts`:
```kotlin
tasks {
    test {
        systemProperty("idea.log.debug.categories", "com.example.worktree")
    }
}
```

[Learn more in Testing FAQ](https://plugins.jetbrains.com/docs/intellij/testing-faq.html)

## Integration Tests (Advanced)

For full IDE testing with UI interaction, use the Starter framework (JUnit 5 only):

### Setup
```kotlin
dependencies {
    intellijPlatform {
        testFramework(TestFrameworkType.Starter)
    }
}
```

### Basic Integration Test
```kotlin
@Test
fun testPluginInRealIDE() {
    Starter.newContext(
        testName = "worktreeTest",
        TestCase(IdeProductProvider.IC, NoProject)
            .withVersion("2024.3")
    ).apply {
        val pathToPlugin = System.getProperty("path.to.build.plugin")
        PluginConfigurator(this).installPluginFromFolder(File(pathToPlugin))
    }.runIdeWithDriver().useDriverAndCloseIde {
        waitForIndicators(5.minutes)
        // Test plugin functionality in real IDE
    }
}
```

**Note**: Integration tests run in separate processes (test process + IDE process), requiring special debugging considerations.

[Learn more about Integration Tests](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html)

## Worktree Plugin-Specific Testing Strategies

### 1. Service Testing
Test `GitWorktreeService` operations:
- List worktrees
- Create worktree
- Delete worktree
- Switch worktree
- Get current worktree

### 2. Action Testing
Test each action's `update()` and `actionPerformed()`:
- CreateWorktreeAction
- SwitchWorktreeAction
- DeleteWorktreeAction
- ManageWorktreesAction

### 3. Status Bar Widget Testing
- Widget factory availability
- Widget state updates
- Widget click actions

### 4. Git Integration Testing
- Mock Git repositories for testing
- Test error handling for Git command failures
- Test edge cases (no commits, detached HEAD, etc.)

### 5. Threading Testing
Verify all Git operations run off EDT:
- Use `ApplicationManager.getApplication().executeOnPooledThread()`
- Verify no blocking in `ReadAction`
- Test background operation completion

## Useful Testing Utilities

- `UsefulTestCase` - Base test utilities
- `PlatformTestUtil` - Platform-specific helpers
- `CodeInsightTestUtil` - Code insight testing
- `VfsTestUtil` - Virtual file system utilities
- `IndexingTestUtil` - Indexing-related utilities
- `WaitFor` - Timeout-based waiting

## Resources

- [Testing Overview](https://plugins.jetbrains.com/docs/intellij/testing-plugins.html)
- [Tests and Fixtures](https://plugins.jetbrains.com/docs/intellij/tests-and-fixtures.html)
- [Light and Heavy Tests](https://plugins.jetbrains.com/docs/intellij/light-and-heavy-tests.html)
- [Writing Tests](https://plugins.jetbrains.com/docs/intellij/writing-tests.html)
- [Testing FAQ](https://plugins.jetbrains.com/docs/intellij/testing-faq.html)
- [Integration Tests](https://plugins.jetbrains.com/docs/intellij/integration-tests-intro.html)

