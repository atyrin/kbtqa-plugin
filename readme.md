# Features

## Context menu action

Action is available under the _QA Helpers_ context menu. 
The options depend on the file where the context menu is opened.

For `gradle.properties`:
* a list with well-known properties.

For `pom.xml`:
* _Configure Repositories_ will declare the Kotlin `dev`, `bootstrap` and `experimental` repositories in both `<repositories>` and `<pluginRepositories>`, creating either section when it is missing. Existing sections are merged into, not replaced: a repository whose URL is already declared is left alone (trailing slashes do not count as a difference), so running the action twice changes nothing.
* _Add Maven Property_ will suggest Kotlin and Maven compiler options in titled groups that follow the [Kotlin docs](https://kotlinlang.org/docs/maven-kotlin-compiler.html):
  * _Kotlin compiler_ — `kotlin.compiler.languageVersion`, `apiVersion`, `jvmTarget`, `jdkRelease`, `jdkHome`, `javaParameters=true`.
  * _Kotlin build_ — how Maven runs the compiler: `kotlin.compiler.daemon=false` (in-process instead of the default daemon), `daemon.jvmArgs`, `daemon.shutdownDelayMs`, `incremental=true`, and `kotlin.smart.defaults.enabled=false`, which turns smart defaults off while `<extensions>` stays on. `daemon.jvmArgs` is filled in as `Xmx3g,Xms1g`: comma-separated and without leading dashes, since the plugin passes the arguments on as they are and `-Xmx3g` would reach the daemon's JVM as `--Xmx3g`, which it rejects.
  * _Maven compiler_ — `maven.compiler.release`, `maven.compiler.target`, which smart defaults derive the Kotlin JVM target from.
  * _kotlin-maven-plugin `<configuration>`_ — options without a property counterpart: `nowarn`, `jdkToolchain`, and the compiler arguments the Kotlin docs use most that the plugin has no parameter for: `-Werror`, `-Wextra`, `-progressive`, `-opt-in` (with `kotlin.ExperimentalStdlibApi`, a stdlib marker that always resolves, for you to replace with the one you need) and `-Xjsr305=strict`.

  The popup lists the names only. Properties go into the `<properties>` section enclosing the caret—so a `<profile>` gets its own—otherwise into the one in the root `<project>` tag, which is created when missing. The last group goes into `kotlin-maven-plugin`; when the pom does not declare it yet, a hint points to _Configure Kotlin Plugin_. Nothing is duplicated: for an option that is already set, the caret simply jumps to the existing entry.

* _Configure Kotlin Plugin_ sets up `kotlin-maven-plugin` in one of the three ways the [Kotlin docs](https://kotlinlang.org/docs/maven-configure-project.html) describe, and switches an existing project between them:
  * _Smart defaults_ — `<extensions>true</extensions>`; the plugin then sets up source roots, `kotlin-stdlib` and executions itself.
  * _Manual: Kotlin only_ — `<sourceDirectory>`/`<testSourceDirectory>` pointing at `src/*/kotlin`, plus `compile` and `test-compile` executions.
  * _Manual: Kotlin + Java_ — Kotlin executions that also see the Java sources, and `maven-compiler-plugin` with its default executions disabled, so that Kotlin compiles first. A missing compiler plugin is declared at its latest stable Maven 3 release, looked up on Maven Central.

  A mode rewrites only what defines a mode: `<extensions>`, the Kotlin `compile`/`test-compile` executions, the compiler executions of the Kotlin + Java recipe, and Kotlin source directories. Everything else — kapt executions, plugin configuration, versions, other plugins — is kept, and settings of replaced executions such as compiler `args` move to the plugin's `<configuration>`. Versions already in the pom are kept. A missing plugin is added with `${kotlin.version}`; if the pom does not declare that property, an empty `<kotlin.version>` is added with the caret in it for you to fill in — except in a pom with a `<parent>`, which most likely inherits it. The manual modes add `kotlin-stdlib` when it is missing, at the plugin's version. One undo reverts the whole change.
* _Add Kotlin Compiler Plugin_ enables `spring`, `all-open`, `no-arg`, `lombok`, `kotlinx-serialization`, `sam-with-receiver` or `power-assert` in `kotlin-maven-plugin`: the name in `<compilerPlugins>`, the matching `kotlin-maven-*` dependency of the plugin at the plugin's version, and an example `<pluginOptions>` entry for the compiler plugins that do nothing without one—`all-open`, `no-arg` and `sam-with-receiver` need an annotation, and `power-assert` in Maven transforms no function unless one is listed. Like the last group of _Add Maven Property_, it needs `kotlin-maven-plugin` declared and adds nothing twice.
* _Choose Kotlin version_ (Alt+Enter on the `<kotlin.version>` property) sets it from a popup of `kotlin-maven-plugin` versions, grouped under `dev`, `experimental` and Maven Central titles, newest first in each. Typing filters the list, so `dev-94` finds `2.5.0-dev-9401`, and the version the pom has is preselected. A version Maven Central has is listed there only, since it needs no extra repository; `dev` and `experimental` builds do—_Configure Repositories_ adds them. The versions are fetched on first use and kept for 10 minutes.
* _Configure JDK Toolchain_ pins the compilation JDK with `maven-toolchains-plugin`, as in [Set JDK version](https://kotlinlang.org/docs/maven-configure-project.html#set-jdk-version): the plugin, its `toolchain` execution and `<toolchains><jdk><version>`. Missing parts are added and existing ones kept; a missing plugin is declared at its latest stable Maven 3 release, looked up on Maven Central, and an empty JDK version gets the caret for you to fill in. The JDKs themselves have to be listed in a `toolchains.xml` — `~/.m2/toolchains.xml`, or a project file passed with `mvn -t toolchains.xml`.

The `pom.xml` actions format only what they insert: the rest of the file keeps its layout, including commented-out lines that Ctrl+/ put at column 0.

For `build.gradle.kts`:
* _Configure maven repositories_ will add a repositories section with popular maven repositories.
* _Add dependency_ will suggest a list of KMP dependencies (GAV coordinates); with the caret inside a `swiftPMDependencies {}` block it suggests SwiftPM snippets (`swiftPackage(...)`, `localSwiftPackage(...)`) instead.
* _Add Compiler Options_ will insert Kotlin compiler options configuration.
* _Create KMP Source Sets_ reads the targets declared in the `kotlin {}` block (`jvm()`, `js {}`, `iosArm64()`, `android {}`, custom names such as `jvm("desktop")`, calls inside `listOf(...)`; comments are ignored) and creates every platform and shared source set with a class named after it, e.g. `src/iosMain/kotlin/IosMain.kt` containing `class IosMain`:
  * shared source sets follow the Kotlin default hierarchy template (`commonMain` → `webMain`/`nativeMain` → `appleMain`/`linuxMain`/… → `iosMain`/…), a group is created only when a selected target belongs to it;
  * a dialog lets you adjust the targets (detected ones are pre-selected, the rest can be added manually), include test source sets, include custom source sets created in `sourceSets {}`, and set a package;
  * existing classes are never overwritten; reload the Gradle project afterwards if the new directories are not marked as source roots.
* _Add Publishing_ will add maven-publish plugin and publishing configuration.

For `settings.gradle.kts`:
* _Configure build scan_ will set up Gradle build cache—add plugin and a simple configuration.
* _Configure build cache_ will add a simple build cache configuration.

For `gradle` directory in the file tree:
* _Configure version catalog_ will create a file `libs.versions.toml` with a sample catalog.

For the project root directory:
* Delete `.gradle`, `.kotlin`, `.idea`, `.git` and `build` directories. Also `local.properties` file. The full will be shown in the popup. 
* Create a zip archive of the project (_Prepare Upload_). Cache folders, build output—`build` next to a Gradle build script, `target` next to a `pom.xml`—and items matched by the project's `.gitignore` rules (root and nested files) are excluded by default:
  * an ignored directory is shown as a single entry in the exclusion dialog — its contents are not enumerated;
  * ignored files are grouped per pattern (e.g. `*.log — 14 files`);
  * every exclusion can be overridden by unchecking it in the dialog.
  
  A built-in `.gitignore` parser is always active; when the Git plugin is enabled, the IDE's VCS ignore state (including global gitignore rules) is consulted as well.

## General actions

### Shows the latest tooling versions
An action available in the `Tools` menu. It will show all available versions from maven repositories for different tools.
KGP from stable/dev and experimental channels. AGP from google repo. KSP and Dokka from maven central. Gradle versions from GitHub releases.
Maven shows three channels: the Apache Maven distribution and `maven-compiler-plugin` from maven central, and the Maven Daemon (`mvnd`) from GitHub releases.
`kotlin-maven-plugin` is not listed separately—it shares its version numbers with KGP, which is already in the Kotlin tab.

### Skills Setup Wizard
An action available in the `Tools` menu. It opens a dialog that lets you browse AI agent skill repositories, select skills, and install them into your project. Works with any git repository (GitHub, GitLab, internal repos, etc.).

* **Repositories panel** (left): shows configured skill repositories. Use `+`/`−` buttons to add or remove repositories. Each repository specifies a git URL and the path to the skills directory within the repo.
* **Skills panel** (center): displays available skills as checkboxes, fetched from the selected repository via git clone.
* **Target directory** (bottom): specify where skills are installed (default: `.junie/skills`). Change to `.claude/skills` or any other path as needed.
* Click **Install** to clone the repository and copy the selected skill folders into your project.
* Default repository: [Kotlin/kotlin-agent-skills](https://github.com/Kotlin/kotlin-agent-skills) (skills path: `skills`).


# Installation and Updates

1. Add `https://raw.githubusercontent.com/atyrin/kbtqa-plugin/refs/heads/main/repository/updatePlugins.xml` as a plugin repository.
2. In the Plugins → Marketplace you will see the plugin in the end of the list.
3. It will also automatically get the plugin updates.