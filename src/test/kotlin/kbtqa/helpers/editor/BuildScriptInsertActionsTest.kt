package kbtqa.helpers.editor

/**
 * Tests for the QA Helper actions that insert snippets into `build.gradle.kts`:
 * [AddCompilerOptionsAction] and [AddJvmPublishingAction].
 */
class BuildScriptInsertActionsTest : GradleScriptActionTestCase() {

    fun testCompilerOptionsAreInsertedAtCaret() {
        val result = runAction(
            AddCompilerOptionsAction(), "build.gradle.kts", """
            plugins {
                kotlin("jvm") version "2.2.20"
            }
            <caret>
            dependencies { }
            """.trimIndent()
        )

        assertEquals(listOf("plugins", "withType", "configureEach", "dependencies"), topLevelCalls(result))
        assertTrue(result.contains("freeCompilerArgs.set(listOf(\"-Xrender-internal-diagnostic-names\","))
    }

    fun testPublishingIsAddedToExistingPluginsBlock() {
        val result = runAction(
            AddJvmPublishingAction(), "build.gradle.kts", """
            plugins {
                kotlin("jvm") version "2.2.20"
            }

            <caret>
            """.trimIndent()
        )

        assertEquals(listOf("plugins", "publishing"), topLevelCalls(result))
        assertEquals(listOf("kotlin(\"jvm\") version \"2.2.20\"", "`maven-publish`"), statements(result, "plugins"))
        assertTrue(statements(result, "publishing", "publications", "create").contains("from(components[\"java\"])"))
    }

    fun testPublishingCreatesPluginsBlockInEmptyScript() {
        val result = runAction(AddJvmPublishingAction(), "build.gradle.kts", "<caret>")

        assertEquals(listOf("plugins", "publishing"), topLevelCalls(result))
        assertEquals(listOf("`maven-publish`"), statements(result, "plugins"))
    }

    fun testPublishingCreatesPluginsBlockAtTheTop() {
        val result = runAction(
            AddJvmPublishingAction(), "build.gradle.kts", """
            group = "org.example"

            dependencies { }
            <caret>
            """.trimIndent()
        )

        assertEquals(listOf("plugins", "dependencies", "publishing"), topLevelCalls(result))
        assertTrue(result.startsWith("plugins {"))
    }

    fun testPublishingPluginsBlockGoesAfterImportsAndBuildscript() {
        val result = runAction(
            AddJvmPublishingAction(), "build.gradle.kts", """
            import java.io.File

            buildscript {
                repositories { mavenCentral() }
            }

            group = "org.example"
            <caret>
            """.trimIndent()
        )

        assertTrue(result.startsWith("import java.io.File\n"))
        assertEquals(listOf("buildscript", "plugins", "publishing"), topLevelCalls(result))
        assertTrue(result.indexOf("plugins {") < result.indexOf("group = "))
    }

    fun testPublishingIsNotInsertedAbovePluginsBlock() {
        val result = runAction(
            AddJvmPublishingAction(), "build.gradle.kts", """
            <caret>plugins {
                kotlin("jvm") version "2.2.20"
            }
            """.trimIndent()
        )

        assertEquals(listOf("plugins", "publishing"), topLevelCalls(result))
    }

    fun testExistingPublishingSetupIsKept() {
        val script = """
            plugins {
                `maven-publish`
            }
            publishing {
                repositories { mavenLocal() }
            }
            <caret>
        """.trimIndent()

        val result = runAction(AddJvmPublishingAction(), "build.gradle.kts", script)

        assertEquals(script.replace("<caret>", ""), result)
    }
}
