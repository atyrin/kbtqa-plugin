package kbtqa.helpers.editor

/**
 * Tests for [ConfigureMavenRepositoriesAction], which declares the Kotlin repositories in a pom.
 */
class ConfigureMavenRepositoriesActionTest : MavenPomActionTestCase() {

    private val kotlinRepositoryIds = listOf("dev", "bootstrap", "experimental")

    private fun runAction(text: String): String {
        configurePom(text)
        myFixture.testAction(ConfigureMavenRepositoriesAction())
        return pomText
    }

    private fun repositoryIds(text: String, section: String) =
        tag(text, section)?.subTags?.map { it.findFirstSubTag("id")?.value?.trimmedText }.orEmpty()

    fun testRepositoriesAreDeclaredInBothSections() {
        val result = runAction(
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <artifactId>app</artifactId>
                <dependencies>
                </dependencies>
            </project>
            """.trimIndent()
        )

        assertEquals(listOf("modelVersion", "artifactId", "repositories", "pluginRepositories", "dependencies"), subTagNames(result))
        assertEquals(kotlinRepositoryIds, repositoryIds(result, "repositories"))
        assertEquals(kotlinRepositoryIds, repositoryIds(result, "pluginRepositories"))
        assertEquals(
            listOf("https://redirector.kotlinlang.org/maven/dev"),
            values(tag(result, "repositories")?.subTags?.first(), "url")
        )
    }

    fun testDeclaredRepositoryIsNotDuplicated() {
        val result = runAction(
            """
            <project>
                <repositories>
                    <repository>
                        <id>central</id>
                        <url>https://repo1.maven.org/maven2/</url>
                    </repository>
                    <repository>
                        <id>kotlin-dev</id>
                        <url>https://redirector.kotlinlang.org/maven/dev/</url>
                    </repository>
                </repositories>
            </project>
            """.trimIndent()
        )

        // A trailing slash does not make the dev repository a different one
        assertEquals(listOf("central", "kotlin-dev", "bootstrap", "experimental"), repositoryIds(result, "repositories"))
    }

    fun testConfiguringTwiceChangesNothing() {
        val once = runAction("<project>\n    <modelVersion>4.0.0</modelVersion>\n</project>")
        myFixture.testAction(ConfigureMavenRepositoriesAction())

        assertEquals(once, pomText)
    }
}
