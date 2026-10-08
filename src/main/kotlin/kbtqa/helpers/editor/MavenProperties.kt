package kbtqa.helpers.editor

/** An entry of the _Add Maven Property_ popup. */
sealed interface MavenOption {

    /** Text shown in the chooser popup. */
    val label: String
}

/**
 * A property declared in the `<properties>` section of a `pom.xml`.
 *
 * @param name the property name, e.g. `kotlin.compiler.jvmTarget`
 * @param value the value to pre-fill; empty means the user has to type it
 */
data class MavenProperty(val name: String, val value: String = "") : MavenOption {

    override val label: String
        get() = name

    /** The XML snippet inserted into `<properties>`. */
    val tagText: String
        get() = "<$name>$value</$name>"
}

/**
 * A Kotlin compiler option that `kotlin-maven-plugin` takes only in its `<configuration>`, with no
 * property counterpart.
 *
 * @param tagText the tag to insert
 * @param tagName the name of [tagText]'s root tag
 * @param container a `<configuration>` child the tag goes into, such as `args`; `null` for a direct child
 * @param presentPrefix for a repeatable tag like `<arg>`: an existing one whose text starts with this
 *   already sets the option; `null` when any tag named [tagName] does
 * @param caretTag a child of the inserted tag that takes the caret, such as `version` of `<jdkToolchain>`
 */
data class KotlinPluginOption(
    override val label: String,
    val tagText: String,
    val tagName: String,
    val container: String? = null,
    val presentPrefix: String? = null,
    val caretTag: String? = null,
) : MavenOption {

    /** Whether an existing tag named [tagName] with [existingText] already sets this option. */
    fun isSetBy(existingText: String): Boolean =
        presentPrefix == null || existingText.trim().startsWith(presentPrefix)
}

/** A titled section of the popup. */
data class MavenOptionGroup(val title: String, val options: List<MavenOption>)

/**
 * Catalog of the options offered by [MavenPropertiesAction], plus the pure helpers it needs.
 * Everything here is plain data and string logic; the PSI work lives in the action.
 *
 * The groups follow the Kotlin docs: compiler options from "Configure Kotlin compiler for your Maven
 * project", the execution strategy and incremental compilation from the same page, and the JVM target
 * alignment of smart defaults. Names and property names come from the `@Parameter` declarations of
 * `kotlin-maven-plugin` (`K2JVMCompileMojo`, `KotlinCompileMojoBase`). The popup shows names only;
 * the values to pre-fill are the non-default ones, or examples where there is no obvious one.
 */
object MavenProperties {

    const val POM_XML = "pom.xml"
    const val PROPERTIES_TAG = "properties"

    /**
     * `<project>` children after which a newly created `<properties>` reads best, most specific
     * first. This keeps the block above `<dependencies>`/`<build>`, where people expect it.
     */
    val PROPERTIES_ANCHORS = listOf("packaging", "version", "artifactId", "groupId", "parent", "modelVersion")

    val GROUPS: List<MavenOptionGroup> = listOf(
        // Compiler options that have a property counterpart; they could also go into <configuration>
        MavenOptionGroup(
            "Kotlin compiler",
            listOf(
                MavenProperty("kotlin.compiler.languageVersion"),
                MavenProperty("kotlin.compiler.apiVersion"),
                MavenProperty("kotlin.compiler.jvmTarget"),
                MavenProperty("kotlin.compiler.jdkRelease"),
                MavenProperty("kotlin.compiler.jdkHome"),
                MavenProperty("kotlin.compiler.javaParameters", "true"),
            )
        ),
        // How Maven runs the compiler rather than what it compiles; true is the default for the daemon
        // and smart defaults, so only false changes anything
        MavenOptionGroup(
            "Kotlin build",
            listOf(
                MavenProperty("kotlin.compiler.daemon", "false"),
                // Comma-separated and without the leading dash: the plugin hands them to the Build Tools
                // API as they are, which prepends one, so -Xmx3g would reach the JVM as --Xmx3g
                MavenProperty("kotlin.compiler.daemon.jvmArgs", "Xmx3g,Xms1g"),
                // The default is 30 minutes, or 1 second under mvnd
                MavenProperty("kotlin.compiler.daemon.shutdownDelayMs", "600000"),
                MavenProperty("kotlin.compiler.incremental", "true"),
                MavenProperty("kotlin.smart.defaults.enabled", "false"),
            )
        ),
        // Not Kotlin-specific, but smart defaults derive the Kotlin JVM target from them
        MavenOptionGroup(
            "Maven compiler",
            listOf(
                MavenProperty("maven.compiler.release"),
                MavenProperty("maven.compiler.target"),
            )
        ),
        MavenOptionGroup(
            "kotlin-maven-plugin <configuration>",
            listOf(
                KotlinPluginOption("nowarn", "<nowarn>true</nowarn>", "nowarn"),
                // Compiler arguments the plugin has no parameter for, the most used ones in the Kotlin docs
                KotlinPluginOption("args: -Werror", "<arg>-Werror</arg>", "arg", container = "args", presentPrefix = "-Werror"),
                KotlinPluginOption("args: -Wextra", "<arg>-Wextra</arg>", "arg", container = "args", presentPrefix = "-Wextra"),
                KotlinPluginOption(
                    "args: -progressive", "<arg>-progressive</arg>", "arg",
                    container = "args", presentPrefix = "-progressive"
                ),
                // A real stdlib marker rather than the docs' made-up one: an unresolved marker is a warning,
                // which -Werror turns into a failed build. Repeatable, one marker each, so only this very
                // marker counts as already set
                KotlinPluginOption(
                    "args: -opt-in", "<arg>-opt-in=kotlin.ExperimentalStdlibApi</arg>", "arg",
                    container = "args", presentPrefix = "-opt-in=kotlin.ExperimentalStdlibApi"
                ),
                KotlinPluginOption(
                    "args: -Xjsr305", "<arg>-Xjsr305=strict</arg>", "arg",
                    container = "args", presentPrefix = "-Xjsr305"
                ),
                // A map in the mojo: there is no kotlin.compiler.jdkToolchain property, whatever the docs say
                KotlinPluginOption(
                    "jdkToolchain", "<jdkToolchain><version></version></jdkToolchain>", "jdkToolchain",
                    caretTag = "version"
                ),
            )
        ),
    )

    val ENTRIES: List<MavenOption> = GROUPS.flatMap { it.options }

    /** The title of the group each option belongs to. */
    val GROUP_TITLES: Map<MavenOption, String> = GROUPS.flatMap { group -> group.options.map { it to group.title } }.toMap()

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
