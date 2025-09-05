package kbtqa.helpers.versions

import com.intellij.openapi.components.Service

/**
 * Service that retrieves available Kotlin versions from different channels (repositories).
 */
@Service(Service.Level.APP)
class KotlinVersionsService : BaseVersionsService() {

    companion object {
        private const val STABLE_REPO_URL = "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml"
        private const val DEV_REPO_URL = "https://packages.jetbrains.team/maven/p/kt/dev/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml"
        private const val EXPERIMENTAL_REPO_URL = "https://packages.jetbrains.team/maven/p/kt/experimental/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml"
    }

    override val toolName: String = "Kotlin"

    /**
     * Retrieves versions from all channels.
     */
    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        return listOf(
            VersionsService.VersionChannel("Dev", "Development versions from JetBrains repository", getVersionsFromUrl(DEV_REPO_URL)),
            VersionsService.VersionChannel("Experimental", "Experimental versions from JetBrains repository", getVersionsFromUrl(EXPERIMENTAL_REPO_URL)),
            VersionsService.VersionChannel("Stable", "Stable releases from Maven Central", getVersionsFromUrl(STABLE_REPO_URL))
        )
    }
}