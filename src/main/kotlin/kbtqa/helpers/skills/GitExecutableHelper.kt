package kbtqa.helpers.skills

import com.intellij.openapi.diagnostic.thisLogger
import git4idea.config.GitExecutableManager

/**
 * Helper to resolve the Git executable path using the IDE's configured Git settings.
 * Falls back to "git" if Git4Idea is not available at runtime.
 */
object GitExecutableHelper {

    private val logger = thisLogger()

    fun getGitExecutable(): String {
        return try {
            val path = GitExecutableManager.getInstance().pathToGit
            logger.info("Using IDE-configured Git executable: $path")
            path
        } catch (e: Throwable) {
            logger.info("Git4Idea not available, falling back to 'git': ${e.message}")
            "git"
        }
    }
}
