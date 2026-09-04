package kbtqa.helpers.editor

import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for the XML PSI part of [MavenPropertiesAction]: where the property lands and what the
 * resulting `pom.xml` looks like. The popup itself is verified manually via `runIde`.
 */
class MavenPropertiesActionTest : BasePlatformTestCase() {

    private val jvmTarget = MavenProperty("kotlin.compiler.jvmTarget")
    private val daemon = MavenProperty("kotlin.compiler.daemon", "true")

    fun testAppendsToExistingPropertiesBlock() {
        val result = insert(
            daemon,
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
                <properties>
                    <kotlin.version>2.3.20</kotlin.version><caret>
                </properties>
            </project>
            """.trimIndent()
        )

        assertEquals(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
                <properties>
                    <kotlin.version>2.3.20</kotlin.version>
                    <kotlin.compiler.daemon>true</kotlin.compiler.daemon>
                </properties>
            </project>
            """.trimIndent(),
            result
        )
    }

    fun testDoesNotAddNamespaceDeclarationToTheInsertedTag() {
        val result = insert(
            jvmTarget,
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <properties><caret></properties>
            </project>
            """.trimIndent()
        )

        assertFalse("Inserted tag must not carry an xmlns attribute: $result", result.contains("xmlns=\"\""))
        assertTrue(result, result.contains("<kotlin.compiler.jvmTarget></kotlin.compiler.jvmTarget>"))
    }

    fun testCreatesPropertiesBlockAfterCoordinates() {
        val result = insert(
            jvmTarget,
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>1.0-SNAPSHOT</version>
                <dependencies><caret></dependencies>
            </project>
            """.trimIndent()
        )

        assertEquals(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>demo</artifactId>
                <version>1.0-SNAPSHOT</version>

                <properties>
                    <kotlin.compiler.jvmTarget></kotlin.compiler.jvmTarget>
                </properties>
                <dependencies></dependencies>
            </project>
            """.trimIndent(),
            result
        )
    }

    fun testLeavesSurroundingBlankLinesIntact() {
        val result = insert(
            jvmTarget,
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>

                <dependencies></dependencies><caret>
            </project>
            """.trimIndent()
        )

        // The block lands right after the anchor, so the pre-existing blank line stays above
        // <dependencies> and a new one is added above <properties> — no doubled blank lines.
        assertEquals(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>

                <properties>
                    <kotlin.compiler.jvmTarget></kotlin.compiler.jvmTarget>
                </properties>

                <dependencies></dependencies>
            </project>
            """.trimIndent(),
            result
        )
    }

    fun testPrefersThePropertiesBlockEnclosingTheCaret() {
        val result = insert(
            daemon,
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <properties>
                    <kotlin.version>2.3.20</kotlin.version>
                </properties>
                <profiles>
                    <profile>
                        <id>ci</id>
                        <properties>
                            <ci>true</ci><caret>
                        </properties>
                    </profile>
                </profiles>
            </project>
            """.trimIndent()
        )

        assertEquals(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <properties>
                    <kotlin.version>2.3.20</kotlin.version>
                </properties>
                <profiles>
                    <profile>
                        <id>ci</id>
                        <properties>
                            <ci>true</ci>
                            <kotlin.compiler.daemon>true</kotlin.compiler.daemon>
                        </properties>
                    </profile>
                </profiles>
            </project>
            """.trimIndent(),
            result
        )
    }

    fun testExpandsSelfClosedPropertiesTag() {
        val result = insert(
            daemon,
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>
                <properties/><caret>
            </project>
            """.trimIndent()
        )

        assertTrue(result, result.contains("<kotlin.compiler.daemon>true</kotlin.compiler.daemon>"))
        assertFalse(result, result.contains("<properties/>"))
    }

    fun testDoesNotDuplicateAnExistingProperty() {
        val pom = """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <properties>
                    <kotlin.compiler.daemon>false</kotlin.compiler.daemon><caret>
                </properties>
            </project>
        """.trimIndent()

        val result = insert(daemon, pom)

        assertEquals(pom.replace("<caret>", ""), result)
        // The caret is parked at the end of the existing value, ready to be edited
        assertEquals(
            result.indexOf("false") + "false".length,
            myFixture.editor.caretModel.offset
        )
    }

    fun testInsertsIntoTheSameBlockOnRepeatedUse() {
        val action = MavenPropertiesAction()
        myFixture.configureByText(
            "pom.xml",
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId><caret>
            </project>
            """.trimIndent()
        )
        val xmlFile = myFixture.file as XmlFile
        action.insertProperty(project, myFixture.editor, xmlFile, jvmTarget)
        action.insertProperty(project, myFixture.editor, xmlFile, daemon)

        assertEquals(
            """
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <artifactId>demo</artifactId>

                <properties>
                    <kotlin.compiler.jvmTarget></kotlin.compiler.jvmTarget>
                    <kotlin.compiler.daemon>true</kotlin.compiler.daemon>
                </properties>
            </project>
            """.trimIndent(),
            myFixture.editor.document.text
        )
    }

    private fun insert(property: MavenProperty, pom: String): String {
        myFixture.configureByText("pom.xml", pom)
        MavenPropertiesAction().insertProperty(project, myFixture.editor, myFixture.file as XmlFile, property)
        return myFixture.editor.document.text
    }
}
