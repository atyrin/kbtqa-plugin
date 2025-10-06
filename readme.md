# Features

## Context menu action

Action available under _QA Helpers_ menu. 
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

## General actions

### Shows the latest tooling versions
An action available in the `Tools` menu. It will show all available versions from maven repositories for different tools. 
KGP from stable/dev and experimental channels. AGP from google repo. KSP and Dokka from maven central.


# Updates

Add `https://raw.githubusercontent.com/atyrin/kbtqa-plugin/refs/heads/main/repository/updatePlugins.xml` as a plugin repository.