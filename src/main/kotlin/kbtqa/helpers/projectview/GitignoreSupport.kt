package kbtqa.helpers.projectview

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/**
 * Represents a successful ignore match.
 * @param pattern The pattern that matched the path (e.g. "*.log", "mydir/", or "(other git-ignored files)" for VCS-only matches)
 */
data class IgnoreMatch(val pattern: String)

/**
 * Filter that decides whether a project-relative path is ignored.
 */
interface IgnoreFilter {
    /**
     * Checks whether the given path is ignored.
     * @param relativePath Path relative to the project root, '/'-separated
     * @param isDirectory Whether the path denotes a directory
     * @return The match describing the ignoring pattern, or null if the path is not ignored
     */
    fun match(relativePath: String, isDirectory: Boolean): IgnoreMatch?
}

/**
 * Filter that never matches anything. Used when no ignore rules are available.
 */
object NoopIgnoreFilter : IgnoreFilter {
    override fun match(relativePath: String, isDirectory: Boolean): IgnoreMatch? = null
}

/**
 * Parses `.gitignore` files (project root and nested ones) plus `.git/info/exclude`
 * and matches paths against the collected rules.
 *
 * Supported syntax subset:
 * - comments (`#`) and blank lines
 * - anchored patterns (leading `/` or patterns containing `/`)
 * - unanchored patterns (match at any directory level)
 * - trailing `/` for directory-only rules
 * - `!` negation (last matching rule wins; re-inclusion inside ignored directories is not supported)
 * - `*`, `?` and `**` globs
 *
 * Rules from nested `.gitignore` files apply only to paths under the directory containing the file.
 * The discovery walk does not descend into directories named in [skipDirNames], into `.git`,
 * or into Gradle module `build` directories, so e.g. `.idea/.gitignore` is never parsed.
 *
 * @param projectDir The project root directory
 * @param skipDirNames Directory names that are never walked when looking for nested `.gitignore` files
 */
