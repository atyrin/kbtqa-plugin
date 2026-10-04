package kbtqa.helpers.versions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Maven metadata parsing and version ordering of [BaseVersionsService],
 * and for the GitHub releases parsing of [GradleVersionsService].
 */
class VersionParsingTest {

    /** Exposes the protected helpers of [BaseVersionsService]. */
    private class TestVersionsService : BaseVersionsService() {
        override val toolName: String = "Test"
        override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> = emptyList()

        fun parse(xml: String) = parseVersionsFromXml(xml)
        fun compare(v1: String, v2: String) = compareVersions(v1, v2)
        fun singleChannel(versions: List<String>) = singleChannelOrEmpty("All", "description", versions)
    }

    private val service = TestVersionsService()

    private fun mavenMetadata(vararg versions: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <metadata>
          <groupId>org.jetbrains.kotlin</groupId>
          <artifactId>kotlin-gradle-plugin</artifactId>
          <versioning>
            <latest>2.2.20</latest>
            <versions>
        VERSIONS
            </versions>
          </versioning>
        </metadata>
    """.trimIndent().replace("VERSIONS", versions.joinToString("\n") { "<version>$it</version>" })

    @Test
    fun `numeric parts are compared as numbers`() {
        assertTrue(service.compare("2.10.0", "2.9.0") > 0)
        assertTrue(service.compare("1.9.24", "1.9.3") > 0)
        assertTrue(service.compare("2.0", "2.0.1") < 0)
        assertEquals(0, service.compare("2.2.20", "2.2.20"))
    }

    @Test
    fun `qualifiers are compared case-insensitively`() {
        assertTrue(service.compare("2.2.0-RC", "2.2.0-Beta1") > 0)
        assertEquals(0, service.compare("2.2.0-rc", "2.2.0-RC"))
    }

    @Test
    fun `numeric part sorts after a qualifier at the same position`() {
        assertTrue(service.compare("2.2.0-1", "2.2.0-Beta") > 0)
        assertTrue(service.compare("2.2.0-Beta", "2.2.0-1") < 0)
    }

    @Test
    fun `maven metadata versions are returned newest first`() {
        val xml = mavenMetadata("1.9.24", "2.0.0", "2.10.0", "2.9.0", "2.2.20-Beta1", "2.2.20-RC")
        assertEquals(
            listOf("2.10.0", "2.9.0", "2.2.20-RC", "2.2.20-Beta1", "2.0.0", "1.9.24"),
            service.parse(xml)
        )
    }

    @Test
    fun `blank versions are skipped and values are trimmed`() {
        val xml = mavenMetadata(" 2.0.0 ", "", "1.0.0")
        assertEquals(listOf("2.0.0", "1.0.0"), service.parse(xml))
    }

    @Test
    fun `malformed xml yields no versions`() {
        assertEquals(emptyList<String>(), service.parse("<metadata><versions><version>1.0"))
        assertEquals(emptyList<String>(), service.parse(""))
    }

    @Test
    fun `single channel is omitted when there are no versions`() {
        assertEquals(emptyList<VersionsService.VersionChannel>(), service.singleChannel(emptyList()))
        assertEquals(
            listOf(VersionsService.VersionChannel("All", "description", listOf("1.0"))),
            service.singleChannel(listOf("1.0"))
        )
    }

    @Test
    fun `gradle versions are taken from bin distribution assets`() {
        val json = """
            [
              {"tag_name": "v8.14.3", "assets": [
                {"name": "gradle-8.14.3-all.zip"},
                {"name": "gradle-8.14.3-bin.zip"},
                {"name": "gradle-8.14.3-bin.zip.sha256"}
              ]},
              {"tag_name": "v9.0.0-rc-1", "assets": [
                {"name": "gradle-9.0.0-rc-1-bin.zip"},
                {"name": "gradle-9.0.0-rc-1-src.zip"}
              ]},
              {"tag_name": "v8.9", "assets": [{"name": "gradle-8.9-bin.zip", "size": 1}]},
              {"tag_name": "no-assets"},
              {"tag_name": "nameless-asset", "assets": [{"size": 1}]}
            ]
        """.trimIndent()
        assertEquals(
            listOf("9.0.0-rc-1", "8.14.3", "8.9"),
            GradleVersionsService().parseVersionsFromJson(json)
        )
    }

    @Test
    fun `malformed gradle releases json yields no versions`() {
        assertEquals(emptyList<String>(), GradleVersionsService().parseVersionsFromJson("{\"message\": \"rate limited\"}"))
        assertEquals(emptyList<String>(), GradleVersionsService().parseVersionsFromJson("not json"))
    }

    @Test
    fun `pagination urls are read from the github link header`() {
        val service = GradleVersionsService()
        val header = "<https://api.github.com/repositories/1/releases?per_page=50&page=1>; rel=\"prev\", " +
                "<https://api.github.com/repositories/1/releases?per_page=50&page=3>; rel=\"next\", " +
                "<https://api.github.com/repositories/1/releases?per_page=50&page=9>; rel=\"last\""
        assertEquals("https://api.github.com/repositories/1/releases?per_page=50&page=1", service.parseLinkUrl(header, "prev"))
        assertEquals("https://api.github.com/repositories/1/releases?per_page=50&page=3", service.parseLinkUrl(header, "next"))
    }

    @Test
    fun `missing link header or relation yields no pagination url`() {
        val service = GradleVersionsService()
        assertNull(service.parseLinkUrl(null, "next"))
        assertNull(service.parseLinkUrl("<https://example.org?page=2>; rel=\"next\"", "prev"))
    }
}
