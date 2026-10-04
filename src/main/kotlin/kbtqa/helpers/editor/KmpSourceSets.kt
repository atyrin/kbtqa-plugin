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
    COMMON("common", null, "JVM, Android, WASI"),
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
    WASM_WASI(listOf("wasmWasi"), "wasmWasi", KmpGroup.COMMON),
    JS(listOf("js"), "js", KmpGroup.WEB),
    WASM_JS(listOf("wasmJs"), "wasmJs", KmpGroup.WEB),
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
     * Test source sets created for a target of this preset.
     * The Android KMP library target only has host/device tests when they are enabled in its [configuration].
     */
    fun testSourceSets(targetName: String, configuration: String): List<String> = when (this) {
        ANDROID_TARGET -> listOf("${targetName}UnitTest", "${targetName}InstrumentedTest")
        ANDROID_LIBRARY -> buildList {
            if (WITH_HOST_TEST.containsMatchIn(configuration)) add("${targetName}HostTest")
            if (WITH_DEVICE_TEST.containsMatchIn(configuration)) add("${targetName}DeviceTest")
        }
        else -> listOf("${targetName}Test")
    }

    private companion object {
        val WITH_HOST_TEST = Regex("""\bwithHostTest\b""")
        val WITH_DEVICE_TEST = Regex("""\bwithDeviceTest\b""")
    }
}

/**
 * A KMP target declared in (or selected for) a build script.
 *
 * @param name Target name; differs from [KmpTargetPreset.defaultName] for e.g. `jvm("desktop")`
 * @param configuration Text of the target's configuration block, empty if there is none
 */
