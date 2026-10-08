package kbtqa.helpers.projectview

import kbtqa.helpers.projectview.ExcludeDirectoriesDialog.ExcludableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for [ExcludableItemsCollector], which lists the items offered by the Prepare Upload dialog.
 */
class ExcludableItemsCollectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val projectDir: File
        get() = tempFolder.root

    private val cacheDirs = setOf(".gradle", ".kotlin", ".idea", ".git", ".junie")

    private fun file(relativePath: String, content: String = "") {
        File(projectDir, relativePath).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun dir(relativePath: String) {
        File(projectDir, relativePath).mkdirs()
    }

    private fun collect(
        defaultFileExclusions: Set<String> = setOf("local.properties"),
        ignoreFilter: IgnoreFilter = NoopIgnoreFilter
    ): List<ExcludableItem> =
        ExcludableItemsCollector(projectDir, cacheDirs, defaultFileExclusions, ignoreFilter).collect()

    private fun List<ExcludableItem>.paths() = map { it.relativePath }

    @Test
    fun `empty project has nothing to exclude`() {
        assertTrue(collect().isEmpty())
    }

    @Test
    fun `cache directories are found at any depth with their categories`() {
        dir(".gradle")
        dir(".kotlin")
        dir(".git")
        dir("app/.gradle")
        dir("libs/core/.kotlin")

        val items = collect().associateBy { it.relativePath }

        assertEquals(setOf(".gradle", ".kotlin", ".git", "app/.gradle", "libs/core/.kotlin"), items.keys)
        assertEquals(ExclusionCategory.GRADLE_CACHE, items.getValue("app/.gradle").category)
        assertEquals(ExclusionCategory.KOTLIN_CACHE, items.getValue("libs/core/.kotlin").category)
        assertEquals(ExclusionCategory.VERSION_CONTROL, items.getValue(".git").category)
        assertTrue(items.values.all { it.isDefaultExclusion && !it.isFile })
    }

    @Test
    fun `ide and agent directories and local properties are only found at the root`() {
        dir(".idea")
        dir(".junie")
        file("local.properties", "sdk.dir=/sdk")
        dir("app/.idea")
        dir("app/.junie")
        file("app/local.properties")

        val items = collect().associateBy { it.relativePath }

        assertEquals(setOf(".idea", ".junie", "local.properties"), items.keys)
        assertEquals(ExclusionCategory.IDE_SETTINGS, items.getValue(".idea").category)
        assertEquals(ExclusionCategory.AI_ASSISTANT, items.getValue(".junie").category)
        assertEquals(ExclusionCategory.CONFIGURATION_FILES, items.getValue("local.properties").category)
        assertTrue(items.getValue("local.properties").isFile)
    }

    @Test
    fun `build directories are found only next to a gradle build script`() {
        file("build.gradle.kts")
        dir("build")
        file("app/build.gradle")
        dir("app/build")
        file("docs/readme.md")
        dir("docs/build")
        file("nested/lib/build.gradle.kts")
        dir("nested/lib/build")

        val items = collect()

        assertEquals(listOf("app/build", "build", "nested/lib/build"), items.paths())
        assertTrue(items.all { it.category == ExclusionCategory.BUILD_OUTPUT })
    }

    @Test
    fun `target directories are found only next to a pom`() {
        file("pom.xml")
        dir("target")
        file("module/pom.xml")
        dir("module/target")
        dir("src/main/kotlin/target")

        val items = collect()

        assertEquals(listOf("module/target", "target"), items.paths())
        assertTrue(items.all { it.category == ExclusionCategory.BUILD_OUTPUT })
    }

    @Test
    fun `build directories are not descended into`() {
        file("build.gradle.kts")
        dir("build/.gradle")

        assertEquals(listOf("build"), collect().paths())
    }

    @Test
    fun `default file exclusions are found below the root`() {
        file("secrets.properties")
        file("app/secrets.properties")

        val items = collect(defaultFileExclusions = setOf("secrets.properties"))

        assertEquals(listOf("app/secrets.properties", "secrets.properties"), items.paths())
        assertTrue(items.all { it.isFile && it.category == ExclusionCategory.OTHER })
    }

    @Test
    fun `git-ignored directory is offered as one item and not descended into`() {
        file(".gitignore", "out/\n")
        dir("out/.gradle")
        file("out/classes/Main.class")

        val items = collect(ignoreFilter = GitignoreFileFilter(projectDir))

        assertEquals(listOf("out"), items.paths())
        assertEquals(ExclusionCategory.GIT_IGNORED, items.single().category)
        assertFalse(items.single().isFile)
        assertTrue(items.single().coveredPaths.isEmpty())
    }

    @Test
    fun `git-ignored files are grouped by the matching pattern`() {
        file(".gitignore", "*.log\n*.tmp\n")
        file("debug.log")
        file("app/logs/run.log")
        file("cache.tmp")
        file("src/Main.kt")

        val items = collect(ignoreFilter = GitignoreFileFilter(projectDir)).associateBy { it.relativePath }

        assertEquals(setOf("*.log", "*.tmp"), items.keys)
        val logs = items.getValue("*.log")
        assertEquals("*.log — 2 files", logs.displayLabel)
        assertEquals(setOf("debug.log", "app/logs/run.log"), logs.coveredPaths.toSet())
        assertTrue(logs.isFile && logs.isDefaultExclusion)
        assertEquals("*.tmp — 1 file", items.getValue("*.tmp").displayLabel)
        assertEquals(listOf("cache.tmp"), items.getValue("*.tmp").coveredPaths)
    }

    @Test
    fun `cache directories and build output win over gitignore rules`() {
        file(".gitignore", ".gradle/\nbuild/\n")
        file("build.gradle.kts")
        dir(".gradle")
        dir("build")

        val items = collect(ignoreFilter = GitignoreFileFilter(projectDir)).associateBy { it.relativePath }

        assertEquals(ExclusionCategory.GRADLE_CACHE, items.getValue(".gradle").category)
        assertEquals(ExclusionCategory.BUILD_OUTPUT, items.getValue("build").category)
    }

    @Test
    fun `items are sorted by category then path`() {
        file(".gitignore", "*.log\n")
        file("z.log")
        file("local.properties")
        dir(".idea")
        dir(".gradle")
        dir("b/.gradle")
        file("build.gradle.kts")
        dir("build")

        val items = collect(ignoreFilter = GitignoreFileFilter(projectDir))

        assertEquals(listOf("build", ".gradle", "b/.gradle", ".idea", "*.log", "local.properties"), items.paths())
    }
}
