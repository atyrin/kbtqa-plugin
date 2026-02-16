package kbtqa.helpers.versions

import com.intellij.openapi.components.Service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Service that retrieves available Gradle versions from GitHub releases.
 */
@Service(Service.Level.APP)
class GradleVersionsService : BaseVersionsService() {

    companion object {
        private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/gradle/gradle-distributions/releases"
        private const val REQUEST_TIMEOUT_SECONDS = 30L
        private const val PER_PAGE = 50
        private val VERSION_REGEX = Regex("""gradle-(.+)-bin\.zip""")
    }

    override val toolName: String = "Gradle"

    /**
     * Result of a paginated fetch containing versions and pagination links.
     */
    data class PaginatedResult(
        val versions: List<String>,
        val prevUrl: String?,
        val nextUrl: String?,
        val errorMessage: String? = null
    )

    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        val result = fetchPage(null)
        return singleChannelOrEmpty(
            name = "All",
            description = "All available versions of Gradle from GitHub releases",
            versions = result.versions
        )
    }

    /**
     * Fetches a single page of Gradle versions.
     * @param url The URL to fetch, or null to fetch the first page.
     * @return PaginatedResult with versions and navigation URLs.
     */
    suspend fun fetchPage(url: String?): PaginatedResult {
        val targetUrl = url ?: "$GITHUB_RELEASES_URL?per_page=$PER_PAGE"
        return try {
            logger.info("Fetching Gradle versions from: $targetUrl")

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build()

            val response = withContext(Dispatchers.IO) {
                client.send(request, HttpResponse.BodyHandlers.ofString())
            }

            if (response.statusCode() != 200) {
                val message = "Failed to fetch Gradle versions: HTTP ${response.statusCode()}"
                logger.warn(message)
                return PaginatedResult(emptyList(), null, null, message)
            }

            val versions = parseVersionsFromJson(response.body())
            val linkHeader = response.headers().firstValue("link").orElse(null)
            val prevUrl = parseLinkUrl(linkHeader, "prev")
            val nextUrl = parseLinkUrl(linkHeader, "next")

            PaginatedResult(versions, prevUrl, nextUrl)
        } catch (e: IOException) {
            logger.warn("IO error while fetching Gradle versions", e)
            PaginatedResult(emptyList(), null, null, "IO error while fetching Gradle versions")
        } catch (e: Exception) {
            logger.warn("Unexpected error while fetching Gradle versions", e)
            PaginatedResult(emptyList(), null, null, "Unexpected error while fetching Gradle versions")
        }
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
