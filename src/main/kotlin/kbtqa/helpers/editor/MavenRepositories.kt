package kbtqa.helpers.editor

/**
 * A repository declared in a `pom.xml`.
 *
 * @param id the `<id>` value, used by Maven to key mirrors and credentials
 * @param url the `<url>` value
 */
data class MavenRepository(val id: String, val url: String) {

    /** The XML snippet for this repository, wrapped in [wrapperTag]. */
    fun tagText(wrapperTag: String): String = "<$wrapperTag><id>$id</id><url>$url</url></$wrapperTag>"
}

/**
 * Catalog of the Kotlin repositories offered by [ConfigureMavenRepositoriesAction], plus the pure
 * helpers it needs. Free of IntelliJ PSI so it can be unit-tested headlessly.
 *
 * The URLs match the ones [ConfigureRepositoriesAction] writes into Gradle build files, so a
 * project configured either way points at the same places.
 */
object MavenRepositories {

    const val REPOSITORIES_TAG = "repositories"
    const val REPOSITORY_TAG = "repository"
    const val PLUGIN_REPOSITORIES_TAG = "pluginRepositories"
    const val PLUGIN_REPOSITORY_TAG = "pluginRepository"
    const val URL_TAG = "url"

    /** `<repositories>` reads best right after `<properties>`, else after the coordinates. */
    val REPOSITORIES_ANCHORS = listOf(MavenProperties.PROPERTIES_TAG) + MavenProperties.PROPERTIES_ANCHORS

    /** `<pluginRepositories>` follows `<repositories>` when that one is already there. */
    val PLUGIN_REPOSITORIES_ANCHORS = listOf(REPOSITORIES_TAG) + REPOSITORIES_ANCHORS

    val ENTRIES: List<MavenRepository> = listOf(
        MavenRepository("dev", "https://redirector.kotlinlang.org/maven/dev"),
        MavenRepository("bootstrap", "https://redirector.kotlinlang.org/maven/bootstrap"),
        MavenRepository("experimental", "https://redirector.kotlinlang.org/maven/experimental"),
    )

    /** Trailing slashes are not significant, so they must not make a repository look like a new one. */
    fun normalizeUrl(url: String): String = url.trim().removeSuffix("/")

    /** The entries from [ENTRIES] whose URL is not among [existingUrls] yet. */
    fun missingFrom(existingUrls: Collection<String>): List<MavenRepository> {
        val known = existingUrls.map { normalizeUrl(it) }.toSet()
        return ENTRIES.filter { normalizeUrl(it.url) !in known }
    }
}
