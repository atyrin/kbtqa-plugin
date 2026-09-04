package kbtqa.helpers.editor

/**
 * A Kotlin compiler property that can be declared in the `<properties>` section of a `pom.xml`.
 *
 * @param name the property name, e.g. `kotlin.compiler.jvmTarget`
 * @param value the value to pre-fill; empty means the user has to type it
 */
data class MavenProperty(val name: String, val value: String = "") {

    /** Text shown in the chooser popup. */
    val label: String
        get() = if (value.isEmpty()) name else "$name ($value)"

    /** The XML snippet inserted into `<properties>`. */
    val tagText: String
        get() = "<$name>$value</$name>"
}

/**
 * Catalog of the Kotlin Maven compiler properties offered by [MavenPropertiesAction], plus the pure
 * helpers it needs. Everything here is free of IntelliJ PSI so it can be unit-tested headlessly.
 *
 * The property names come from `@Parameter(property = "...")` declarations in the Kotlin Maven
 * plugin (`K2JVMCompileMojo` and `KotlinCompileMojoBase`).
 */
object MavenProperties {

    const val POM_XML = "pom.xml"
    const val PROPERTIES_TAG = "properties"

    /**
     * `<project>` children after which a newly created `<properties>` reads best, most specific
     * first. This keeps the block above `<dependencies>`/`<build>`, where people expect it.
     */
    val PROPERTIES_ANCHORS = listOf("packaging", "version", "artifactId", "groupId", "parent", "modelVersion")

    /** Properties grouped as they are shown in the popup; groups are drawn with a separator between them. */
    val GROUPS: List<List<MavenProperty>> = listOf(
        // Kotlin Maven plugin
        listOf(
            MavenProperty("kotlin.compiler.jvmTarget"),
            MavenProperty("kotlin.compiler.languageVersion"),
            MavenProperty("kotlin.compiler.apiVersion"),
            MavenProperty("kotlin.compiler.jdkRelease"),
            MavenProperty("kotlin.compiler.jdkHome"),
            MavenProperty("kotlin.compiler.daemon", "true"),
            MavenProperty("kotlin.compiler.incremental", "true"),
        ),
        // Maven itself, not Kotlin-specific
        listOf(
            MavenProperty("maven.compiler.release"),
            MavenProperty("maven.compiler.target"),
        ),
    )

    val ENTRIES: List<MavenProperty> = GROUPS.flatten()

    /** Entries that open a new group and therefore get a separator line above them in the popup. */
    val ENTRIES_WITH_SEPARATOR_ABOVE: Set<MavenProperty> =
        GROUPS.drop(1).mapNotNull { it.firstOrNull() }.toSet()

    fun isPomFile(fileName: String?): Boolean = fileName == POM_XML

    /**
     * Offset inside [tagText] where the caret should land: the end of the value, or right between
     * the tags when the property has no value. Degrades to "after the opening tag" for a
     * self-closed tag.
     */
    fun caretOffsetInTagText(tagText: String): Int {
        val afterOpen = tagText.indexOf('>') + 1
        val closeStart = tagText.lastIndexOf("</")
        return if (closeStart > afterOpen) closeStart else afterOpen
    }

    /**
     * Name of the `<project>` child after which a new section belongs, picked as the first of
     * [anchors] that is actually present, or `null` when none is and the section should become the
     * first child.
     */
    fun anchorName(projectChildNames: List<String>, anchors: List<String>): String? =
        anchors.firstOrNull { it in projectChildNames }
}
