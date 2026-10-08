package kbtqa.helpers.editor

/**
 * Tests for [MavenPropertiesAction]: what choosing an entry of its popup does to the pom.
 */
class MavenPropertiesActionTest : MavenPomActionTestCase() {

    private val action = MavenPropertiesAction()

    private val kotlinPluginPom = """
        <project>
            <modelVersion>4.0.0</modelVersion>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>${'$'}{kotlin.version}</version>
                        <extensions>true</extensions>
                    </plugin>
                </plugins>
            </build>
        </project>
    """.trimIndent()

    private fun insert(name: String) = action.insertProperty(
        project, myFixture.editor, pom, MavenProperties.ENTRIES.filterIsInstance<MavenProperty>().first { it.name == name }
    )

    private fun insertOption(label: String): Boolean = action.insertPluginOption(
        project, myFixture.editor, pom, MavenProperties.ENTRIES.filterIsInstance<KotlinPluginOption>().first { it.label == label }
    )

    private fun configuration(text: String) = plugin(text, "kotlin-maven-plugin").at("configuration")

    // region Properties

    fun testPropertyIsAppendedToExistingProperties() {
        configurePom(
            """
            <project>
                <properties>
                    <kotlin.version>2.4.20</kotlin.version>
                </properties>
            </project>
            """.trimIndent()
        )
        insert("kotlin.compiler.daemon")

        assertEquals(
            """
            <project>
                <properties>
                    <kotlin.version>2.4.20</kotlin.version>
                    <kotlin.compiler.daemon>false</kotlin.compiler.daemon>
                </properties>
            </project>
            """.trimIndent(),
            pomText
        )
        assertEquals("<kotlin.compiler.daemon>false|</kotlin.compiler.daemon>", caretLine())
    }

    fun testPropertiesAreCreatedAfterTheCoordinates() {
        configurePom(
            """
            <project>
                <modelVersion>4.0.0</modelVersion>
                <groupId>demo</groupId>
                <artifactId>app</artifactId>
                <version>1.0</version>
                <dependencies>
                </dependencies>
            </project>
            """.trimIndent()
        )
        insert("kotlin.compiler.jvmTarget")

        val result = pomText
        assertEquals(listOf("modelVersion", "groupId", "artifactId", "version", "properties", "dependencies"), subTagNames(result))
        assertTrue("Blank line above the new section:\n$result", result.contains("<version>1.0</version>\n\n    <properties>"))
        assertEquals("<kotlin.compiler.jvmTarget>|</kotlin.compiler.jvmTarget>", caretLine())
    }

    fun testDeclaredPropertyIsNotDuplicated() {
        val text = """
            <project>
                <properties>
                    <kotlin.compiler.daemon>true</kotlin.compiler.daemon>
                </properties>
            </project>
        """.trimIndent()
        configurePom(text)
        insert("kotlin.compiler.daemon")

        assertEquals(text, pomText)
        assertEquals("<kotlin.compiler.daemon>true|</kotlin.compiler.daemon>", caretLine())
    }

    fun testPropertyGoesIntoTheProfileAtTheCaret() {
        configurePom(
            """
            <project>
                <properties>
                    <kotlin.version>2.4.20</kotlin.version>
                </properties>
                <profiles>
                    <profile>
                        <id>dev</id>
                        <properties>
                            <kotlin.version>2.5.0-dev-1</kotlin.version><caret>
                        </properties>
                    </profile>
                </profiles>
            </project>
            """.trimIndent()
        )
        insert("kotlin.compiler.daemon")

        val result = pomText
        assertEquals(listOf("kotlin.version"), subTagNames(result, "properties"))
        assertEquals(listOf("false"), values(tag(result, "profiles", "profile", "properties"), "kotlin.compiler.daemon"))
    }

    fun testCommentedOutLinesKeepTheirIndentation() {
        configurePom(
            """
            <project>
                <properties>
                    <kotlin.version>2.4.20</kotlin.version>
            <!--        <kotlin.compiler.jvmTarget>21</kotlin.compiler.jvmTarget>-->
            <!--        <maven.compiler.release>21</maven.compiler.release>-->
                </properties>
            </project>
            """.trimIndent()
        )
        insert("kotlin.compiler.daemon")

        assertEquals(
            """
            <project>
                <properties>
                    <kotlin.version>2.4.20</kotlin.version>
                    <kotlin.compiler.daemon>false</kotlin.compiler.daemon>
            <!--        <kotlin.compiler.jvmTarget>21</kotlin.compiler.jvmTarget>-->
            <!--        <maven.compiler.release>21</maven.compiler.release>-->
                </properties>
            </project>
            """.trimIndent(),
            pomText
        )
    }

    fun testPopupShowsNamesWithoutValues() {
        for (entry in MavenProperties.ENTRIES) {
            when (entry) {
                is MavenProperty -> assertEquals(entry.name, entry.label)
                is KotlinPluginOption -> assertFalse(entry.label, entry.label.contains('=') || entry.label.contains('('))
            }
        }
    }

    // endregion

    // region kotlin-maven-plugin <configuration>

    fun testCompilerArgsShareOneArgsTag() {
        configurePom(kotlinPluginPom)
        assertTrue(insertOption("args: -Werror"))
        assertTrue(insertOption("args: -Xjsr305"))
        assertTrue(insertOption("args: -Werror"))

        assertEquals(listOf("-Werror", "-Xjsr305=strict"), values(configuration(pomText).at("args"), "arg"))
    }

    fun testOptInUsesAMarkerThatAlwaysResolves() {
        configurePom(kotlinPluginPom)
        insertOption("args: -opt-in")

        assertEquals(listOf("-opt-in=kotlin.ExperimentalStdlibApi"), values(configuration(pomText).at("args"), "arg"))
        assertEquals("<arg>-opt-in=kotlin.ExperimentalStdlibApi|</arg>", caretLine())
    }

    fun testJdkToolchainPutsTheCaretIntoItsVersion() {
        configurePom(kotlinPluginPom)
        insertOption("jdkToolchain")

        assertNotNull(configuration(pomText).at("jdkToolchain", "version"))
        assertEquals("<version>|</version>", caretLine())
    }

    fun testExistingOptionIsKept() {
        configurePom(kotlinPluginPom.replace("<extensions>true</extensions>", "<configuration><nowarn>false</nowarn></configuration>"))
        insertOption("nowarn")

        assertEquals(listOf("false"), values(configuration(pomText), "nowarn"))
    }

    fun testOptionsNeedTheKotlinPlugin() {
        val text = "<project>\n    <modelVersion>4.0.0</modelVersion>\n</project>"
        configurePom(text)

        assertFalse(insertOption("nowarn"))
        assertEquals(text, pomText)
    }

    // endregion
}
