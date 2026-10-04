package kbtqa.helpers.editor

/**
 * Shared source set groups of the Kotlin default hierarchy template.
 * The declaration order defines the display order of sibling groups.
 *
 * @param sourceSetPrefix Prefix of the group's source sets, e.g. `apple` for `appleMain`/`appleTest`
 * @param parent The group this group depends on; `null` only for [COMMON]
 * @param displayName Human-readable name used in the UI
 */
enum class KmpGroup(val sourceSetPrefix: String, val parent: KmpGroup?, val displayName: String) {
    COMMON("common", null, "Common"),
    WEB("web", COMMON, "Web"),
    NATIVE("native", COMMON, "Native"),
    APPLE("apple", NATIVE, "Apple"),
    IOS("ios", APPLE, "iOS"),
    MACOS("macos", APPLE, "macOS"),
    TVOS("tvos", APPLE, "tvOS"),
    WATCHOS("watchos", APPLE, "watchOS"),
    LINUX("linux", NATIVE, "Linux"),
    MINGW("mingw", NATIVE, "Windows (MinGW)"),
    ANDROID_NATIVE("androidNative", NATIVE, "Android Native");

    val mainSourceSet: String get() = "${sourceSetPrefix}Main"
    val testSourceSet: String get() = "${sourceSetPrefix}Test"

    /** This group followed by all its ancestors up to [COMMON]. */
    val withAncestors: List<KmpGroup> get() = generateSequence(this) { it.parent }.toList()
}

/**
 * KMP target presets that can be declared in the `kotlin {}` block.
 *
 * @param dslNames Function names declaring the target; the first one is the canonical name
 * @param defaultName Target name used when no explicit name is given (source sets are `<name>Main`/`<name>Test`)
 * @param group Shared group of the default hierarchy template the target belongs to
 * @param requiresBlock Whether the declaration is only recognised when followed by a configuration block
 */
enum class KmpTargetPreset(
    val dslNames: List<String>,
    val defaultName: String,
    val group: KmpGroup,
    val requiresBlock: Boolean = false
) {
    JVM(listOf("jvm"), "jvm", KmpGroup.COMMON),
    ANDROID_TARGET(listOf("androidTarget"), "android", KmpGroup.COMMON),
    /** Target of the `com.android.kotlin.multiplatform.library` plugin: `android {}` (AGP 9+) or `androidLibrary {}`. */
    ANDROID_LIBRARY(listOf("android", "androidLibrary"), "android", KmpGroup.COMMON, requiresBlock = true),
    JS(listOf("js"), "js", KmpGroup.WEB),
    WASM_JS(listOf("wasmJs"), "wasmJs", KmpGroup.WEB),
    WASM_WASI(listOf("wasmWasi"), "wasmWasi", KmpGroup.COMMON),
    IOS_X64(listOf("iosX64"), "iosX64", KmpGroup.IOS),
    IOS_ARM64(listOf("iosArm64"), "iosArm64", KmpGroup.IOS),
    IOS_SIMULATOR_ARM64(listOf("iosSimulatorArm64"), "iosSimulatorArm64", KmpGroup.IOS),
    MACOS_X64(listOf("macosX64"), "macosX64", KmpGroup.MACOS),
    MACOS_ARM64(listOf("macosArm64"), "macosArm64", KmpGroup.MACOS),
    TVOS_X64(listOf("tvosX64"), "tvosX64", KmpGroup.TVOS),
    TVOS_ARM64(listOf("tvosArm64"), "tvosArm64", KmpGroup.TVOS),
    TVOS_SIMULATOR_ARM64(listOf("tvosSimulatorArm64"), "tvosSimulatorArm64", KmpGroup.TVOS),
    WATCHOS_X64(listOf("watchosX64"), "watchosX64", KmpGroup.WATCHOS),
    WATCHOS_ARM32(listOf("watchosArm32"), "watchosArm32", KmpGroup.WATCHOS),
    WATCHOS_ARM64(listOf("watchosArm64"), "watchosArm64", KmpGroup.WATCHOS),
    WATCHOS_SIMULATOR_ARM64(listOf("watchosSimulatorArm64"), "watchosSimulatorArm64", KmpGroup.WATCHOS),
    WATCHOS_DEVICE_ARM64(listOf("watchosDeviceArm64"), "watchosDeviceArm64", KmpGroup.WATCHOS),
    LINUX_X64(listOf("linuxX64"), "linuxX64", KmpGroup.LINUX),
    LINUX_ARM64(listOf("linuxArm64"), "linuxArm64", KmpGroup.LINUX),
    MINGW_X64(listOf("mingwX64"), "mingwX64", KmpGroup.MINGW),
    ANDROID_NATIVE_ARM32(listOf("androidNativeArm32"), "androidNativeArm32", KmpGroup.ANDROID_NATIVE),
    ANDROID_NATIVE_ARM64(listOf("androidNativeArm64"), "androidNativeArm64", KmpGroup.ANDROID_NATIVE),
    ANDROID_NATIVE_X86(listOf("androidNativeX86"), "androidNativeX86", KmpGroup.ANDROID_NATIVE),
    ANDROID_NATIVE_X64(listOf("androidNativeX64"), "androidNativeX64", KmpGroup.ANDROID_NATIVE);

    /** Canonical DSL declaration, e.g. `jvm()` or `android {}`. */
    val dslDeclaration: String get() = if (requiresBlock) "${dslNames.first()} {}" else "${dslNames.first()}()"

    /**
     * Heading of the target in the UI. Targets placed directly under `commonMain` have no shared group,
     * so they get their own headings instead of [group]'s name.
     */
    val section: String
        get() = when (this) {
            JVM, ANDROID_TARGET, ANDROID_LIBRARY -> "JVM & Android"
            WASM_WASI -> "WASI"
            else -> group.displayName
        }

    /**
     * Test source sets created for a target of this preset. The Android KMP library target only has
     * host/device tests when `withHostTest`/`withDeviceTest` are among its [configurationCalls].
     */
    fun testSourceSets(targetName: String, configurationCalls: Set<String>): List<String> = when (this) {
        ANDROID_TARGET -> listOf("${targetName}UnitTest", "${targetName}InstrumentedTest")
        ANDROID_LIBRARY -> buildList {
            if ("withHostTest" in configurationCalls) add("${targetName}HostTest")
            if ("withDeviceTest" in configurationCalls) add("${targetName}DeviceTest")
        }
        else -> listOf("${targetName}Test")
    }
}

