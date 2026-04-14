package kbtqa.helpers.skills

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Service for installing skills by cloning a git repository and copying selected skill directories.
 * Works with any git repository (GitHub, GitLab, internal repos, etc.).
 */
class SkillsInstallerService {

    private val logger = thisLogger()

    /**
     * Installs selected skills from a repository into the target directory.
     *
     * @param repo the skill repository configuration
     * @param skillNames list of skill directory names to install
     * @param targetDir the target directory where skills will be copied (e.g., <project>/.junie/skills)
     * @param indicator progress indicator for showing clone/copy progress
     */
    fun installSkills(
        repo: SkillRepository,
        skillNames: List<String>,
        targetDir: File,
        indicator: ProgressIndicator
    ) {
        val git = resolveGitExecutable()

        val tempDir = createTempDirectory()
        try {
            indicator.text = "Cloning ${repo.url}..."
            indicator.fraction = 0.1
            cloneRepository(git, repo.url, tempDir)

            indicator.text = "Copying skills..."
            val skillsSourceDir = File(tempDir, repo.skillsPath)
            if (!skillsSourceDir.exists()) {
                throw RuntimeException("Skills directory '${repo.skillsPath}' not found in repository")
            }

            targetDir.mkdirs()

            for ((index, skillName) in skillNames.withIndex()) {
                indicator.text = "Copying skill: $skillName"
                indicator.fraction = 0.2 + 0.8 * (index.toDouble() / skillNames.size)

                val source = File(skillsSourceDir, skillName)
                if (!source.exists() || !source.isDirectory) {
                    throw RuntimeException("Skill '$skillName' was not found in the repository. It may have been renamed or removed since browsing.")
                }

                // Reject symlinks to prevent copying arbitrary local directories
                if (Files.isSymbolicLink(source.toPath()) || !source.canonicalPath.startsWith(tempDir.canonicalPath + File.separator)) {
                    throw RuntimeException("Skill '$skillName' contains a symlink or escapes the repository boundary. Skipping for security.")
                }

                val destination = File(targetDir, skillName)
                if (destination.exists()) {
                    destination.deleteRecursively()
                }
                source.copyRecursively(destination, overwrite = true)
                logger.info("Installed skill '$skillName' to $destination")
            }

            indicator.fraction = 1.0
            indicator.text = "Skills installed successfully"
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

    private fun createTempDirectory(): File {
        return kotlin.io.path.createTempDirectory("skills-setup-").toFile()
    }
}
