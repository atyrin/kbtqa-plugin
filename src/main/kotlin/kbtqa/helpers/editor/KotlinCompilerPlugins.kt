package kbtqa.helpers.editor

/**
 * A Kotlin compiler plugin enabled through `kotlin-maven-plugin`: its name in `<compilerPlugins>`, the
 * artifact that has to be a dependency of `kotlin-maven-plugin` for that name to resolve, and an
 * example `<pluginOptions>` entry for plugins that do nothing without one.
 *
 * @param name the plugin name, e.g. `all-open`; a preset such as `spring` counts as a name too
 * @param artifactId the `org.jetbrains.kotlin` artifact providing the plugin
 * @param exampleOption `key=value` for `<option>name:key=value</option>`, or `null` for none
 */
data class KotlinCompilerPlugin(
    val name: String,
    val artifactId: String,
    val exampleOption: String? = null,
) {

    /** The `<option>` text, prefixed with the plugin name as `kotlin-maven-plugin` requires. */
    val optionText: String?
        get() = exampleOption?.let { "$name:$it" }

    /** Whether an existing `<option>` already sets the example option's key, whatever its value. */
    fun isOptionSetBy(existingOption: String): Boolean =
        exampleOption != null && existingOption.trim().startsWith("$name:${exampleOption.substringBefore('=')}=")
}

/**
 * Catalog of the compiler plugins offered by [AddKotlinCompilerPluginAction]: the ones the Kotlin docs
 * describe for Maven. Names are the hints the plugins' Maven extensions register under; the example
 * options are the ones from the docs.
 */
object KotlinCompilerPlugins {

    val ENTRIES: List<KotlinCompilerPlugin> = listOf(
        // A preset of all-open for Spring's annotations, so it needs no option
        KotlinCompilerPlugin("spring", "kotlin-maven-allopen"),
        KotlinCompilerPlugin("all-open", "kotlin-maven-allopen", "annotation=com.my.Annotation"),
        KotlinCompilerPlugin("no-arg", "kotlin-maven-noarg", "annotation=com.my.Annotation"),
        // Its only option points to a lombok.config, which the project may not have
        KotlinCompilerPlugin("lombok", "kotlin-maven-lombok"),
        KotlinCompilerPlugin("kotlinx-serialization", "kotlin-maven-serialization"),
        KotlinCompilerPlugin("sam-with-receiver", "kotlin-maven-sam-with-receiver", "annotation=com.my.SamWithReceiver"),
        // The Maven extension sets no functions, unlike the Gradle one, so without this nothing is transformed
        KotlinCompilerPlugin("power-assert", "kotlin-maven-power-assert", "function=kotlin.assert"),
    )
}
