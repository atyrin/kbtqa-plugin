package kbtqa.helpers.editor

/** How the Kotlin Maven plugin is wired into the build — the three setups the Kotlin docs describe. */
enum class KotlinPluginMode(val label: String) {
    /** `<extensions>true</extensions>`: the plugin sets up source roots, stdlib and executions itself. */
    SMART_DEFAULTS("Smart defaults (<extensions>)"),

    /** Kotlin sources only: Maven's source directories point at the Kotlin ones, plus two executions. */
    KOTLIN_ONLY("Manual: Kotlin only"),

    /** Mixed sources: Kotlin compiles first, then the Maven compiler with its default executions disabled. */
    KOTLIN_AND_JAVA("Manual: Kotlin + Java"),
}

/**
 * Recipes and rules for configuring the Kotlin Maven plugin, used by [ConfigureKotlinPluginAction].
 * Plain data and string logic; the PSI work lives in the action.
 *
 * Each [KotlinPluginMode] owns a fixed set of elements and rewrites only those, so switching modes
 * back and forth leaves the rest of the pom alone:
 * - `<extensions>` of the Kotlin plugin;
 * - Kotlin plugin executions whose goals are only `compile`/`test-compile` (kapt ones are kept);
 * - Maven compiler executions with the ids the Kotlin + Java recipe uses;
 * - `<sourceDirectory>`/`<testSourceDirectory>` when they point at the Kotlin directories.
 *
 * Settings of a replaced execution other than `sourceDirs`, such as compiler `args`, move up to the
 * plugin's own `<configuration>` instead of being lost; one already set there is kept.
 */
object MavenKotlinPlugin {

    const val GROUP_ID = "org.jetbrains.kotlin"
    const val ARTIFACT_ID = "kotlin-maven-plugin"
    const val STDLIB_ARTIFACT_ID = "kotlin-stdlib"

    const val APACHE_PLUGINS_GROUP_ID = "org.apache.maven.plugins"
    const val COMPILER_PLUGIN_ARTIFACT_ID = "maven-compiler-plugin"

    /**
     * Used when the Kotlin + Java setup has to declare the Maven compiler itself and the latest
     * version cannot be looked up: the newest stable 3.x release at the time of writing.
     */
    const val COMPILER_PLUGIN_FALLBACK_VERSION = "3.16.0"

    /**
     * Shown by the actions that edit the Kotlin plugin when the pom does not declare it; they do not
     * declare it themselves, since that means picking a setup.
     */
    const val NOT_DECLARED_HINT =
        "kotlin-maven-plugin is not declared in <build><plugins> yet. Set it up with Configure Kotlin Plugin first."

    const val BUILD_TAG = "build"
    const val PLUGINS_TAG = "plugins"
    const val PLUGIN_TAG = "plugin"
    const val GROUP_ID_TAG = "groupId"
    const val ARTIFACT_ID_TAG = "artifactId"
    const val VERSION_TAG = "version"
    const val EXTENSIONS_TAG = "extensions"
    const val EXECUTIONS_TAG = "executions"
    const val CONFIGURATION_TAG = "configuration"

    /** The one execution setting the modes manage themselves; everything else is the user's. */
    const val SOURCE_DIRS_TAG = "sourceDirs"
    const val COMPILER_PLUGINS_TAG = "compilerPlugins"
    const val PLUGIN_OPTIONS_TAG = "pluginOptions"
    const val OPTION_TAG = "option"
    const val ID_TAG = "id"
    const val GOALS_TAG = "goals"
    const val GOAL_TAG = "goal"
    const val PARENT_TAG = "parent"
    const val DEPENDENCIES_TAG = "dependencies"
    const val DEPENDENCY_TAG = "dependency"
    const val SOURCE_DIRECTORY_TAG = "sourceDirectory"
    const val TEST_SOURCE_DIRECTORY_TAG = "testSourceDirectory"

    const val KOTLIN_VERSION_PROPERTY = "kotlin.version"
    const val KOTLIN_VERSION_REFERENCE = "\${$KOTLIN_VERSION_PROPERTY}"

    const val EXTENSIONS_TAG_TEXT = "<$EXTENSIONS_TAG>true</$EXTENSIONS_TAG>"

