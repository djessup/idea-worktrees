# Technology Stack

## Core Technologies

- **Language**: Kotlin
- **Build System**: Gradle with IntelliJ Platform Gradle Plugin 2.7.1
- **Target Platform**: IntelliJ IDEA 2025.1.4.1 (Community Edition)
- **Java Version**: 21
- **Kotlin Version**: 2.1.0

## Key Dependencies

- **IntelliJ Platform SDK**: Core platform APIs
- **Git4Idea**: IntelliJ's Git plugin for VCS integration
- **JUnit 4.13.2**: Testing framework
- **Kotlinx Kover 0.8.1**: Code coverage

## Build Commands

```bash
# Build the plugin
./gradlew build

# Run tests
./gradlew test

# Run plugin in development IDE
./gradlew runIde

# Build plugin distribution
./gradlew buildPlugin

# Generate code coverage report
./gradlew koverHtmlReport
```

## Configuration Notes

- Uses Gradle Configuration Cache and Build Cache for performance
- Kotlin stdlib bundling is disabled (opt-out flag set)
- Minimum build version: 251 (IntelliJ 2025.1)
- Plugin distributed as ZIP from `build/distributions/`

## Git Integration

- Uses `GeneralCommandLine` and `ExecUtil` for Git command execution
- Integrates with IntelliJ's VCS repository management
- Requires Git to be available in system PATH