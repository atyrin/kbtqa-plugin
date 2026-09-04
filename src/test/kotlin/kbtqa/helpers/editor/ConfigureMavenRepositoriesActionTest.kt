package kbtqa.helpers.editor

import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for the XML PSI part of [ConfigureMavenRepositoriesAction].
 */
class ConfigureMavenRepositoriesActionTest : BasePlatformTestCase() {

    fun testCreatesBothSectionsInAMinimalPom() {
        val result = configure(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <artifactId>demo</artifactId>
            </project>
            """.trimIndent()
        )

        assertEquals(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <artifactId>demo</artifactId>

                <repositories>
                    <repository>
                        <id>dev</id>
                        <url>https://redirector.kotlinlang.org/maven/dev</url>
                    </repository>
                    <repository>
                        <id>bootstrap</id>
                        <url>https://redirector.kotlinlang.org/maven/bootstrap</url>
                    </repository>
                    <repository>
                        <id>experimental</id>
                        <url>https://redirector.kotlinlang.org/maven/experimental</url>
                    </repository>
                </repositories>

                <pluginRepositories>
                    <pluginRepository>
                        <id>dev</id>
                        <url>https://redirector.kotlinlang.org/maven/dev</url>
                    </pluginRepository>
                    <pluginRepository>
                        <id>bootstrap</id>
                        <url>https://redirector.kotlinlang.org/maven/bootstrap</url>
                    </pluginRepository>
                    <pluginRepository>
                        <id>experimental</id>
                        <url>https://redirector.kotlinlang.org/maven/experimental</url>
                    </pluginRepository>
                </pluginRepositories>
            </project>
            """.trimIndent(),
            result
        )
    }

    fun testPlacesSectionsAfterProperties() {
        val result = configure(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>

                <properties>
                    <kotlin.version>2.3.20</kotlin.version>
                </properties>
                <build></build>
            </project>
            """.trimIndent()
        )

        val propertiesEnd = result.indexOf("</properties>")
        val repositories = result.indexOf("<repositories>")
        val pluginRepositories = result.indexOf("<pluginRepositories>")
        val build = result.indexOf("<build>")

        assertTrue(result, propertiesEnd < repositories)
        assertTrue(result, repositories < pluginRepositories)
        assertTrue(result, pluginRepositories < build)
    }

    fun testMergesIntoAnExistingSectionWithoutTouchingForeignRepositories() {
        val result = configure(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
                <repositories>
                    <repository>
                        <id>company</id>
                        <url>https://repo.example.com/releases</url>
                    </repository>
                    <repository>
                        <id>dev</id>
                        <url>https://redirector.kotlinlang.org/maven/dev/</url>
                    </repository>
                </repositories>
            </project>
            """.trimIndent()
        )

        // The company repo survives untouched
        assertEquals(result, 1, countOf(result, "<url>https://repo.example.com/releases</url>"))
        // dev is not duplicated despite the trailing slash: once in each of the two sections
        assertEquals(result, 2, countOf(result, "<id>dev</id>"))
        assertEquals(result, 1, countOf(result, "https://redirector.kotlinlang.org/maven/dev/"))
        // bootstrap and experimental were added to both sections
        assertEquals(result, 2, countOf(result, "<id>bootstrap</id>"))
        assertEquals(result, 2, countOf(result, "<id>experimental</id>"))
    }

    fun testRunningTwiceChangesNothing() {
        val pom = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
            </project>
        """.trimIndent()

        myFixture.configureByText("pom.xml", pom)
        val xmlFile = myFixture.file as XmlFile
        val action = ConfigureMavenRepositoriesAction()
        action.configureRepositories(project, xmlFile)
        val afterFirst = myFixture.editor.document.text
        action.configureRepositories(project, xmlFile)

        assertEquals(afterFirst, myFixture.editor.document.text)
        assertEquals(3, countOf(afterFirst, "<repository>"))
        assertEquals(3, countOf(afterFirst, "<pluginRepository>"))
    }

    private fun countOf(text: String, needle: String): Int =
        text.split(needle).size - 1

    private fun configure(pom: String): String {
        myFixture.configureByText("pom.xml", pom)
        ConfigureMavenRepositoriesAction().configureRepositories(project, myFixture.file as XmlFile)
        return myFixture.editor.document.text
    }
}
