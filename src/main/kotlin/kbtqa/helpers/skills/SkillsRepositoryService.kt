package kbtqa.helpers.skills

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import java.io.File
import java.io.IOException

/**
 * Information about a single skill available in a repository.
 */
data class SkillInfo(
    val name: String,
    val path: String
)

/**
 * Service for fetching available skills from git repositories by cloning and listing directories.
 * Works with any git repository (GitHub, GitLab, internal repos, etc.).
 */
@Service(Service.Level.APP)
class SkillsRepositoryService {

    private val logger = thisLogger()

    /**
     * Fetches the list of available skills (directories) from a skill repository
     * by performing a shallow clone and listing directories in the skills path.
     */
    fun fetchSkills(repo: SkillRepository): List<SkillInfo> {
        val git = resolveGitExecutable()

        val tempDir = kotlin.io.path.createTempDirectory("skills-browse-").toFile()
        try {
            logger.info("Cloning ${redactUrl(repo.url)} to list skills...")
            cloneRepository(git, repo.url, tempDir)

            val skillsDir = File(tempDir, repo.skillsPath)
            if (!skillsDir.exists() || !skillsDir.isDirectory) {
                throw RuntimeException("Skills directory '${repo.skillsPath}' not found in repository")
            }

            return skillsDir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.map { SkillInfo(name = it.name, path = "${repo.skillsPath}/${it.name}") }
                ?.sortedBy { it.name }
                ?: emptyList()
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun resolveGitExecutable(): String {
        val git = GitExecutableHelper.getGitExecutable()
        try {
            val process = ProcessBuilder(git, "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("Git is not available. Please install Git or configure it in Settings → Version Control → Git.")
            }
        } catch (e: IOException) {
            throw RuntimeException("Git is not available. Please install Git or configure it in Settings → Version Control → Git.", e)
        }
        return git
    }

    private fun cloneRepository(git: String, url: String, targetDir: File) {
        val pb = ProcessBuilder(git, "clone", "--depth", "1", url, targetDir.absolutePath)
            .redirectErrorStream(true)
        pb.environment()["GIT_TERMINAL_PROMPT"] = "0"
        val process = pb.start()
        process.outputStream.close()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw RuntimeException("Failed to clone repository: ${redactUrl(output)}")
        }
    }

    private fun redactUrl(text: String): String {
        return text.replace(Regex("(https?://)([^@]+)@"), "$1***@")
    }
}
