package kbtqa.helpers.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenPropertiesTest {

    @Test
    fun `catalog holds the kotlin compiler group followed by the maven compiler group`() {
        assertEquals(
            listOf(
                "kotlin.compiler.jvmTarget",
                "kotlin.compiler.languageVersion",
                "kotlin.compiler.apiVersion",
                "kotlin.compiler.jdkRelease",
                "kotlin.compiler.jdkHome",
                "kotlin.compiler.daemon",
                "kotlin.compiler.incremental",
            ),
            MavenProperties.GROUPS[0].map { it.name }
        )
        assertEquals(
            listOf("maven.compiler.release", "maven.compiler.target"),
            MavenProperties.GROUPS[1].map { it.name }
        )
        assertEquals(2, MavenProperties.GROUPS.size)
    }

    @Test
    fun `entries are the groups flattened in order`() {
        assertEquals(MavenProperties.GROUPS.flatten(), MavenProperties.ENTRIES)
    }

    @Test
    fun `a separator is drawn above the first entry of every group but the first`() {
        assertEquals(
            setOf(MavenProperty("maven.compiler.release")),
            MavenProperties.ENTRIES_WITH_SEPARATOR_ABOVE
        )
    }

    @Test
    fun `the maven compiler group carries no preset values`() {
        assertTrue(MavenProperties.GROUPS[1].all { it.value.isEmpty() })
    }

    @Test
    fun `only daemon and incremental carry a default value`() {
        val withValue = MavenProperties.ENTRIES.filter { it.value.isNotEmpty() }
        assertEquals(listOf("kotlin.compiler.daemon", "kotlin.compiler.incremental"), withValue.map { it.name })
        assertTrue(withValue.all { it.value == "true" })
    }

    @Test
    fun `label omits the value hint for valueless properties`() {
        assertEquals("kotlin.compiler.jdkHome", MavenProperty("kotlin.compiler.jdkHome").label)
        assertEquals("kotlin.compiler.daemon (true)", MavenProperty("kotlin.compiler.daemon", "true").label)
    }

    @Test
    fun `tag text wraps name and value`() {
        assertEquals("<kotlin.compiler.jdkHome></kotlin.compiler.jdkHome>", MavenProperty("kotlin.compiler.jdkHome").tagText)
        assertEquals(
            "<kotlin.compiler.daemon>true</kotlin.compiler.daemon>",
            MavenProperty("kotlin.compiler.daemon", "true").tagText
        )
    }

    @Test
    fun `caret lands between the tags for an empty property`() {
        val tagText = MavenProperty("kotlin.compiler.jdkHome").tagText
        assertEquals("<kotlin.compiler.jdkHome>".length, MavenProperties.caretOffsetInTagText(tagText))
    }

    @Test
    fun `caret lands after the value for a valued property`() {
        val tagText = MavenProperty("kotlin.compiler.daemon", "true").tagText
        assertEquals("<kotlin.compiler.daemon>true".length, MavenProperties.caretOffsetInTagText(tagText))
    }

    @Test
    fun `caret offset degrades gracefully for a self-closed tag`() {
        assertEquals("<kotlin.compiler.daemon/>".length, MavenProperties.caretOffsetInTagText("<kotlin.compiler.daemon/>"))
    }

    private fun propertiesAnchor(children: List<String>) =
        MavenProperties.anchorName(children, MavenProperties.PROPERTIES_ANCHORS)

    @Test
    fun `properties anchor prefers packaging`() {
        val children = listOf("modelVersion", "groupId", "artifactId", "version", "packaging", "dependencies")
        assertEquals("packaging", propertiesAnchor(children))
    }

    @Test
    fun `properties anchor falls back through the coordinate elements`() {
        assertEquals("version", propertiesAnchor(listOf("modelVersion", "artifactId", "version")))
        assertEquals("artifactId", propertiesAnchor(listOf("modelVersion", "groupId", "artifactId")))
        assertEquals("groupId", propertiesAnchor(listOf("modelVersion", "groupId")))
        assertEquals("parent", propertiesAnchor(listOf("modelVersion", "parent")))
        assertEquals("modelVersion", propertiesAnchor(listOf("modelVersion", "build")))
    }

    @Test
    fun `properties anchor is null for a project without coordinates`() {
        assertNull(propertiesAnchor(emptyList()))
        assertNull(propertiesAnchor(listOf("dependencies", "build")))
    }

    @Test
    fun `repositories anchor prefers properties over the coordinates`() {
        assertEquals(
            "properties",
            MavenProperties.anchorName(
                listOf("modelVersion", "artifactId", "properties", "build"),
                MavenRepositories.REPOSITORIES_ANCHORS
            )
        )
        assertEquals(
            "artifactId",
            MavenProperties.anchorName(listOf("modelVersion", "artifactId"), MavenRepositories.REPOSITORIES_ANCHORS)
        )
    }

    @Test
    fun `plugin repositories anchor prefers repositories`() {
        assertEquals(
            "repositories",
            MavenProperties.anchorName(
                listOf("modelVersion", "properties", "repositories"),
                MavenRepositories.PLUGIN_REPOSITORIES_ANCHORS
            )
        )
    }

    @Test
    fun `isPomFile matches only pom xml`() {
        assertTrue(MavenProperties.isPomFile("pom.xml"))
        assertFalse(MavenProperties.isPomFile(null))
        assertFalse(MavenProperties.isPomFile("pom.xml.bak"))
        assertFalse(MavenProperties.isPomFile("POM.XML"))
        assertFalse(MavenProperties.isPomFile("build.gradle.kts"))
    }
}
