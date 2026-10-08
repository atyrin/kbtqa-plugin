package kbtqa.helpers.editor

import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.psi.xml.XmlTag

/**
 * Tests for [ConfigureKotlinPluginAction]: each setup it offers, and switching between them.
 */
class ConfigureKotlinPluginActionTest : MavenPomActionTestCase() {

    private val barePom = """
        <project>
            <modelVersion>4.0.0</modelVersion>
            <groupId>demo</groupId>
            <artifactId>app</artifactId>
            <version>1.0</version>
        </project>
    """.trimIndent()

    private fun apply(mode: KotlinPluginMode, compilerPluginVersion: String = "3.16.0") =
        ConfigureKotlinPluginAction().applyMode(project, pom, myFixture.editor, mode, compilerPluginVersion)

    private fun kotlinPlugin(text: String) = plugin(text, "kotlin-maven-plugin")

    private fun executionIds(plugin: XmlTag?): List<String> =
        plugin.at("executions")?.findSubTags("execution")?.map { it.findFirstSubTag("id")?.value?.trimmedText.orEmpty() }.orEmpty()

    private fun execution(plugin: XmlTag?, id: String): XmlTag? =
        plugin.at("executions")?.findSubTags("execution")?.firstOrNull { it.findFirstSubTag("id")?.value?.trimmedText == id }

    fun testSmartDefaultsDeclareThePluginWithExtensions() {
        configurePom(barePom)
        apply(KotlinPluginMode.SMART_DEFAULTS)

        val result = pomText
        val plugin = kotlinPlugin(result)
        assertEquals(listOf("\${kotlin.version}"), values(plugin, "version"))
        assertEquals(listOf("true"), values(plugin, "extensions"))
        assertNull(plugin.at("executions"))
        // Smart defaults bring kotlin-stdlib themselves
        assertNull(tag(result, "dependencies"))
        // The missing version is left for the user, with the caret in it
        assertEquals(listOf(""), values(tag(result, "properties"), "kotlin.version"))
        assertEquals("<kotlin.version>|</kotlin.version>", caretLine())
    }

    fun testKotlinOnlyAddsSourceDirectoriesExecutionsAndStdlib() {
        configurePom(barePom)
        apply(KotlinPluginMode.KOTLIN_ONLY)

        val result = pomText
        assertEquals(listOf("src/main/kotlin"), values(tag(result, "build"), "sourceDirectory"))
        assertEquals(listOf("src/test/kotlin"), values(tag(result, "build"), "testSourceDirectory"))
        assertEquals(listOf("compile", "test-compile"), executionIds(kotlinPlugin(result)))
        assertNull(kotlinPlugin(result).at("extensions"))
        val stdlib = tag(result, "dependencies", "dependency")
        assertEquals(listOf("kotlin-stdlib"), values(stdlib, "artifactId"))
        assertEquals(listOf("\${kotlin.version}"), values(stdlib, "version"))
    }

    fun testKotlinAndJavaCompilesKotlinFirst() {
        configurePom(barePom)
        apply(KotlinPluginMode.KOTLIN_AND_JAVA, compilerPluginVersion = "3.16.0")

        val result = pomText
        val plugins = tag(result, "build", "plugins")?.findSubTags("plugin").orEmpty()
        assertEquals(listOf("kotlin-maven-plugin", "maven-compiler-plugin"), plugins.map { it.findFirstSubTag("artifactId")?.value?.trimmedText })

        val kotlin = kotlinPlugin(result)
        assertEquals(listOf("kotlin-compile", "kotlin-test-compile"), executionIds(kotlin))
        assertTrue(values(execution(kotlin, "kotlin-compile").at("configuration", "sourceDirs"), "sourceDir").any { it.endsWith("src/main/java") })

        val compiler = plugin(result, "maven-compiler-plugin")
        assertEquals(listOf("3.16.0"), values(compiler, "version"))
        assertEquals(listOf("default-compile", "default-testCompile", "java-compile", "java-test-compile"), executionIds(compiler))
        assertEquals(listOf("none"), values(execution(compiler, "default-compile"), "phase"))
    }

