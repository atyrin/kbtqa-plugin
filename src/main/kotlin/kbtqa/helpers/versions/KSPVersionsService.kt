package kbtqa.helpers.versions

import com.intellij.openapi.components.Service

/**
 * Service that retrieves available KSP (Kotlin Symbol Processing) versions from Maven Central repository.
 */
@Service
class KSPVersionsService : BaseVersionsService() {

    companion object {
        private const val MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml"
    }

    override val toolName: String = "KSP"

    /**
     * Retrieves versions from Maven Central repository.
     */
    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        val versions = getVersionsFromUrl(MAVEN_CENTRAL_URL)
        
        val channels = mutableListOf<VersionsService.VersionChannel>()
        
        // Add an "All" channel with all versions
        if (versions.isNotEmpty()) {
            channels.add(
                VersionsService.VersionChannel(
                    "All",
                    "All available versions of KSP (Kotlin Symbol Processing) from Maven Central",
                    versions
                )
            )
        }
        
        return channels
    }
}