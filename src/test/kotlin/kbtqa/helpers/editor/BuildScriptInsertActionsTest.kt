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

    fun testPublishingCreatesPluginsBlockWhenMissing() {
        val result = runAction(AddJvmPublishingAction(), "build.gradle.kts", "<caret>")

        assertEquals(1, result.occurrences("`maven-publish`"))
        assertEquals(1, result.occurrences("publishing {"))
        assertEquals(listOf("`maven-publish`"), statements(result, "plugins"))
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
