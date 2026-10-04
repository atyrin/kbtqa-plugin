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
        private val QUALIFIER_CHUNK_REGEX = Regex("""\d+|\D+""")
        private val httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .build()
    }

    protected val logger = thisLogger()

    /**
     * Sends an HTTP GET request and returns the response, or null on failure.
     */
    protected suspend fun fetchHttpResponse(
        url: String,
        headers: Map<String, String> = emptyMap()
    ): HttpResponse<String>? {
        return try {
            logger.info("Fetching from: $url")

            val requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .GET()

            for ((key, value) in headers) {
                requestBuilder.header(key, value)
            }

            val request = requestBuilder.build()

            val response = withContext(Dispatchers.IO) {
                httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            }

            if (response.statusCode() == 200) {
                response
            } else {
                logger.warn("HTTP request failed for $url: HTTP ${response.statusCode()}")
                null
            }
        } catch (e: IOException) {
            logger.warn("IO error while fetching from $url", e)
            null
        } catch (e: Exception) {
            logger.warn("Unexpected error while fetching from $url", e)
            null
        }
    }

    /**
     * Fetches versions from a Maven repository URL.
     */
    protected suspend fun getVersionsFromUrl(url: String): List<String> {
        val response = fetchHttpResponse(url) ?: return emptyList()
        return parseVersionsFromXml(response.body())
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
     *
     * Parts are compared numerically when both are numbers; qualifiers are compared case-insensitively
     * with embedded numbers compared numerically (`RC10` > `RC2`). A release is newer than its
     * pre-releases (`2.0.0` > `2.0.0-RC`), but older than a version with an extra number (`2.0` < `2.0.1`).
     */
    protected fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-")
        val parts2 = v2.split(".", "-")

        val maxLength = maxOf(parts1.size, parts2.size)

        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i)
            val part2 = parts2.getOrNull(i)

            val comparison = when {
                part1 == null -> -compareToMissingPart(part2!!)
                part2 == null -> compareToMissingPart(part1)
                else -> compareParts(part1, part2)
            }

            if (comparison != 0) {
                return comparison
            }
        }

        return 0
    }

    /**
     * Compares a part present in only one of the versions with the missing part of the other one:
     * an extra number makes the version newer, an extra qualifier makes it a pre-release, so older.
     */
    private fun compareToMissingPart(part: String): Int = if (part.toLongOrNull() != null) 1 else -1

    private fun compareParts(part1: String, part2: String): Int {
        // Try to compare as numbers first
        val num1 = part1.toLongOrNull()
        val num2 = part2.toLongOrNull()

        return when {
            num1 != null && num2 != null -> num1.compareTo(num2)
            num1 != null -> 1 // Numbers come after text
            num2 != null -> -1 // Text comes before numbers
            else -> compareQualifiers(part1, part2)
        }
    }

    /**
     * Compares qualifiers such as `Beta1` or `RC10` chunk by chunk, numeric chunks as numbers.
     */
    private fun compareQualifiers(qualifier1: String, qualifier2: String): Int {
        val chunks1 = QUALIFIER_CHUNK_REGEX.findAll(qualifier1).map { it.value }.toList()
        val chunks2 = QUALIFIER_CHUNK_REGEX.findAll(qualifier2).map { it.value }.toList()

        for (i in 0 until maxOf(chunks1.size, chunks2.size)) {
            val chunk1 = chunks1.getOrNull(i) ?: return -1
            val chunk2 = chunks2.getOrNull(i) ?: return 1
            val num1 = chunk1.toBigIntegerOrNull()
            val num2 = chunk2.toBigIntegerOrNull()
            val comparison = if (num1 != null && num2 != null) {
                num1.compareTo(num2)
            } else {
                chunk1.compareTo(chunk2, ignoreCase = true)
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