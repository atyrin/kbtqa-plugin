package kbtqa.helpers.versions

import com.intellij.openapi.components.Service
import kotlinx.serialization.json.*

/**
 * Service that retrieves available Gradle versions from GitHub releases.
 */
@Service(Service.Level.APP)
class GradleVersionsService : BaseVersionsService(), PaginatedVersionsService {

    companion object {
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/gradle/gradle-distributions/releases"
        private const val PER_PAGE = 50
        private val VERSION_REGEX = Regex("""gradle-(.+)-bin\.zip""")
    }

    override val toolName: String = "Gradle"

    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        val result = fetchPage(null)
        return singleChannelOrEmpty(
            name = "All",
            description = "All available versions of Gradle from GitHub releases",
            versions = result.versions
        )
    }

    override suspend fun fetchPage(url: String?): PaginatedVersionsService.PaginatedResult {
        val targetUrl = url ?: "$GITHUB_RELEASES_URL?per_page=$PER_PAGE"

        val response = fetchHttpResponse(targetUrl, mapOf("Accept" to "application/vnd.github+json"))
            ?: return PaginatedVersionsService.PaginatedResult(
                emptyList(), null, null, "Failed to fetch Gradle versions"
            )

        val versions = parseVersionsFromJson(response.body())
        val linkHeader = response.headers().firstValue("link").orElse(null)
        val prevUrl = parseLinkUrl(linkHeader, "prev")
        val nextUrl = parseLinkUrl(linkHeader, "next")

        return PaginatedVersionsService.PaginatedResult(versions, prevUrl, nextUrl)
    }

    private fun parseLinkUrl(linkHeader: String?, rel: String): String? {
        if (linkHeader == null) return null
        val regex = Regex("""<([^>]+)>;\s*rel="$rel"""")
        return regex.find(linkHeader)?.groupValues?.get(1)
    }

    private fun parseVersionsFromJson(jsonContent: String): List<String> {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val releases = json.parseToJsonElement(jsonContent).jsonArray
            val versions = mutableSetOf<String>()

            for (release in releases) {
                val assets = release.jsonObject["assets"]?.jsonArray ?: continue
                for (asset in assets) {
                    val name = asset.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: continue
                    val matchResult = VERSION_REGEX.find(name)
                    if (matchResult != null) {
                        versions.add(matchResult.groupValues[1])
                    }
                }
            }

            versions.sortedWith { v1, v2 -> compareVersions(v2, v1) }
        } catch (e: Exception) {
            logger.warn("Error parsing Gradle versions JSON", e)
            emptyList()
        }
    }
}
