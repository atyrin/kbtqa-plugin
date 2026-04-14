# Features

## Context menu action

Action is available under the _QA Helpers_ context menu. 
The options depend on the file where the context menu is opened.

For `gradle.properties`:
* a list with well-known properties.

For `build.gradle.kts`:
* _Configure maven repositories_ will add a repositories section with popular maven repositories.
* _Add dependency_ will suggest a list of KMP dependencies (GAV coordinates)
* _Add Compiler Options_ will insert Kotlin compiler options configuration.
* _Add Publishing_ will add maven-publish plugin and publishing configuration.

For `settings.gradle.kts`:
* _Configure build scan_ will set up Gradle build cache—add plugin and a simple configuration.
* _Configure build cache_ will add a simple build cache configuration.

For `gradle` directory in the file tree:
* _Configure version catalog_ will create a file `libs.versions.toml` with a sample catalog.

For the project root directory:
* Delete `.gradle`, `.kotlin`, `.idea`, `.git` and `build` directories. Also `local.properties` file. The full will be shown in the popup. 
* Create a zip archive of the project.

## General actions

### Shows the latest tooling versions
An action available in the `Tools` menu. It will show all available versions from maven repositories for different tools.
KGP from stable/dev and experimental channels. AGP from google repo. KSP and Dokka from maven central. Gradle versions from GitHub releases.

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