    /** `<plugin>` children follow the Maven model order: groupId, artifactId, version, extensions, executions, … */
    val EXTENSIONS_ANCHORS = listOf(VERSION_TAG, ARTIFACT_ID_TAG)
    val EXECUTIONS_ANCHORS = listOf(EXTENSIONS_TAG, VERSION_TAG, ARTIFACT_ID_TAG)
    val PLUGIN_DEPENDENCIES_ANCHORS = listOf(EXECUTIONS_TAG) + EXECUTIONS_ANCHORS

    /** A new `<dependencies>` reads best after the repository sections, a new `<build>` after the dependencies. */
    val DEPENDENCIES_ANCHORS =
        listOf("dependencyManagement", MavenRepositories.PLUGIN_REPOSITORIES_TAG) + MavenRepositories.PLUGIN_REPOSITORIES_ANCHORS
    val BUILD_ANCHORS = listOf(DEPENDENCIES_TAG) + DEPENDENCIES_ANCHORS

    val KOTLIN_PLUGIN_TAG_TEXT = pluginTagText(GROUP_ID, ARTIFACT_ID, KOTLIN_VERSION_REFERENCE)

    private val OWNED_KOTLIN_GOALS = setOf("compile", "test-compile")
    private val OWNED_COMPILER_EXECUTION_IDS = setOf("default-compile", "default-testCompile", "java-compile", "java-test-compile")
    private val KOTLIN_SOURCE_DIRECTORIES = setOf("src/main/kotlin", "src/test/kotlin")

    fun usesExtensions(mode: KotlinPluginMode): Boolean = mode == KotlinPluginMode.SMART_DEFAULTS

    /** Smart defaults add kotlin-stdlib on their own; the manual setups have to declare it. */
    fun requiresStdlib(mode: KotlinPluginMode): Boolean = mode != KotlinPluginMode.SMART_DEFAULTS

    /** The Kotlin plugin executions of [mode], as XML snippets. */
    fun kotlinExecutions(mode: KotlinPluginMode): List<String> = when (mode) {
        KotlinPluginMode.SMART_DEFAULTS -> emptyList()
        KotlinPluginMode.KOTLIN_ONLY -> listOf(
            execution("compile", phase = null, goal = "compile"),
            execution("test-compile", phase = null, goal = "test-compile"),
        )
        KotlinPluginMode.KOTLIN_AND_JAVA -> listOf(
            execution("kotlin-compile", "compile", "compile", sourceDirsConfiguration("main")),
            execution("kotlin-test-compile", "test-compile", "test-compile", sourceDirsConfiguration("test")),
        )
    }

    /** The Maven compiler executions of [mode], as XML snippets. */
    fun compilerExecutions(mode: KotlinPluginMode): List<String> =
        if (mode != KotlinPluginMode.KOTLIN_AND_JAVA) emptyList() else listOf(
            disabledExecution("default-compile"),
            disabledExecution("default-testCompile"),
            execution("java-compile", "compile", "compile"),
            execution("java-test-compile", "test-compile", "testCompile"),
        )

    /** `<build>` source directories of [mode] as tag name to value. */
    fun sourceDirectories(mode: KotlinPluginMode): List<Pair<String, String>> =
        if (mode != KotlinPluginMode.KOTLIN_ONLY) emptyList() else listOf(
            SOURCE_DIRECTORY_TAG to "src/main/kotlin",
            TEST_SOURCE_DIRECTORY_TAG to "src/test/kotlin",
        )

    fun compilerPluginTagText(version: String): String =
        pluginTagText(APACHE_PLUGINS_GROUP_ID, COMPILER_PLUGIN_ARTIFACT_ID, version)

    fun stdlibTagText(version: String): String = kotlinDependencyTagText(STDLIB_ARTIFACT_ID, version)

    /** A `<dependency>` on the `org.jetbrains.kotlin` artifact [artifactId]. */
    fun kotlinDependencyTagText(artifactId: String, version: String): String =
        "<$DEPENDENCY_TAG>" +
            "<$GROUP_ID_TAG>$GROUP_ID</$GROUP_ID_TAG>" +
            "<$ARTIFACT_ID_TAG>$artifactId</$ARTIFACT_ID_TAG>" +
            "<$VERSION_TAG>$version</$VERSION_TAG>" +
            "</$DEPENDENCY_TAG>"

