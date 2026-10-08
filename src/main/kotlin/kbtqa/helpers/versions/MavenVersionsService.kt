package kbtqa.helpers.versions

import com.intellij.openapi.components.Service
import kotlinx.serialization.json.*

/**
 * Service that retrieves available Maven versions: the distribution itself, the Maven Compiler
 * Plugin, and the Maven Daemon.
 *
 * Kotlin's own Maven plugin is deliberately not listed here — `kotlin-maven-plugin` is released
 * from the same train as `kotlin-gradle-plugin`, so its versions are already shown by
 * [KotlinVersionsService].
 */
@Service(Service.Level.APP)
class MavenVersionsService : BaseVersionsService() {

    companion object {
        private const val MAVEN_REPO_URL =
            "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/maven-metadata.xml"
        private const val APACHE_PLUGINS_REPO_URL = "https://repo1.maven.org/maven2/org/apache/maven/plugins"
        private const val COMPILER_PLUGIN_ARTIFACT_ID = "maven-compiler-plugin"

        // mvnd is not published to Maven Central, only as GitHub releases
        private const val MVND_RELEASES_URL = "https://api.github.com/repos/apache/maven-mvnd/releases?per_page=100"
    }

    override val toolName: String = "Maven"

    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        return listOf(
            VersionsService.VersionChannel(
                "Maven",
                "Apache Maven distributions from Maven Central",
                getVersionsFromUrl(MAVEN_REPO_URL)
            ),
            VersionsService.VersionChannel(
                "Maven Compiler Plugin",
                "maven-compiler-plugin releases from Maven Central",
                getMavenPluginVersions(COMPILER_PLUGIN_ARTIFACT_ID)
            ),
            VersionsService.VersionChannel(
                "Maven Daemon",
                "mvnd releases from GitHub",
                getMvndVersions()
            )
        )
    }

    /**
     * All published versions of the Apache Maven plugin [artifactId] (e.g. `maven-compiler-plugin`),
     * newest first; empty when Maven Central is unreachable.
     */
    suspend fun getMavenPluginVersions(artifactId: String): List<String> =
        getVersionsFromUrl("$APACHE_PLUGINS_REPO_URL/$artifactId/maven-metadata.xml")

    private suspend fun getMvndVersions(): List<String> {
        val response = fetchHttpResponse(MVND_RELEASES_URL, mapOf("Accept" to "application/vnd.github+json"))
            ?: return emptyList()
        return parseTagNamesFromJson(response.body())
    }

    private fun parseTagNamesFromJson(jsonContent: String): List<String> {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val releases = json.parseToJsonElement(jsonContent).jsonArray
            val versions = mutableSetOf<String>()

            for (release in releases) {
                val tag = release.jsonObject["tag_name"]?.jsonPrimitive?.contentOrNull ?: continue
                val version = tag.removePrefix("v").trim()
                if (version.isNotEmpty()) {
                    versions.add(version)
                }
            }

            versions.sortedWith { v1, v2 -> compareVersions(v2, v1) }
        } catch (e: Exception) {
            logger.warn("Error parsing mvnd releases JSON", e)
            emptyList()
        }
    }
}
