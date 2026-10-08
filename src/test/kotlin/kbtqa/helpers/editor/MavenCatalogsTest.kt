package kbtqa.helpers.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the plain rules behind the pom.xml helpers: [MavenKotlinPlugin], the option and
 * compiler plugin catalogs, [MavenRepositories] and [MavenPluginVersions].
 */
class MavenCatalogsTest {

    @Test
    fun `added kotlin artifacts follow the plugin version or kotlin version`() {
        assertEquals("2.4.20", MavenKotlinPlugin.kotlinArtifactVersion(" 2.4.20 "))
        assertEquals("\${my.kotlin}", MavenKotlinPlugin.kotlinArtifactVersion("\${my.kotlin}"))
        assertEquals("\${kotlin.version}", MavenKotlinPlugin.kotlinArtifactVersion(null))
        assertEquals("\${kotlin.version}", MavenKotlinPlugin.kotlinArtifactVersion(""))
    }

    @Test
    fun `kotlin version property is declared only in a standalone pom that lacks it`() {
        assertTrue(MavenKotlinPlugin.needsKotlinVersionProperty(declaresProperty = false, hasParent = false))
        assertFalse(MavenKotlinPlugin.needsKotlinVersionProperty(declaresProperty = true, hasParent = false))
        assertFalse(MavenKotlinPlugin.needsKotlinVersionProperty(declaresProperty = false, hasParent = true))
    }

    @Test
    fun `only compile executions belong to the modes`() {
        assertTrue(MavenKotlinPlugin.isOwnedKotlinExecution(listOf("compile")))
        assertTrue(MavenKotlinPlugin.isOwnedKotlinExecution(listOf("test-compile")))
        assertFalse(MavenKotlinPlugin.isOwnedKotlinExecution(listOf("kapt")))
        assertFalse(MavenKotlinPlugin.isOwnedKotlinExecution(listOf("compile", "kapt")))
        assertFalse(MavenKotlinPlugin.isOwnedKotlinExecution(emptyList()))
        assertTrue(MavenKotlinPlugin.isOwnedCompilerExecution("default-compile"))
        assertFalse(MavenKotlinPlugin.isOwnedCompilerExecution("my-compile"))
    }

    @Test
    fun `kotlin source directories are recognised however they are written`() {
        for (value in listOf("src/main/kotlin", "\${project.basedir}/src/main/kotlin", "./src/test/kotlin/", "src\\main\\kotlin")) {
            assertTrue(value, MavenKotlinPlugin.isKotlinSourceDirectory(value))
        }
        assertFalse(MavenKotlinPlugin.isKotlinSourceDirectory("src/custom/kotlin"))
        assertFalse(MavenKotlinPlugin.isKotlinSourceDirectory(null))
    }

    @Test
    fun `apache plugins may omit their group`() {
        assertTrue(MavenKotlinPlugin.isMavenCompilerPlugin(null, "maven-compiler-plugin"))
        assertTrue(MavenKotlinPlugin.isMavenCompilerPlugin("org.apache.maven.plugins", "maven-compiler-plugin"))
        assertFalse(MavenKotlinPlugin.isMavenCompilerPlugin("com.example", "maven-compiler-plugin"))
        assertFalse(MavenKotlinPlugin.isKotlinStdlib("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"))
    }

    @Test
    fun `latest stable version stays on maven 3`() {
        val versions = listOf("3.9.0", "3.10.1", "3.11.0-M1", "4.0.0-beta-1", "4.0.0", "2.5")
        assertEquals("3.10.1", MavenPluginVersions.latestStableMaven3(versions))
        assertNull(MavenPluginVersions.latestStableMaven3(listOf("4.0.0", "3.0-alpha-1")))
    }

    @Test
    fun `repeatable compiler arguments are told apart by their prefix`() {
        val jsr305 = MavenProperties.ENTRIES.filterIsInstance<KotlinPluginOption>().first { it.label == "args: -Xjsr305" }
        assertTrue(jsr305.isSetBy(" -Xjsr305=warn "))
        assertFalse(jsr305.isSetBy("-Werror"))
        val nowarn = MavenProperties.ENTRIES.filterIsInstance<KotlinPluginOption>().first { it.label == "nowarn" }
        assertTrue(nowarn.isSetBy("false"))
    }

    @Test
    fun `compiler plugin options carry the plugin name`() {
        val powerAssert = KotlinCompilerPlugins.ENTRIES.first { it.name == "power-assert" }
        assertEquals("power-assert:function=kotlin.assert", powerAssert.optionText)
        assertTrue(powerAssert.isOptionSetBy("power-assert:function=kotlin.require"))
        assertFalse(powerAssert.isOptionSetBy("all-open:annotation=com.my.Annotation"))
        // Plugins that work without an option get none
        val withoutOptions = KotlinCompilerPlugins.ENTRIES.filter { it.exampleOption == null }.map { it.name }
        assertEquals(listOf("spring", "lombok", "kotlinx-serialization"), withoutOptions)
    }

    @Test
    fun `daemon jvm arguments are prefilled without dashes`() {
        val jvmArgs = MavenProperties.ENTRIES.filterIsInstance<MavenProperty>().first { it.name == "kotlin.compiler.daemon.jvmArgs" }
        // kotlin-maven-plugin hands them on as they are, and a leading dash would reach the JVM doubled
        assertTrue(jvmArgs.value.split(',').none { it.startsWith("-") })
    }

    @Test
    fun `repositories with a trailing slash are not missing`() {
        val missing = MavenRepositories.missingFrom(listOf("https://redirector.kotlinlang.org/maven/dev/", "https://repo1.maven.org/maven2"))
        assertEquals(listOf("bootstrap", "experimental"), missing.map { it.id })
    }

    @Test
    fun `caret lands at the end of the value or between the tags`() {
        assertEquals("<a>value".length, MavenProperties.caretOffsetInTagText("<a>value</a>"))
        assertEquals("<a>".length, MavenProperties.caretOffsetInTagText("<a></a>"))
    }

    @Test
    fun `new sections go after the most specific anchor present`() {
        assertEquals("version", MavenProperties.anchorName(listOf("modelVersion", "groupId", "version"), MavenProperties.PROPERTIES_ANCHORS))
        assertNull(MavenProperties.anchorName(listOf("dependencies"), MavenProperties.PROPERTIES_ANCHORS))
    }
}