    fun testKotlinPluginMovesBeforeAnExistingCompiler() {
        configurePom(
            """
            <project>
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.14.0</version>
                        </plugin>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <version>2.4.20</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.trimIndent()
        )
        apply(KotlinPluginMode.KOTLIN_AND_JAVA)

        val result = pomText
        val plugins = tag(result, "build", "plugins")?.findSubTags("plugin").orEmpty()
        assertEquals(listOf("kotlin-maven-plugin", "maven-compiler-plugin"), plugins.map { it.findFirstSubTag("artifactId")?.value?.trimmedText })
        // Versions already in the pom are the user's
        assertEquals(listOf("3.14.0"), values(plugin(result, "maven-compiler-plugin"), "version"))
        assertEquals(listOf("2.4.20"), values(kotlinPlugin(result), "version"))
    }

    fun testSwitchingToSmartDefaultsKeepsKaptAndConfiguration() {
        configurePom(
            """
            <project>
                <build>
                    <sourceDirectory>src/main/kotlin</sourceDirectory>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <version>2.4.20</version>
                            <executions>
                                <execution>
                                    <id>kapt</id>
                                    <goals>
                                        <goal>kapt</goal>
                                    </goals>
                                </execution>
                                <execution>
                                    <id>compile</id>
                                    <goals>
                                        <goal>compile</goal>
                                    </goals>
                                    <configuration>
                                        <args>
                                            <arg>-Xjsr305=strict</arg>
                                        </args>
                                        <sourceDirs>
                                            <sourceDir>src/main/kotlin</sourceDir>
                                        </sourceDirs>
                                    </configuration>
                                </execution>
                            </executions>
                            <configuration>
                                <jvmTarget>17</jvmTarget>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.trimIndent()
        )
        apply(KotlinPluginMode.SMART_DEFAULTS)

        val result = pomText
        val plugin = kotlinPlugin(result)
        assertEquals(listOf("true"), values(plugin, "extensions"))
        assertEquals(listOf("kapt"), executionIds(plugin))
        // Settings of the replaced execution move up to the plugin, except the source directories
        assertEquals(listOf("jvmTarget", "args"), plugin.at("configuration")?.subTags?.map { it.name })
        assertEquals(listOf("-Xjsr305=strict"), values(plugin.at("configuration", "args"), "arg"))
        assertNull(tag(result, "build", "sourceDirectory"))
    }

    fun testExistingPluginVersionIsFollowedByStdlib() {
        configurePom(
            """
            <project>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <version>2.4.20</version>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.trimIndent()
        )
        apply(KotlinPluginMode.KOTLIN_ONLY)

        val result = pomText
        assertEquals(listOf("2.4.20"), values(tag(result, "dependencies", "dependency"), "version"))
        // Nothing refers to kotlin.version, so it is not declared
        assertNull(tag(result, "properties"))
    }

    fun testPomWithParentGetsNoKotlinVersionProperty() {
        configurePom(
            """
            <project>
                <parent>
                    <groupId>demo</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0</version>
                </parent>
                <artifactId>app</artifactId>
            </project>
            """.trimIndent()
        )
        apply(KotlinPluginMode.SMART_DEFAULTS)

        val result = pomText
        assertNotNull(kotlinPlugin(result))
        assertNull(tag(result, "properties"))
    }

    fun testApplyingAModeTwiceChangesNothing() {
        configurePom(barePom)
        apply(KotlinPluginMode.KOTLIN_AND_JAVA)
        val once = pomText
        apply(KotlinPluginMode.KOTLIN_AND_JAVA)

        assertEquals(once, pomText)
    }

    fun testCommentedOutLinesKeepTheirIndentation() {
        val text = """
            <project>
                <properties>
                    <kotlin.version>2.4.20</kotlin.version>
                </properties>
                <build>
            <!--        <sourceDirectory>src/main/kotlin</sourceDirectory>-->
                    <plugins>
                        <plugin>
                            <groupId>org.jetbrains.kotlin</groupId>
                            <artifactId>kotlin-maven-plugin</artifactId>
                            <version>${'$'}{kotlin.version}</version>
                            <extensions>true</extensions>
            <!--                <configuration>-->
            <!--                    <jvmTarget>17</jvmTarget>-->
            <!--                </configuration>-->
                        </plugin>
                    </plugins>
                </build>
            </project>
        """.trimIndent()
        configurePom(text)
        apply(KotlinPluginMode.KOTLIN_AND_JAVA)

        assertEquals(columnZeroComments(text), columnZeroComments(pomText))
    }

    fun testOneUndoRevertsTheWholeSetup() {
        configurePom(barePom)
        apply(KotlinPluginMode.KOTLIN_AND_JAVA)
        assertFalse(barePom == pomText)

        UndoManager.getInstance(project).undo(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))

        assertEquals(barePom, myFixture.editor.document.text)
    }
}