data class KmpTarget(
    val preset: KmpTargetPreset,
    val name: String = preset.defaultName,
    val configuration: String = ""
) {
    val mainSourceSet: String get() = "${name}Main"
    val testSourceSets: List<String> get() = preset.testSourceSets(name, configuration)
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

/**
 * Text-based reader of the `kotlin {}` block of a `build.gradle.kts` script.
 *
 * It does not evaluate the script: targets are recognised by their DSL calls (`jvm()`, `iosArm64()`,
 * `js { … }`, `android { … }`, …) placed directly in the `kotlin {}` block, including calls nested in
 * expressions such as `listOf(iosX64(), iosArm64()).forEach { … }`. Comments are ignored.
 */
object KmpBuildScriptParser {

    private val presetsByDslName: Map<String, KmpTargetPreset> =
        KmpTargetPreset.entries.flatMap { preset -> preset.dslNames.map { it to preset } }.toMap()

    private val kotlinBlockRegex = Regex("""(?<![\w.])kotlin\s*\{""")
    private val sourceSetsBlockRegex = Regex("""(?<![\w.])sourceSets\s*\{""")
    private val targetCallRegex =
        Regex("""(?<![\w.])(${presetsByDslName.keys.joinToString("|")})\b\s*([({])""")
    private val targetNameRegex = Regex("^\\s*(?:name\\s*=\\s*)?\"([A-Za-z_]\\w*)\"")
    private val createdByDelegateRegex = Regex("""\bval\s+([A-Za-z_]\w*)\s+by\s+(?:creating|registering)\b""")
    private val createdByCallRegex = Regex("\\b(?:create|register|maybeCreate)\\s*\\(\\s*\"([A-Za-z_]\\w*)\"")

    fun parse(script: String): KmpBuildScriptInfo {
        val code = ScriptCode(script)
        // Prefer the outermost `kotlin {}` block, e.g. the top-level one over one nested in `subprojects {}`
        val kotlinOpen = kotlinBlockRegex.findAll(code.text)
            .map { it.range.last }
            .filter { code.isCode(it) }
            .minByOrNull { code.depthAt(it) }
            ?: return KmpBuildScriptInfo(kotlinBlockFound = false, targets = emptyList(), customSourceSets = emptyList())

        val bodyStart = kotlinOpen + 1
        val bodyEnd = code.matchingBrace(kotlinOpen).takeIf { it >= 0 } ?: code.text.length
        val topLevel = code.topLevelText(bodyStart, bodyEnd)

        return KmpBuildScriptInfo(
            kotlinBlockFound = true,
            targets = parseTargets(code, topLevel, bodyStart),
            customSourceSets = parseCustomSourceSets(code, topLevel, bodyStart)
        )
    }

    private fun parseTargets(code: ScriptCode, topLevel: String, offset: Int): List<KmpTarget> {
        val targets = LinkedHashMap<String, KmpTarget>()
        for (match in targetCallRegex.findAll(topLevel)) {
            if (!code.isCode(offset + match.range.first)) continue
            val preset = presetsByDslName.getValue(match.groupValues[1])
            var cursor = match.groups[2]!!.range.first
            var name = preset.defaultName

            if (topLevel[cursor] == '(') {
                if (preset.requiresBlock) continue
                val close = code.matchingParen(offset + cursor) - offset
                if (close < 0) continue
                targetNameRegex.find(topLevel.substring(cursor + 1, close))?.let { name = it.groupValues[1] }
                cursor = close + 1
                while (cursor < topLevel.length && topLevel[cursor].isWhitespace()) cursor++
            }

            var configuration = ""
            if (cursor < topLevel.length && topLevel[cursor] == '{') {
                val open = offset + cursor
                val close = code.matchingBrace(open).takeIf { it >= 0 } ?: code.text.length
                configuration = code.text.substring(open + 1, close)
            } else if (preset.requiresBlock) {
                continue
            }

            // A target may be declared more than once, e.g. `jvm()` and later `jvm { … }`
            val existing = targets[name]
            targets[name] = if (existing == null) {
                KmpTarget(preset, name, configuration)
            } else {
                existing.copy(configuration = existing.configuration + "\n" + configuration)
            }
        }
        return targets.values.toList()
    }

    private fun parseCustomSourceSets(code: ScriptCode, topLevel: String, offset: Int): List<String> {
        val open = sourceSetsBlockRegex.findAll(topLevel)
            .map { offset + it.range.last }
            .firstOrNull { code.isCode(it) }
            ?: return emptyList()
        val close = code.matchingBrace(open).takeIf { it >= 0 } ?: code.text.length
        val body = code.text.substring(open + 1, close)
        return (createdByDelegateRegex.findAll(body) + createdByCallRegex.findAll(body))
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }
}

/**
 * A script with comments blanked out (offsets preserved), aware of string/char literals and brace depth.
 */
internal class ScriptCode(source: String) {
    /** The source with comments replaced by spaces; line breaks are kept. */
    val text: String
    private val literal = BooleanArray(source.length)
    private val depth = IntArray(source.length)

    init {
        val chars = source.toCharArray()
        val n = source.length
        fun blank(from: Int, to: Int) {
            for (k in from until to) if (chars[k] != '\n') chars[k] = ' '
        }
        fun markLiteral(from: Int, to: Int) {
            for (k in from until to) literal[k] = true
        }

        var i = 0
        while (i < n) {
            val c = source[i]
            when {
                source.startsWith("//", i) -> {
                    val end = source.indexOf('\n', i).let { if (it < 0) n else it }
                    blank(i, end)
                    i = end
                }
                source.startsWith("/*", i) -> {
                    // Kotlin block comments can be nested
                    var nesting = 0
                    var j = i
                    while (j < n) {
                        when {
                            source.startsWith("/*", j) -> { nesting++; j += 2 }
                            source.startsWith("*/", j) -> { nesting--; j += 2; if (nesting == 0) break }
                            else -> j++
                        }
                    }
                    j = minOf(j, n)
                    blank(i, j)
                    i = j
                }
                source.startsWith("\"\"\"", i) -> {
                    var j = source.indexOf("\"\"\"", i + 3).let { if (it < 0) n else it + 3 }
                    while (j < n && source[j] == '"') j++
                    markLiteral(i, j)
                    i = j
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < n && source[j] != c && source[j] != '\n') {
                        if (source[j] == '\\') j++
                        j++
                    }
                    j = minOf(j + 1, n)
                    markLiteral(i, j)
                    i = j
                }
                else -> i++
            }
        }
        text = String(chars)

        var d = 0
        for (k in 0 until n) {
            depth[k] = d
            if (literal[k]) continue
            when (text[k]) {
                '{' -> d++
                '}' -> d = maxOf(0, d - 1)
            }
        }
    }

    /** Whether the character at [index] is code (not inside a string or char literal). */
    fun isCode(index: Int): Boolean = !literal[index]

    /** Brace depth before the character at [index]. */
    fun depthAt(index: Int): Int = depth[index]

    /** Index of the `}` closing the `{` at [open], or -1. */
    fun matchingBrace(open: Int): Int {
        val inner = depth[open] + 1
        for (i in open + 1 until text.length) {
            if (!literal[i] && text[i] == '}' && depth[i] == inner) return i
        }
        return -1
    }

    /** Index of the `)` closing the `(` at [open], or -1. */
    fun matchingParen(open: Int): Int {
        var level = 0
        for (i in open until text.length) {
            if (literal[i]) continue
            when (text[i]) {
                '(' -> level++
                ')' -> if (--level == 0) return i
            }
        }
        return -1
    }

    /**
     * The text in `[start, end)` where everything nested in braces deeper than [start]'s depth is blanked,
     * so that only the block's own statements remain visible. Offsets are preserved.
     */
    fun topLevelText(start: Int, end: Int): String {
        if (start >= end) return ""
        val level = depth[start]
        val chars = CharArray(end - start) { k ->
            val c = text[start + k]
            if (depth[start + k] == level || c == '\n') c else ' '
        }
        return String(chars)
    }
}
