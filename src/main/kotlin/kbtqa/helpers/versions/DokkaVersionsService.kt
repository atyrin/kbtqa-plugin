package kbtqa.helpers.versions

import com.intellij.openapi.components.Service

/**
 * Service that retrieves available Dokka Gradle Plugin versions from different channels.
 */
@Service(Service.Level.APP)
class DokkaVersionsService : BaseVersionsService() {

    companion object {
        private const val STABLE_REPO_URL = "https://repo1.maven.org/maven2/org/jetbrains/dokka/dokka-gradle-plugin/maven-metadata.xml"
        private const val DEV_REPO_URL = "https://packages.jetbrains.team/maven/p/kt/dokka-dev/org/jetbrains/dokka/dokka-gradle-plugin/maven-metadata.xml"
        private const val TEST_REPO_URL = "https://packages.jetbrains.team/maven/p/kt/dokka-test/org/jetbrains/dokka/dokka-gradle-plugin/maven-metadata.xml"
    }

    override val toolName: String = "Dokka"

    /**
     * Retrieves versions from all channels.
     */
    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        return listOf(
            VersionsService.VersionChannel(
                "Dev",
                "Development versions from JetBrains repository",
                getVersionsFromUrl(DEV_REPO_URL)
            ),
            VersionsService.VersionChannel(
                "Test",
                "Test versions from JetBrains repository",
                getVersionsFromUrl(TEST_REPO_URL)
            ),
            VersionsService.VersionChannel(
                "Stable",
                "Stable releases from Maven Central",
                getVersionsFromUrl(STABLE_REPO_URL)
            )
        )
    }
}