/**
 * A KMP target declared in (or selected for) a build script.
 *
 * @param name Target name; differs from [KmpTargetPreset.defaultName] for e.g. `jvm("desktop")`
 * @param configurationCalls Names of the functions called in the target's configuration block
 */
data class KmpTarget(
    val preset: KmpTargetPreset,
    val name: String = preset.defaultName,
    val configurationCalls: Set<String> = emptySet()
) {
    val mainSourceSet: String get() = "${name}Main"
    val testSourceSets: List<String> get() = preset.testSourceSets(name, configurationCalls)
}

/**
 * Result of reading a build script.
 *
 * @param kotlinBlockFound Whether a `kotlin {}` block was found at all
 * @param targets Targets declared in the `kotlin {}` block, in declaration order
 * @param customSourceSets Source sets explicitly created in `kotlin { sourceSets {} }`
 */
data class KmpBuildScriptInfo(
    val kotlinBlockFound: Boolean,
    val targets: List<KmpTarget>,
    val customSourceSets: List<String>
)

/**
 * A source set to be created, with its depth in the default hierarchy tree (for display).
 */
data class PlannedSourceSet(val name: String, val depth: Int, val isTest: Boolean) {
    /** Name of the class generated in this source set, e.g. `IosArm64Main`. */
    val className: String get() = name.replaceFirstChar { it.uppercaseChar() }

    /** Path of the generated class relative to the module directory. */
    fun relativeFilePath(packageName: String): String {
        val packagePath = if (packageName.isEmpty()) "" else packageName.replace('.', '/') + "/"
        return "src/$name/kotlin/$packagePath$className.kt"
    }

    /** Content of the generated class file. */
    fun fileContent(packageName: String): String = buildString {
        if (packageName.isNotEmpty()) append("package ").append(packageName).append("\n\n")
        append("class ").append(className).append('\n')
    }
}

/**
 * Computes the source sets of a KMP module following the Kotlin default hierarchy template:
 * `commonMain` → `webMain`/`nativeMain` → `appleMain`/`linuxMain`/… → `iosMain`/… → per-target source sets.
 * A shared group is included only when at least one of the given targets belongs to it.
 */
object KmpSourceSetPlanner {

    /**
     * Returns the source sets to create: the main tree first, then the test tree, then [customSourceSets].
     * Duplicate names (e.g. two targets with the same name) are kept only once.
     */
    fun plan(
        targets: List<KmpTarget>,
        customSourceSets: List<String> = emptyList(),
        includeTests: Boolean = true
    ): List<PlannedSourceSet> {
        if (targets.isEmpty() && customSourceSets.isEmpty()) return emptyList()

        val usedGroups = targets.flatMapTo(mutableSetOf(KmpGroup.COMMON)) { it.preset.group.withAncestors }
        val result = LinkedHashMap<String, PlannedSourceSet>()
        fun add(name: String, depth: Int, isTest: Boolean) {
            result.putIfAbsent(name, PlannedSourceSet(name, depth, isTest))
        }

        fun addGroup(group: KmpGroup, depth: Int, isTest: Boolean) {
            add(if (isTest) group.testSourceSet else group.mainSourceSet, depth, isTest)
            targets.filter { it.preset.group == group }.forEach { target ->
                val names = if (isTest) target.testSourceSets else listOf(target.mainSourceSet)
                names.forEach { add(it, depth + 1, isTest) }
            }
            KmpGroup.entries
                .filter { it.parent == group && it in usedGroups }
                .forEach { addGroup(it, depth + 1, isTest) }
        }

        addGroup(KmpGroup.COMMON, 0, isTest = false)
        if (includeTests) addGroup(KmpGroup.COMMON, 0, isTest = true)
        customSourceSets.forEach { name ->
            val isTest = name.endsWith("Test")
            if (!isTest || includeTests) add(name, 1, isTest)
        }
        return result.values.toList()
    }
}
