package kbtqa.helpers.versions

import com.intellij.openapi.components.Service

/**
 * Service that retrieves available Android Gradle Plugin versions from Maven Google repository.
 */
@Service
class AndroidVersionsService : BaseVersionsService() {

    companion object {
        private const val MAVEN_GOOGLE_URL = "https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml"
    }

    override val toolName: String = "Android"

    /**
     * Retrieves versions from Maven Google repository.
     */
    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        val versions = getVersionsFromUrl(MAVEN_GOOGLE_URL)
        
        val channels = mutableListOf<VersionsService.VersionChannel>()
        
        // Add an "All" channel with all versions
        if (versions.isNotEmpty()) {
            channels.add(
                VersionsService.VersionChannel(
                    "All",
                    "All available versions of Android Gradle Plugin from Maven Google",
                    versions
                )
            )
        }
        
        return channels
    }
}