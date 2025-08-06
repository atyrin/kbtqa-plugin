package kbtqa.helpers

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*

/**
 * Action that adds a context menu option for Gradle files
 * to insert repository configurations using Kotlin PSI.
 */
class ConfigureRepositoriesAction :
    BaseSettingsGradleAction("Configure Repositories", "Insert repository configurations", null) {
    companion object {
        private const val COMMON_REPOSITORIES = """mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/dev")
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/experimental")
        google()"""
        private const val PLUGIN_REPOSITORIES = """gradlePluginPortal()
        mavenCentral()
        maven("https://redirector.kotlinlang.org/maven/dev")
        maven("https://redirector.kotlinlang.org/maven/bootstrap")
        maven("https://redirector.kotlinlang.org/maven/experimental")
        google()"""
        private const val PLUGIN_MANAGEMENT_BLOCK_NAME = "pluginManagement"
        private const val DEPENDENCY_RESOLUTION_MANAGEMENT_BLOCK_NAME = "dependencyResolutionManagement"
        private const val REPOSITORIES_BLOCK_NAME = "repositories"
        private const val SETTINGS_GRADLE_KTS = "settings.gradle.kts"
        private const val BUILD_GRADLE_KTS = "build.gradle.kts"
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        // Always show the action, but enable it only for settings.gradle.kts and build.gradle.kts files
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null &&
                (file.name == SETTINGS_GRADLE_KTS || file.name == BUILD_GRADLE_KTS)
    }

    override fun performConfiguration(project: Project, ktFile: KtFile) {
        val file = ktFile.virtualFile ?: return
        when (file.name) {
            SETTINGS_GRADLE_KTS -> configureSettingsGradleRepositories(project, ktFile)
            BUILD_GRADLE_KTS -> configureBuildGradleRepositories(project, ktFile)
        }
    }

    private fun configureSettingsGradleRepositories(project: Project, ktFile: KtFile) {
        executeWriteAction(project) {
            val factory = KtPsiFactory(project)
            // Configure pluginManagement block
            configureRepositoriesInParentBlock(
                ktFile, factory, PLUGIN_MANAGEMENT_BLOCK_NAME, PLUGIN_REPOSITORIES
            ) { content ->
                val newBlock = factory.createExpression(content)
                val anchor = ktFile.firstChild
                val addedBlock = if (anchor != null) {
                    ktFile.addBefore(newBlock, anchor)
                } else {
                    ktFile.add(newBlock)
                }
                ktFile.addAfter(factory.createNewLine(2), addedBlock)
            }
            // Configure dependencyResolutionManagement block
            configureRepositoriesInParentBlock(
                ktFile,
                factory,
                DEPENDENCY_RESOLUTION_MANAGEMENT_BLOCK_NAME,
                COMMON_REPOSITORIES
            ) { content ->
                // Attempt to add after pluginManagement block for better formatting
                val pluginManagementBlock = ktFile.findBlock(PLUGIN_MANAGEMENT_BLOCK_NAME)
                if (pluginManagementBlock != null) {
                    val newBlock = factory.createExpression(content)
                    val addedBlock = pluginManagementBlock.parent.addAfter(newBlock, pluginManagementBlock)
                    pluginManagementBlock.parent.addBefore(factory.createNewLine(2), addedBlock)
                } else {
                    // Fallback to adding at the end of the file
                    ktFile.addContentToFile(factory, content)
                }
            }
        }
    }

    private fun configureBuildGradleRepositories(project: Project, ktFile: KtFile) {
        executeWriteAction(project) {
            val factory = KtPsiFactory(project)
            val repositoriesBlock = ktFile.findBlock(REPOSITORIES_BLOCK_NAME)
            if (repositoriesBlock != null) {
                // Update existing repositories block
                updateRepositoriesContent(repositoriesBlock, factory, COMMON_REPOSITORIES)
            } else {
                // Add new repositories block
                val indentedRepos = COMMON_REPOSITORIES.prependIndent("    ")
                val repositoriesConfig = """
                |repositories {
                |$indentedRepos
                |}
                """.trimMargin()
                ktFile.addContentToFile(factory, repositoriesConfig)
            }
        }
    }

    private fun configureRepositoriesInParentBlock(
        ktFile: KtFile,
        factory: KtPsiFactory,
        parentBlockName: String,
        repositoriesContent: String,
        addNewBlock: (String) -> Unit
    ) {
        val parentBlock = ktFile.findBlock(parentBlockName)
        if (parentBlock != null) {
            // Parent block exists, check for repositories block inside
            val repositoriesBlock = findRepositoriesInBlock(parentBlock)
            if (repositoriesBlock != null) {
                updateRepositoriesContent(repositoriesBlock, factory, repositoriesContent)
            } else {
                addRepositoriesToBlock(parentBlock, factory, repositoriesContent)
            }
        } else {
            // Parent block does not exist, create it with repositories
            val indentedRepos = repositoriesContent.prependIndent("        ")
            val newBlockContent = """
            |$parentBlockName {
            |    repositories {
            |$indentedRepos
            |    }
            |}
            """.trimMargin()
            addNewBlock(newBlockContent)
        }
    }

    private fun findRepositoriesInBlock(parentBlock: KtCallExpression): KtCallExpression? {
        val lambdaBody = parentBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
        return lambdaBody?.let { body ->
            PsiTreeUtil.findChildrenOfType(body, KtCallExpression::class.java)
                .find { it.calleeExpression?.text == REPOSITORIES_BLOCK_NAME }
        }
    }

    private fun addRepositoriesToBlock(parentBlock: KtCallExpression, factory: KtPsiFactory, repositories: String) {
        val lambdaBody = parentBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
        if (lambdaBody != null) {
            val formattedRepositories = repositories.lines().joinToString("\n") { "    $it" }
            val repositoriesBlock = factory.createExpression("repositories {\n$formattedRepositories\n}")
            lambdaBody.addBefore(repositoriesBlock, lambdaBody.lastChild)
            lambdaBody.addBefore(factory.createNewLine(), lambdaBody.lastChild)
        }
    }

    private fun updateRepositoriesContent(
        repositoriesBlock: KtCallExpression,
        factory: KtPsiFactory,
        repositories: String
    ) {
        val lambdaBody = repositoriesBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return

        val existingRepositories = lambdaBody.statements
        val existingMavenUrls = existingRepositories.mapNotNull { getMavenUrl(it) }.map { it.removeSuffix("/") }.toSet()
        val existingOtherRepoTexts = existingRepositories.filter { getMavenUrl(it) == null }.map { it.text }.toSet()

        val repositoriesToAdd = repositories.lines().map(String::trim).filter(String::isNotEmpty)

        for (repoString in repositoriesToAdd) {
            val newRepoExpression = factory.createExpression(repoString)
            val mavenUrl = getMavenUrl(newRepoExpression)

            val shouldAdd = if (mavenUrl != null) {
                mavenUrl.removeSuffix("/") !in existingMavenUrls
            } else {
                newRepoExpression.text !in existingOtherRepoTexts
            }

            if (shouldAdd) {
                val anchor = lambdaBody.lastChild
                if (anchor != null) {
                    val addedRepo = lambdaBody.addBefore(newRepoExpression, anchor)
                    lambdaBody.addAfter(factory.createNewLine(), addedRepo)
                }
            }
        }
    }

    private fun getMavenUrl(expression: KtExpression): String? {
        if (expression !is KtCallExpression) return null
        if (expression.calleeExpression?.text != "maven") return null

        val argument = expression.valueArguments.firstOrNull()?.getArgumentExpression() as? KtStringTemplateExpression
            ?: return null

        // It's a string literal like "https://...", without any variables.
        if (argument.entries.size == 1 && argument.entries[0] is KtLiteralStringTemplateEntry) {
            return argument.entries[0].text
        }

        return null
    }
}