class GitignoreFileFilter(
    private val projectDir: File,
    private val skipDirNames: Set<String> = emptySet()
) : IgnoreFilter {

    /**
     * A single parsed ignore rule.
     * @param pattern Display pattern (original line, prefixed with the base directory for nested files)
     * @param regex Regex the rule matches against paths relative to [baseDir]
     * @param negated Whether this is a `!` re-inclusion rule
     * @param dirOnly Whether the rule applies to directories only (trailing `/`)
     * @param baseDir Directory (project-relative, '/'-separated) the rule is scoped to; empty for root rules
     */
    private class IgnoreRule(
        val pattern: String,
        val regex: Regex,
        val negated: Boolean,
        val dirOnly: Boolean,
        val baseDir: String
    )

    // Loaded lazily so the constructor is cheap and file I/O happens on the first match
    // (the dialog queries the filter from a pooled thread, off the EDT)
    private val rules: List<IgnoreRule> by lazy { loadRules() }

    /**
     * Returns true if at least one ignore rule was loaded.
     */
    fun hasRules(): Boolean = rules.isNotEmpty()

    override fun match(relativePath: String, isDirectory: Boolean): IgnoreMatch? {
        val path = relativePath.replace('\\', '/').trim('/')
        if (path.isEmpty() || rules.isEmpty()) return null

        // Direct match for the path itself
        evaluate(path, isDirectory)?.let { return it }

        // A path inside an ignored directory is ignored as well
        var separatorIndex = path.lastIndexOf('/')
        while (separatorIndex > 0) {
            val ancestor = path.substring(0, separatorIndex)
            evaluate(ancestor, isDirectory = true)?.let { return it }
            separatorIndex = ancestor.lastIndexOf('/')
        }
        return null
    }

    /**
     * Evaluates all rules against the given path; the last matching rule wins (git semantics).
     */
    private fun evaluate(path: String, isDirectory: Boolean): IgnoreMatch? {
        var current: IgnoreMatch? = null
        for (rule in rules) {
            if (rule.dirOnly && !isDirectory) continue
            val candidate = relativeToBase(path, rule.baseDir) ?: continue
            if (rule.regex.matches(candidate)) {
                current = if (rule.negated) null else IgnoreMatch(rule.pattern)
            }
        }
        return current
    }

    /**
     * Returns the path relative to the rule's base directory, or null if the path is outside it.
     */
    private fun relativeToBase(path: String, baseDir: String): String? {
        if (baseDir.isEmpty()) return path
        val prefix = "$baseDir/"
        return if (path.startsWith(prefix)) path.substring(prefix.length) else null
    }

    private fun loadRules(): List<IgnoreRule> {
        val result = mutableListOf<IgnoreRule>()
        // .git/info/exclude contributes rules scoped to the project root
        val gitInfoExclude = File(projectDir, ".git/info/exclude")
        if (gitInfoExclude.isFile) {
            parseIgnoreFile(gitInfoExclude, baseDir = "", target = result)
        }
        collectGitignoreFiles(projectDir, baseDir = "", target = result)
        return result
    }

    /**
     * Walks the project tree collecting `.gitignore` files. Deeper files are parsed later,
     * so their rules take precedence (last match wins). The walk is pruned at directories
     * from [skipDirNames], at `.git`, and at Gradle module `build` directories.
     */
    private fun collectGitignoreFiles(dir: File, baseDir: String, target: MutableList<IgnoreRule>) {
        val gitignore = File(dir, GITIGNORE_FILE_NAME)
        if (gitignore.isFile) {
            parseIgnoreFile(gitignore, baseDir, target)
        }

        dir.listFiles()?.forEach { child ->
            if (!child.isDirectory) return@forEach
            if (child.name == ".git" || child.name in skipDirNames) return@forEach
            if (child.name == "build" && isGradleModuleDirectory(dir)) return@forEach

            val childBase = if (baseDir.isEmpty()) child.name else "$baseDir/${child.name}"
            collectGitignoreFiles(child, childBase, target)
        }
    }

    /**
     * Checks if the given directory is a Gradle module directory
     * (contains build.gradle or build.gradle.kts).
     */
    private fun isGradleModuleDirectory(dir: File): Boolean {
        return dir.listFiles()?.any { it.isFile && it.name in setOf("build.gradle", "build.gradle.kts") } == true
    }

    private fun parseIgnoreFile(file: File, baseDir: String, target: MutableList<IgnoreRule>) {
        val lines = runCatching { file.readLines() }.getOrElse { return }
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            parseRule(line, baseDir)?.let { target.add(it) }
        }
    }

    private fun parseRule(line: String, baseDir: String): IgnoreRule? {
        var pattern = line
        val negated = pattern.startsWith("!")
        if (negated) pattern = pattern.substring(1)

        val dirOnly = pattern.endsWith("/")
        if (dirOnly) pattern = pattern.dropLast(1)

        val leadingSlash = pattern.startsWith("/")
        if (leadingSlash) pattern = pattern.substring(1)
        if (pattern.isEmpty()) return null

        // A pattern starting with or containing a slash is anchored to the base directory;
        // otherwise it can match at any depth below it.
        val anchored = leadingSlash || pattern.contains('/')
        val prefix = if (anchored) "" else "(?:.*/)?"
        val regex = Regex("^$prefix${globToRegex(pattern)}$")

        val displayPattern = if (baseDir.isEmpty()) line else "$baseDir/$line"
        return IgnoreRule(displayPattern, regex, negated, dirOnly, baseDir)
    }

    /**
     * Converts a gitignore glob pattern to a regular expression body.
     */
    private fun globToRegex(glob: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            when (val c = glob[i]) {
                '*' -> {
                    if (i + 1 < glob.length && glob[i + 1] == '*') {
                        if (i + 2 < glob.length && glob[i + 2] == '/') {
                            // '**/' matches zero or more directories
                            sb.append("(?:[^/]+/)*")
                            i += 3
                            continue
                        }
                        // trailing or embedded '**' matches anything including '/'
                        sb.append(".*")
                        i += 2
                        continue
                    }
                    sb.append("[^/]*")
                }
                '?' -> sb.append("[^/]")
                in REGEX_SPECIAL_CHARS -> {
                    sb.append('\\')
                    sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private companion object {
        const val GITIGNORE_FILE_NAME = ".gitignore"
        val REGEX_SPECIAL_CHARS = setOf('.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\')
    }
}

/**
 * Filter backed by the IDE's VCS ignore state (`ChangeListManager`), which honors
 * global gitignore files and other rules the file parser cannot see.
 * All calls are wrapped in a read action and guarded against failures so the filter
 * is safe to use when the VCS integration is unavailable.
 */
class VcsIgnoreFilter(
    private val project: Project,
    private val projectDir: File
) : IgnoreFilter {

    override fun match(relativePath: String, isDirectory: Boolean): IgnoreMatch? {
        return try {
            val file = File(projectDir, relativePath)
            val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file) ?: return null
            val ignored = ReadAction.compute<Boolean, RuntimeException> {
                ChangeListManager.getInstance(project).isIgnoredFile(virtualFile)
            }
            if (ignored) IgnoreMatch(VCS_MATCH_PATTERN) else null
        } catch (e: Throwable) {
            null
        }
    }

    companion object {
        /**
         * Synthetic pattern used for files reported as ignored by the VCS but not matched
         * by any known `.gitignore` pattern.
         */
        const val VCS_MATCH_PATTERN = "(other git-ignored files)"
    }
}

/**
 * Combines multiple filters; the first non-null match wins.
 * The gitignore file parser should come first so a concrete pattern is preferred for grouping.
 */
class CompositeIgnoreFilter(private val filters: List<IgnoreFilter>) : IgnoreFilter {
    override fun match(relativePath: String, isDirectory: Boolean): IgnoreMatch? {
        return filters.firstNotNullOfOrNull { it.match(relativePath, isDirectory) }
    }
}

/**
 * Factory for building the [IgnoreFilter] appropriate for a project.
 */
object IgnoreFilters {

    private val logger = Logger.getInstance(IgnoreFilters::class.java)

    /**
     * Builds an ignore filter for the given project directory.
     * The `.gitignore` file parser is always active when ignore files exist; the VCS-backed
     * filter is added when the project is available and the VCS API can be accessed
     * (e.g. the optional Git4Idea plugin is enabled).
     *
     * @param project The IDE project, or null if not available
     * @param projectDir The project root directory
     * @param skipDirNames Directory names that are never walked when looking for nested `.gitignore` files
     */
    fun forProject(project: Project?, projectDir: File, skipDirNames: Set<String> = emptySet()): IgnoreFilter {
        val filters = mutableListOf<IgnoreFilter>()

        // Rules are loaded lazily on first match, so creating the filter here is cheap (EDT-safe)
        runCatching { GitignoreFileFilter(projectDir, skipDirNames) }
            .onSuccess { filters.add(it) }
            .onFailure { logger.info("Failed to create .gitignore filter: ${it.message}") }

        if (project != null && File(projectDir, ".git").exists()) {
            try {
                filters.add(VcsIgnoreFilter(project, projectDir))
            } catch (e: Throwable) {
                logger.info("VCS ignore filter not available: ${e.message}")
            }
        }

        return when (filters.size) {
            0 -> NoopIgnoreFilter
            1 -> filters.first()
            else -> CompositeIgnoreFilter(filters)
        }
    }
}
