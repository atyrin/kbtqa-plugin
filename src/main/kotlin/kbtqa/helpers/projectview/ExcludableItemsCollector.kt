package kbtqa.helpers.projectview

import kbtqa.helpers.projectview.ExcludeDirectoriesDialog.ExcludableItem
import java.io.File

/**
 * Scans a project directory for the items offered by [ExcludeDirectoriesDialog]:
 * cache and IDE directories, build directories of Gradle modules and target directories of
 * Maven modules, default excluded files,
 * and git-ignored directories and files (the latter grouped by the pattern that matched them).
 *
 * @param projectDir The project root directory
 * @param defaultDirectoryExclusions Directory names offered (and pre-selected) at any depth
 * @param defaultFileExclusions File names offered (and pre-selected) at any depth
 * @param ignoreFilter Filter that decides whether a path is git-ignored
 */
internal class ExcludableItemsCollector(
    private val projectDir: File,
    private val defaultDirectoryExclusions: Set<String>,
    private val defaultFileExclusions: Set<String> = emptySet(),
    private val ignoreFilter: IgnoreFilter = NoopIgnoreFilter
) {

    /**
     * Scans the project directory to find all directories and files that can be excluded.
     * This includes default exclusions, build directories in Gradle modules, and default excluded files.
     *
     * Optimization: Some items are only searched at specific locations:
     * - .idea, .junie directories: only at project root level
     * - local.properties file: only at project root level
     * - build directory: only at the same level as build.gradle or build.gradle.kts
     * - target directory: only at the same level as pom.xml
     */
    fun collect(): List<ExcludableItem> {
        val result = mutableListOf<ExcludableItem>()
        scanTopLevelOnlyItems(result)
        val gitIgnoredFilesByPattern = linkedMapOf<String, MutableList<String>>()
        scanDirectory(projectDir, "", result, gitIgnoredFilesByPattern)
        // Emit one group item per gitignore pattern for matched files
        gitIgnoredFilesByPattern.forEach { (pattern, files) ->
            result.add(ExcludableItem(
                relativePath = pattern,
                isDefaultExclusion = true,
                isFile = true,
                category = ExclusionCategory.GIT_IGNORED,
                displayLabel = "$pattern — ${files.size} ${if (files.size == 1) "file" else "files"}",
                coveredPaths = files.toList()
            ))
        }
        // Sort by category order first, then by relative path within each category
        result.sortWith(compareBy({ it.category }, { it.relativePath }))
        return result
    }

    /**
     * Scans for items that can only exist at the top project level.
     * This includes .idea, .junie directories and local.properties file.
     */
    private fun scanTopLevelOnlyItems(target: MutableList<ExcludableItem>) {
        projectDir.listFiles()?.forEach { file ->
            when {
                file.isDirectory && file.name in TOP_LEVEL_ONLY_DIRECTORIES -> {
                    target.add(ExcludableItem(
                        relativePath = file.name,
                        isDefaultExclusion = true,
                        isFile = false,
                        category = getCategoryForDirectory(file.name)
                    ))
                }
                file.isFile && file.name in TOP_LEVEL_ONLY_FILES -> {
                    target.add(ExcludableItem(
                        relativePath = file.name,
                        isDefaultExclusion = true,
                        isFile = true,
                        category = getCategoryForFile(file.name)
                    ))
                }
            }
        }
    }

    private fun scanDirectory(
        dir: File,
        relativePath: String,
        target: MutableList<ExcludableItem>,
        gitIgnoredFilesByPattern: MutableMap<String, MutableList<String>>
    ) {
        if (!dir.isDirectory) return

        dir.listFiles()?.forEach { file ->
            val childRelativePath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

            if (file.isDirectory) {
                // Skip top-level-only directories (already handled in scanTopLevelOnlyItems)
                if (file.name in TOP_LEVEL_ONLY_DIRECTORIES) {
                    return@forEach
                }

                // Show default exclusion directories that can appear in subdirectories
                when (file.name) {
                    in defaultDirectoryExclusions -> {
                        target.add(ExcludableItem(
                            relativePath = childRelativePath,
                            isDefaultExclusion = true,
                            isFile = false,
                            category = getCategoryForDirectory(file.name)
                        ))
                    }
                    // Show build directories in Gradle module directories, and target ones in Maven modules
                    "build" if isGradleModuleDirectory(dir) -> addBuildOutput(target, childRelativePath)
                    "target" if isMavenModuleDirectory(dir) -> addBuildOutput(target, childRelativePath)
                    else -> {
                        // Gitignored directory: show as a single item, do not enumerate its contents
                        if (ignoreFilter.match(childRelativePath, isDirectory = true) != null) {
                            target.add(ExcludableItem(
                                relativePath = childRelativePath,
                                isDefaultExclusion = true,
                                isFile = false,
                                category = ExclusionCategory.GIT_IGNORED
                            ))
                        } else {
                            // Recursively scan subdirectories to find nested build folders
                            scanDirectory(file, childRelativePath, target, gitIgnoredFilesByPattern)
                        }
                    }
                }
            } else if (file.isFile) {
                // Skip top-level-only files (already handled in scanTopLevelOnlyItems)
                if (file.name in TOP_LEVEL_ONLY_FILES) {
                    return@forEach
                }
                // Check if this file should be excluded by default
                if (file.name in defaultFileExclusions) {
                    target.add(ExcludableItem(
                        relativePath = childRelativePath,
                        isDefaultExclusion = true,
                        isFile = true,
                        category = getCategoryForFile(file.name)
                    ))
                } else {
                    // Gitignored file: accumulate into its pattern group
                    val match = ignoreFilter.match(childRelativePath, isDirectory = false)
                    if (match != null) {
                        gitIgnoredFilesByPattern.getOrPut(match.pattern) { mutableListOf() }.add(childRelativePath)
                    }
                }
            }
        }
    }

    private fun addBuildOutput(target: MutableList<ExcludableItem>, relativePath: String) {
        target.add(ExcludableItem(
            relativePath = relativePath,
            isDefaultExclusion = true,
            isFile = false,
            category = ExclusionCategory.BUILD_OUTPUT
        ))
    }

    /**
     * Determines the category for a directory based on its name.
     */
    private fun getCategoryForDirectory(name: String): ExclusionCategory = when (name) {
        "build" -> ExclusionCategory.BUILD_OUTPUT
        ".gradle" -> ExclusionCategory.GRADLE_CACHE
        ".kotlin" -> ExclusionCategory.KOTLIN_CACHE
        ".idea" -> ExclusionCategory.IDE_SETTINGS
        ".git" -> ExclusionCategory.VERSION_CONTROL
        ".junie" -> ExclusionCategory.AI_ASSISTANT
        else -> ExclusionCategory.OTHER
    }

    /**
     * Determines the category for a file based on its name.
     */
    private fun getCategoryForFile(name: String): ExclusionCategory = when (name) {
        "local.properties" -> ExclusionCategory.CONFIGURATION_FILES
        else -> ExclusionCategory.OTHER
    }

    /**
     * Checks if the given directory is a Gradle module directory
     * (contains build.gradle or build.gradle.kts).
     */
    private fun isGradleModuleDirectory(dir: File): Boolean {
        return dir.listFiles()?.any { it.isFile && it.name in GRADLE_BUILD_SCRIPT_NAMES } == true
    }

    /**
     * Checks if the given directory is a Maven module directory (contains pom.xml).
     */
    private fun isMavenModuleDirectory(dir: File): Boolean = File(dir, MAVEN_BUILD_FILE_NAME).isFile
}

// Directories that should only be searched at the top project level
private val TOP_LEVEL_ONLY_DIRECTORIES = setOf(".idea", ".junie")

// Files that should only be searched at the top project level
private val TOP_LEVEL_ONLY_FILES = setOf("local.properties")

// Build script files that identify a directory as a Gradle module
private val GRADLE_BUILD_SCRIPT_NAMES = setOf("build.gradle", "build.gradle.kts")

// The build file that identifies a directory as a Maven module
private const val MAVEN_BUILD_FILE_NAME = "pom.xml"
