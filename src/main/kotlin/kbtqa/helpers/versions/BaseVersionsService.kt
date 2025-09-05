package kbtqa.helpers.versions

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Base class for version services that fetch versions from Maven repositories.
 * Provides common HTTP and XML parsing functionality.
 */
abstract class BaseVersionsService : VersionsService {

    companion object {
        private const val REQUEST_TIMEOUT_SECONDS = 30L
    }

    protected val logger = thisLogger()

    /**
     * Fetches versions from a Maven repository URL.
     */
    protected suspend fun getVersionsFromUrl(url: String): List<String> {
        return try {
            logger.warn("Fetching versions from: $url")

            val client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build()

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()
                .build()

            val response = withContext(Dispatchers.IO) { client.send(request, HttpResponse.BodyHandlers.ofString()) }

            if (response.statusCode() == 200) {
                parseVersionsFromXml(response.body())
            } else {
                logger.warn("Failed to fetch versions from $url: HTTP ${response.statusCode()}")
                emptyList()
            }
        } catch (e: IOException) {
            logger.warn("IO error while fetching versions from $url", e)
            emptyList()
        } catch (e: Exception) {
            logger.warn("Unexpected error while fetching versions from $url", e)
            emptyList()
        }
    }

    /**
     * Parses version information from Maven metadata XML.
     */
    protected fun parseVersionsFromXml(xmlContent: String): List<String> {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xmlContent.byteInputStream())

            val versionNodes = document.getElementsByTagName("version")
            val versions = mutableListOf<String>()

            for (i in 0 until versionNodes.length) {
                val version = versionNodes.item(i).textContent?.trim()
                if (!version.isNullOrEmpty()) {
                    versions.add(version)
                }
            }

            // Sort versions in descending order (newest first)
            versions.sortedWith { v1, v2 ->
                compareVersions(v2, v1)
            }
        } catch (e: Exception) {
            logger.warn("Error parsing XML metadata", e)
            emptyList()
        }
    }

    /**
     * Compares two version strings for sorting.
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     */
    protected fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-")
        val parts2 = v2.split(".", "-")

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: ""
            val part2 = parts2.getOrNull(i) ?: ""

            // Try to compare as numbers first
            val num1 = part1.toIntOrNull()
            val num2 = part2.toIntOrNull()

            val comparison = when {
                num1 != null && num2 != null -> num1.compareTo(num2)
                num1 != null && num2 == null -> 1 // Numbers come after text
                num1 == null && num2 != null -> -1 // Text comes before numbers
                else -> part1.compareTo(part2, ignoreCase = true)
            }

            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    /**
     * Helper to return a single VersionChannel list when versions exist, otherwise empty list.
     */
    protected fun singleChannelOrEmpty(
        name: String,
        description: String,
        versions: List<String>
    ): List<VersionsService.VersionChannel> {
        return if (versions.isNotEmpty()) listOf(VersionsService.VersionChannel(name, description, versions)) else emptyList()
    }
}