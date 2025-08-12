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
        private const val PLUGIN_ID = "$PLUGIN_ID_WITHOUT_VERSION version(\"3.17\")"

        private const val DEVELOCITY_BLOCK_NAME = "develocity"
        private const val PLUGINS_BLOCK_NAME = "plugins"
        private const val PLUGIN_MANAGEMENT_BLOCK_NAME = "pluginManagement"

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
        val pluginsBlock = ktFile.findBlock(PLUGINS_BLOCK_NAME) ?: return false
        val body = pluginsBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression ?: return false
        return body.statements.any { it.text.contains(PLUGIN_ID_WITHOUT_VERSION) }
    }

    private fun addDevelocityPlugin(ktFile: KtFile, factory: KtPsiFactory) {
        val pluginsBlock = ktFile.findBlock(PLUGINS_BLOCK_NAME)
        val pluginDeclaration = factory.createExpression(PLUGIN_ID)

        if (pluginsBlock != null) {
            // Existing plugins block found, add our plugin to it
            val body = pluginsBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
            if (body != null) {
                // Add the plugin before the closing brace of the block
                body.addBefore(pluginDeclaration, body.lastChild)
                body.addBefore(factory.createNewLine(), body.lastChild)
            }
        } else {
            // No plugins block found, create one
            val newPluginsBlock = factory.createExpression("plugins {\n    ${pluginDeclaration.text}\n}")
            val pluginManagementBlock = ktFile.findBlock(PLUGIN_MANAGEMENT_BLOCK_NAME)

            if (pluginManagementBlock != null) {
                // Insert plugins block after pluginManagement block for better structure
                ktFile.add(factory.createNewLine(2))
                ktFile.addAfter(newPluginsBlock, pluginManagementBlock)
                ktFile.addAfter(factory.createNewLine(2), pluginManagementBlock)
            } else {
                // No pluginManagement block, insert at the beginning of the file
                ktFile.add(factory.createNewLine(2))
                ktFile.addBefore(newPluginsBlock, ktFile.firstChild)
                ktFile.addBefore(factory.createNewLine(2), ktFile.firstChild)
            }
        }
    }

}