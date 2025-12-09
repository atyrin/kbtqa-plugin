# KBT QA Swiss Knife Plugin - Project Guidelines

## Project Overview

**KBT QA Swiss Knife** is an IntelliJ IDEA plugin developed by Andrey Tyrin that provides various quality-of-life features for developers working with Kotlin and Gradle projects. The plugin offers two main feature sets:

1. **QA Helper Actions**: Tools for configuring Gradle projects including repositories, dependencies, build scans, build cache, and Gradle properties management
2. **Stacktrace Handling**: Enhanced stacktrace copying functionality with line markers and context menu integration

## Project Structure

```
kbtqa-plugin/
├── build.gradle.kts              # Main build configuration
├── settings.gradle.kts           # Gradle settings
├── gradle.properties             # Gradle properties
├── src/main/kotlin/kbtqa/
│   ├── helpers/                  # QA helper actions for Gradle configuration
│   │   ├── QAHelpersActionGroup.kt
│   │   ├── GradlePropertiesAction.kt
│   │   ├── ConfigureRepositoriesAction.kt
│   │   ├── AddDependencyAction.kt
│   │   ├── ConfigureBuildScanAction.kt
│   │   └── ConfigureBuildCacheAction.kt
│   └── stacktraces/              # Stacktrace handling functionality
│       ├── StacktraceCopyAction.kt
│       ├── StacktraceIcons.kt
│       └── StacktraceLineMarkerProvider.kt
├── src/main/resources/
│   ├── META-INF/plugin.xml       # Plugin configuration
│   └── icons/                    # Plugin icons and resources
└── gradle/wrapper/               # Gradle wrapper files
```

## Technology Stack

- **Language**: Kotlin 2.2.20-Beta2
- **JVM Target**: Java 21
- **Build System**: Gradle with IntelliJ Platform Plugin 2.7.0
- **Target Platform**: IntelliJ IDEA Community Edition 2025.1+ (build 251+)
- **Plugin Dependencies**: Kotlin plugin (bundled)

## Build and Test Instructions

### Building the Project
```bash
./gradlew build
```

### Running the Plugin in Development
```bash
./gradlew runIde
```

### Building Plugin Distribution
```bash
./gradlew buildPlugin
```

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and single-purpose

### Testing Strategy
- Test plugin functionality in a development IDE instance using `runIde`
- Verify actions work correctly with different Gradle file types (build.gradle.kts, settings.gradle.kts, gradle.properties)

### Plugin Development Best Practices
- Actions should implement `DumbAware` when possible for better IDE performance
- Use `ActionUpdateThread.BGT` for background thread updates
- Properly handle file type detection and context validation
- Follow IntelliJ Platform plugin development guidelines

### File Modification Guidelines
- QA Helper actions should only be enabled for relevant Gradle files
- Maintain backward compatibility with existing functionality
- Test actions in both editor context menus and dedicated action groups

## Junie Instructions

### Testing Requirements
- **Test in development IDE** using `./gradlew runIde` to verify plugin functionality
- **Verify action availability** in appropriate contexts (editor menus, console menus, etc.)

### Build Requirements
- **Build the project** using `./gradlew build` before submitting changes
- Ensure the plugin builds successfully and all dependencies are resolved

### Code Quality
- Follow existing code patterns and structure
- Maintain separation between helpers and stacktraces packages
- Ensure proper error handling and user feedback
- Test edge cases, especially file type detection and context validation

### Documentation
- After adding a new feature, make sure that documentation is updated accordingly
- documentation: readme.md, src/main/resources/description.html