    /**
     * Version for an added Kotlin artifact that has to match the compiler — kotlin-stdlib, or a
     * compiler plugin: the Kotlin plugin's own version, as smart defaults do it for the stdlib, or the
     * `kotlin.version` property when the plugin has none in this pom.
     */
    fun kotlinArtifactVersion(pluginVersion: String?): String =
        pluginVersion?.trim()?.takeIf { it.isNotEmpty() } ?: KOTLIN_VERSION_REFERENCE

    fun isKotlinMavenPlugin(groupId: String?, artifactId: String?): Boolean =
        groupId?.trim() == GROUP_ID && artifactId?.trim() == ARTIFACT_ID

    fun isMavenCompilerPlugin(groupId: String?, artifactId: String?): Boolean =
        isApacheMavenPlugin(groupId, artifactId, COMPILER_PLUGIN_ARTIFACT_ID)

    /** `<groupId>` may be omitted for an Apache Maven plugin: theirs is the default plugin group. */
    fun isApacheMavenPlugin(groupId: String?, artifactId: String?, expectedArtifactId: String): Boolean =
        artifactId?.trim() == expectedArtifactId && (groupId == null || groupId.trim() == APACHE_PLUGINS_GROUP_ID)

    /** Matches exactly what smart defaults look for, so `kotlin-stdlib-jdk8` does not count. */
    fun isKotlinStdlib(groupId: String?, artifactId: String?): Boolean =
        groupId?.trim() == GROUP_ID && artifactId?.trim() == STDLIB_ARTIFACT_ID

    /** A Kotlin execution is ours when all its goals are compile goals; kapt executions are kept. */
    fun isOwnedKotlinExecution(goals: List<String>): Boolean =
        goals.isNotEmpty() && goals.all { it.trim() in OWNED_KOTLIN_GOALS }

    fun isOwnedCompilerExecution(id: String?): Boolean =
        id != null && id.trim() in OWNED_COMPILER_EXECUTION_IDS

    fun isKotlinSourceDirectory(value: String?): Boolean {
        val normalized = value?.trim()
            ?.replace('\\', '/')
            ?.removePrefix("\${project.basedir}/")
            ?.removePrefix("./")
            ?.removeSuffix("/")
            ?: return false
        return normalized in KOTLIN_SOURCE_DIRECTORIES
    }

    /**
     * Whether `kotlin.version` has to be declared because a `${kotlin.version}` reference is being
     * written. A pom with a `<parent>` most likely inherits it, and an empty override there would
     * break the build.
     */
    fun needsKotlinVersionProperty(declaresProperty: Boolean, hasParent: Boolean): Boolean =
        !declaresProperty && !hasParent

    fun pluginTagText(groupId: String, artifactId: String, version: String): String =
        "<$PLUGIN_TAG>" +
            "<$GROUP_ID_TAG>$groupId</$GROUP_ID_TAG>" +
            "<$ARTIFACT_ID_TAG>$artifactId</$ARTIFACT_ID_TAG>" +
            "<$VERSION_TAG>$version</$VERSION_TAG>" +
            "</$PLUGIN_TAG>"

    private fun execution(id: String, phase: String?, goal: String, configuration: String = ""): String =
        "<execution><$ID_TAG>$id</$ID_TAG>" +
            (phase?.let { "<phase>$it</phase>" } ?: "") +
            "<$GOALS_TAG><$GOAL_TAG>$goal</$GOAL_TAG></$GOALS_TAG>" +
            configuration +
            "</execution>"

    private fun disabledExecution(id: String): String =
        "<execution><$ID_TAG>$id</$ID_TAG><phase>none</phase></execution>"

    /** Lets Kotlin see the Java sources of the same scope, so mixed code compiles in one pass. */
    private fun sourceDirsConfiguration(scope: String): String =
        "<configuration><sourceDirs>" +
            "<sourceDir>\${project.basedir}/src/$scope/kotlin</sourceDir>" +
            "<sourceDir>\${project.basedir}/src/$scope/java</sourceDir>" +
            "</sourceDirs></configuration>"
}
