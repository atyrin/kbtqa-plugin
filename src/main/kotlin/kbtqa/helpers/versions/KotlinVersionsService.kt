package kbtqa.helpers.versions

import com.intellij.openapi.components.Service
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Service that retrieves available Kotlin versions from different channels (repositories).
 */
@Service(Service.Level.APP)
class KotlinVersionsService : BaseVersionsService() {

    companion object {
        private const val MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
        private const val DEV_REPO = "https://packages.jetbrains.team/maven/p/kt/dev"
        private const val EXPERIMENTAL_REPO = "https://packages.jetbrains.team/maven/p/kt/experimental"
        private const val DEV_REPOSITORY_URL = "https://redirector.kotlinlang.org/maven/dev"
        private const val EXPERIMENTAL_REPOSITORY_URL = "https://redirector.kotlinlang.org/maven/experimental"

        private const val GRADLE_PLUGIN_METADATA = "org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml"
        private const val MAVEN_PLUGIN_METADATA = "org/jetbrains/kotlin/kotlin-maven-plugin/maven-metadata.xml"
    }

    override val toolName: String = "Kotlin"

    /**
     * Retrieves versions from all channels.
     */
    override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> {
        return listOf(
            VersionsService.VersionChannel(
                "Dev", "Development versions from JetBrains repository",
                getVersionsFromUrl("$DEV_REPO/$GRADLE_PLUGIN_METADATA"),
                repositoryUrl = DEV_REPOSITORY_URL,
                useColumnsLayout = true
            ),
            VersionsService.VersionChannel(
                "Experimental", "Experimental versions from JetBrains repository",
                getVersionsFromUrl("$EXPERIMENTAL_REPO/$GRADLE_PLUGIN_METADATA"),
                repositoryUrl = EXPERIMENTAL_REPOSITORY_URL,
                useColumnsLayout = true
            ),
            VersionsService.VersionChannel(
                "Stable", "Stable releases from Maven Central",
                getVersionsFromUrl("$MAVEN_CENTRAL/$GRADLE_PLUGIN_METADATA")
            )
        )
    }

    /**
     * Versions of `kotlin-maven-plugin`, the artifact `kotlin.version` sets in a pom, from the dev and
     * experimental repositories and Maven Central, in that order, each newest first. A repository that
     * cannot be reached contributes an empty channel.
     */
    suspend fun getMavenPluginVersionChannels(): List<VersionsService.VersionChannel> = coroutineScope {
        val dev = async { getVersionsFromUrl("$DEV_REPO/$MAVEN_PLUGIN_METADATA") }
        val experimental = async { getVersionsFromUrl("$EXPERIMENTAL_REPO/$MAVEN_PLUGIN_METADATA") }
        val central = async { getVersionsFromUrl("$MAVEN_CENTRAL/$MAVEN_PLUGIN_METADATA") }
        listOf(
            VersionsService.VersionChannel("dev", "Kotlin dev repository", dev.await(), repositoryUrl = DEV_REPOSITORY_URL),
            VersionsService.VersionChannel(
                "experimental", "Kotlin experimental repository", experimental.await(),
                repositoryUrl = EXPERIMENTAL_REPOSITORY_URL
            ),
            VersionsService.VersionChannel("Maven Central", "Maven Central", central.await()),
        )
    }
}
