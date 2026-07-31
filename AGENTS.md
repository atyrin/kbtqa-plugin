# KBT QA Swiss Knife Plugin — Agent Guidelines

## Project Overview

**KBT QA Swiss Knife** (plugin id `kbtqa`, display name "KBT QA Swiss Army Knife") is an IntelliJ IDEA
plugin by Andrey Tyrin providing quality-of-life features for developers working with Kotlin and Gradle
projects. Four feature areas:

1. **Editor helpers** — the _QA Helpers_ context-menu group on Gradle files: repositories, dependencies,
   compiler options, publishing, build scan, build cache, `gradle.properties` entries, version catalog.
2. **Project-view actions** — exclude/delete cache directories, and _Prepare Upload_ (zip a project as a
   reproducer, honouring `.gitignore`).
3. **Tool versions** — `Tools ▸ Show Tool Versions`, fetching the latest KGP, AGP, KSP, Dokka and Gradle
   versions from their respective repositories.
4. **Skills Setup Wizard** — `Tools ▸ Skills Setup Wizard`, clones a git repository of AI agent skills and
   installs selected ones into the project.

`readme.md` is the detailed user-facing feature reference; keep this file short and point there.

## Project Structure

```
kbtqa-plugin/
├── build.gradle.kts                       # Build config; also inlines description.html and changeNotes
├── settings.gradle.kts
├── gradle.properties                      # pluginSinceBuild, fallback version, Gradle flags
├── src/main/kotlin/kbtqa/
│   └── helpers/
│       ├── editor/                        # QA Helpers context-menu actions on Gradle files
│       ├── projectview/                   # Project-tree actions: exclude dirs, Prepare Upload
│       ├── skills/                        # Skills Setup Wizard (git clone + install)
│       └── versions/                      # Tool versions viewer and its per-tool services
├── src/main/resources/
│   ├── META-INF/plugin.xml                # Plugin descriptor
│   ├── META-INF/kbtqa-withGit.xml         # Optional descriptor, loaded only when Git4Idea is present
│   └── description.html                   # Marketplace description, inlined by build.gradle.kts
├── src/test/kotlin/kbtqa/                 # JUnit tests (IntelliJ Platform test framework)
├── .github/workflows/                     # gradle.yml (CI), release.yml (tag-driven release)
├── repository/updatePlugins.xml           # Custom plugin repo descriptor — generated, do not hand-edit
└── gradle/wrapper/
```

## Technology Stack

Version numbers are deliberately not duplicated here — read them from the source of truth:
`build.gradle.kts` (Kotlin, IntelliJ Platform Gradle Plugin, target IDE, JVM target) and
`gradle.properties` (`pluginSinceBuild`).

- **Language**: Kotlin. **Build system**: Gradle Kotlin DSL with the IntelliJ Platform Gradle Plugin
- **Target platform**: IntelliJ IDEA Community. `sinceBuild` is read from `pluginSinceBuild` in
  `gradle.properties` — change it there, never hardcode it in `build.gradle.kts`
- **Kotlin API level**: `apiVersion` is pinned *below* the compiler's own version in `build.gradle.kts`
  as a workaround for [KT-79354](https://youtrack.jetbrains.com/issue/KT-79354). **Do not use language
  or stdlib API newer than the pinned level** — it will not compile. Check the pin before reaching for
  recent stdlib additions, and remove it only together with the workaround comment.
- **JVM target**: set for both `JavaCompile` and `KotlinCompile` in `build.gradle.kts`; the CI and release
  workflows pin the matching JDK
- **Plugin dependencies**: `org.jetbrains.kotlin` (required), `Git4Idea` (optional)
- **Kotlin plugin mode**: K2 supported (`supportsKotlinPluginMode supportsK2="true"`)

## Build and Test Instructions

```bash
./gradlew build          # Compile + tests; the default check before submitting changes
./gradlew test           # Headless JUnit tests only — the fast feedback loop
./gradlew runIde         # Launch a sandbox IDE with the plugin, for manual verification
./gradlew buildPlugin    # Produce build/distributions/kbtqa-<version>.zip
```

Prefer `./gradlew test` while iterating; `runIde` is for manual UI verification and cannot be scripted.
CI (`.github/workflows/gradle.yml`) runs `./gradlew buildPlugin test` on every push and PR to `main`.

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and single-purpose

### Testing Strategy
- Add headless tests under `src/test/kotlin/` for pure logic (parsing, filtering, version resolution);
  see `src/test/kotlin/kbtqa/helpers/projectview/GitignoreFileFilterTest.kt` for the existing pattern
- Verify UI-bound behaviour in a development IDE instance using `./gradlew runIde`
- Verify actions work correctly with the relevant Gradle file types (`build.gradle.kts`,
  `settings.gradle.kts`, `gradle.properties`) and in the correct context menus

### Plugin Development Best Practices
- Actions should implement `DumbAware` when possible for better IDE performance
- Use `ActionUpdateThread.BGT` for background thread updates
- Properly handle file type detection and context validation
- Code that touches Git4Idea belongs behind the optional descriptor
  `src/main/resources/META-INF/kbtqa-withGit.xml`; the plugin must degrade gracefully when the Git plugin
  is disabled
- Follow IntelliJ Platform plugin development guidelines

### File Modification Guidelines
- QA Helper actions should only be enabled for relevant Gradle files
- Maintain backward compatibility with existing functionality
- Test actions in both editor context menus and dedicated action groups

### Releases
Releases are tag-driven: pushing an `X.Y.Z` tag runs `.github/workflows/release.yml`, which builds at
that version, creates a GitHub Release with the zip, and regenerates `repository/updatePlugins.xml` on
`main`. Do not hand-edit `repository/updatePlugins.xml` and do not bump `version=` in `gradle.properties`
— that value is only a local fallback, overridden by `-Pversion=` from the tag.

## Agent Instructions

### Build Requirements
- **Build the project** using `./gradlew build` before submitting changes
- Ensure the plugin builds successfully and all dependencies are resolved

### Testing Requirements
- **Run `./gradlew test`** for anything with testable logic
- **Test in a development IDE** using `./gradlew runIde` to verify plugin functionality
- **Verify action availability** in appropriate contexts (editor menus, project view, console menus, etc.)

### Code Quality
- Follow existing code patterns and structure
- Maintain the separation between the `helpers/{editor,projectview,skills,versions}` packages
- Ensure proper error handling and user feedback
- Test edge cases, especially file type detection and context validation

### Documentation
- After adding a new feature, make sure documentation is updated accordingly
- Documentation lives in `readme.md` and `src/main/resources/description.html` (the latter is read at
  build time by `build.gradle.kts` and becomes the marketplace description)
- User-visible changes usually also warrant a `changeNotes` update in `build.gradle.kts`
