package kbtqa.helpers.editor

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.*

/**
 * Action that adds a context menu option for settings.gradle.kts files
 * to configure Gradle Build Scan (Develocity).
 */
class ConfigureBuildScanAction :
    BaseSettingsGradleAction("Configure Build Scan", "Configure Gradle Build Scan (Develocity)", null) {

    companion object {
        private const val PLUGIN_ID_WITHOUT_VERSION = "id(\"com.gradle.develocity\")"
        private const val PLUGIN_ID = "$PLUGIN_ID_WITHOUT_VERSION version(\"4.3.3\")"

        private const val DEVELOCITY_BLOCK_NAME = "develocity"
        private const val PLUGINS_BLOCK_NAME = "plugins"
        private val PLUGINS_PRECEDING_BLOCK_NAMES = listOf("pluginManagement", "buildscript")

        private val DEVELOCITY_CONFIG = """
develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
    }
    server.set("https://ge.labs.jb.gg")
    // Login on https://ge.labs.jb.gg
    // Generate Access Token in Settings
    accessKey.set("000")
}
""".trimIndent()
    }

    override fun performConfiguration(project: Project, ktFile: KtFile) {
        configureBuildScan(project, ktFile)
    }

    private fun configureBuildScan(project: Project, ktFile: KtFile) {
        executeWriteAction(project) {
            val factory = KtPsiFactory(project)

            if (!hasDevelocityPlugin(ktFile)) {
                addDevelocityPlugin(ktFile, factory)
            }

            if (!ktFile.hasBlock(DEVELOCITY_BLOCK_NAME)) {
                ktFile.addContentToFile(factory, DEVELOCITY_CONFIG)
            }
        }
    }

    private fun hasDevelocityPlugin(ktFile: KtFile): Boolean {
        val pluginsBlock = ktFile.findTopLevelBlock(PLUGINS_BLOCK_NAME) ?: return false
        val body = pluginsBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return false
        return body.statements.any { it.text.contains(PLUGIN_ID_WITHOUT_VERSION) }
    }

    private fun addDevelocityPlugin(ktFile: KtFile, factory: KtPsiFactory) {
        // Only the top-level plugins block applies plugins; the one in pluginManagement just declares versions
        val pluginsBlock = ktFile.findTopLevelBlock(PLUGINS_BLOCK_NAME)
        val pluginDeclaration = factory.createExpression(PLUGIN_ID)

        if (pluginsBlock != null) {
            // Existing plugins block found, add our plugin to it
            val body = pluginsBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
            body?.appendStatement(factory, pluginDeclaration)
        } else {
            // No plugins block found, create one. Gradle allows only pluginManagement and buildscript before it.
            val newPluginsBlock = factory.createExpression("plugins {\n    ${pluginDeclaration.text}\n}")
            ktFile.insertTopLevelBlock(factory, newPluginsBlock, afterBlocks = PLUGINS_PRECEDING_BLOCK_NAMES)
        }
    }

}