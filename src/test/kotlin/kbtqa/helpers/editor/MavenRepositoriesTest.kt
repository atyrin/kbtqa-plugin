package kbtqa.helpers.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenRepositoriesTest {

    @Test
    fun `catalog holds the three kotlin channels`() {
        assertEquals(listOf("dev", "bootstrap", "experimental"), MavenRepositories.ENTRIES.map { it.id })
        assertTrue(MavenRepositories.ENTRIES.all { it.url.startsWith("https://redirector.kotlinlang.org/maven/") })
    }

    @Test
    fun `tag text wraps id and url in the given tag`() {
        val dev = MavenRepository("dev", "https://example.org/dev")
        assertEquals(
            "<repository><id>dev</id><url>https://example.org/dev</url></repository>",
            dev.tagText(MavenRepositories.REPOSITORY_TAG)
        )
        assertEquals(
            "<pluginRepository><id>dev</id><url>https://example.org/dev</url></pluginRepository>",
            dev.tagText(MavenRepositories.PLUGIN_REPOSITORY_TAG)
        )
    }

    @Test
    fun `url normalization ignores trailing slashes and surrounding space`() {
        assertEquals("https://example.org/dev", MavenRepositories.normalizeUrl("https://example.org/dev/"))
        assertEquals("https://example.org/dev", MavenRepositories.normalizeUrl("  https://example.org/dev  "))
    }

    @Test
    fun `everything is missing from an empty section`() {
        assertEquals(MavenRepositories.ENTRIES, MavenRepositories.missingFrom(emptyList()))
    }

    @Test
    fun `an already declared url is not offered again`() {
        val missing = MavenRepositories.missingFrom(listOf("https://redirector.kotlinlang.org/maven/dev"))
        assertEquals(listOf("bootstrap", "experimental"), missing.map { it.id })
    }

    @Test
    fun `a trailing slash does not make a declared repository look new`() {
        val declared = MavenRepositories.ENTRIES.map { "${it.url}/" }
        assertTrue(MavenRepositories.missingFrom(declared).isEmpty())
    }

    @Test
    fun `unrelated repositories do not hide anything`() {
        val missing = MavenRepositories.missingFrom(listOf("https://repo1.maven.org/maven2"))
        assertEquals(MavenRepositories.ENTRIES, missing)
    }
}
