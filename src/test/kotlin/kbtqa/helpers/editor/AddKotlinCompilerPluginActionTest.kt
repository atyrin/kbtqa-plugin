package kbtqa.helpers.editor

/**
 * Tests for [AddKotlinCompilerPluginAction]: the three parts enabling a compiler plugin takes.
 */
class AddKotlinCompilerPluginActionTest : MavenPomActionTestCase() {

    private fun kotlinPluginPom(version: String = "\${kotlin.version}") = """
        <project>
            <build>
                <plugins>
                    <plugin>
                        <groupId>org.jetbrains.kotlin</groupId>
                        <artifactId>kotlin-maven-plugin</artifactId>
                        <version>$version</version>
                        <extensions>true</extensions>
                    </plugin>
                </plugins>
            </build>
        </project>
    """.trimIndent()

    private fun enable(name: String): Boolean = AddKotlinCompilerPluginAction().enableCompilerPlugin(
        project, myFixture.editor, pom, KotlinCompilerPlugins.ENTRIES.first { it.name == name }
    )

    private fun kotlinPlugin(text: String) = plugin(text, "kotlin-maven-plugin")

    fun testPowerAssertGetsItsArtifactNameAndFunction() {
        configurePom(kotlinPluginPom())
        assertTrue(enable("power-assert"))

        val plugin = kotlinPlugin(pomText)
        val dependency = plugin.at("dependencies", "dependency")
        assertEquals(listOf("kotlin-maven-power-assert"), values(dependency, "artifactId"))
        assertEquals(listOf("\${kotlin.version}"), values(dependency, "version"))
        assertEquals(listOf("power-assert"), values(plugin.at("configuration", "compilerPlugins"), "plugin"))
        // The Maven extension sets no function to transform, so one is needed
        assertEquals(listOf("power-assert:function=kotlin.assert"), values(plugin.at("configuration", "pluginOptions"), "option"))
        assertEquals("<option>power-assert:function=kotlin.assert|</option>", caretLine())
    }

    fun testSpringAndAllOpenShareTheirArtifact() {
        configurePom(kotlinPluginPom())
        enable("spring")
        enable("all-open")

        val plugin = kotlinPlugin(pomText)
        assertEquals(listOf("kotlin-maven-allopen"), values(plugin.at("dependencies", "dependency"), "artifactId"))
        assertEquals(listOf("spring", "all-open"), values(plugin.at("configuration", "compilerPlugins"), "plugin"))
        // The spring preset needs no option
        assertEquals(listOf("all-open:annotation=com.my.Annotation"), values(plugin.at("configuration", "pluginOptions"), "option"))
    }

    fun testArtifactFollowsTheKotlinPluginVersion() {
        configurePom(kotlinPluginPom(version = "2.4.20"))
        enable("kotlinx-serialization")

        val dependency = kotlinPlugin(pomText).at("dependencies", "dependency")
        assertEquals(listOf("kotlin-maven-serialization"), values(dependency, "artifactId"))
        assertEquals(listOf("2.4.20"), values(dependency, "version"))
    }

    fun testExistingOptionIsKept() {
        configurePom(
            kotlinPluginPom().replace(
                "<extensions>true</extensions>",
                "<configuration><pluginOptions><option>power-assert:function=kotlin.test.assertTrue</option></pluginOptions></configuration>"
            )
        )
        enable("power-assert")

        assertEquals(
            listOf("power-assert:function=kotlin.test.assertTrue"),
            values(kotlinPlugin(pomText).at("configuration", "pluginOptions"), "option")
        )
    }

    fun testEnablingTwiceChangesNothing() {
        configurePom(kotlinPluginPom())
        enable("no-arg")
        val once = pomText
        enable("no-arg")

        assertEquals(once, pomText)
    }

    fun testCompilerPluginsNeedTheKotlinPlugin() {
        val text = "<project>\n    <modelVersion>4.0.0</modelVersion>\n</project>"
        configurePom(text)

        assertFalse(enable("spring"))
        assertEquals(text, pomText)
    }
}
