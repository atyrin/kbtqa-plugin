package kbtqa.helpers.editor

/**
 * Tests for [ConfigureToolchainAction], which pins the compilation JDK with maven-toolchains-plugin.
 */
class ConfigureToolchainActionTest : MavenPomActionTestCase() {

    private fun configure(pluginVersion: String = "3.3.0") =
        ConfigureToolchainAction().configureToolchain(project, pom, myFixture.editor, pluginVersion)

    private fun toolchainsPlugin(text: String) = plugin(text, "maven-toolchains-plugin")

    fun testToolchainIsSetUpInPomWithoutBuild() {
        configurePom("<project>\n    <modelVersion>4.0.0</modelVersion>\n</project>")
        configure(pluginVersion = "3.3.0")

        val plugin = toolchainsPlugin(pomText)
        assertEquals(listOf("org.apache.maven.plugins"), values(plugin, "groupId"))
        assertEquals(listOf("3.3.0"), values(plugin, "version"))
        assertEquals(listOf("toolchain"), values(plugin.at("executions", "execution", "goals"), "goal"))
        assertEquals(listOf(""), values(plugin.at("configuration", "toolchains", "jdk"), "version"))
        // The JDK version is left for the user, with the caret in it
        assertEquals("<version>|</version>", caretLine())
    }

    fun testExistingPartsAreKept() {
        configurePom(
            """
            <project>
                <build>
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-toolchains-plugin</artifactId>
                            <version>3.1.0</version>
                            <configuration>
                                <toolchains>
                                    <jdk>
                                        <version>21</version>
                                    </jdk>
                                </toolchains>
                            </configuration>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """.trimIndent()
        )
        configure(pluginVersion = "3.3.0")

        val plugin = toolchainsPlugin(pomText)
        assertEquals(listOf("3.1.0"), values(plugin, "version"))
        assertEquals(listOf("toolchain"), values(plugin.at("executions", "execution", "goals"), "goal"))
        assertEquals(listOf("21"), values(plugin.at("configuration", "toolchains", "jdk"), "version"))
        assertEquals("<version>21|</version>", caretLine())
    }

    fun testConfiguringTwiceChangesNothing() {
        configurePom("<project>\n    <modelVersion>4.0.0</modelVersion>\n</project>")
        configure()
        val once = pomText
        configure()

        assertEquals(once, pomText)
    }
}
