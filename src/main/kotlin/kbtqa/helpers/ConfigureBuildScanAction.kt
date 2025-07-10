package kbtqa.helpers

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

/**
 * Action that adds a context menu option for settings.gradle.kts files
 * to configure Gradle Build Scan (Develocity).
 */
class ConfigureBuildScanAction : AnAction("Configure Build Scan", "Configure Gradle Build Scan (Develocity)", null), DumbAware {

    companion object {
        private const val PLUGIN_ID_WITHOUT_VERSION = "id(\"com.gradle.develocity\")"
        private const val PLUGIN_ID = "$PLUGIN_ID_WITHOUT_VERSION version(\"3.17\")"


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

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        // Always show the action, but enable it only for settings.gradle.kts files
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null && file.name == "settings.gradle.kts"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        configureBuildScan(project, editor)
    }

    private fun configureBuildScan(project: Project, editor: Editor) {
        val document = editor.document
        val text = document.text
        
        WriteCommandAction.runWriteCommandAction(project) {
            // Check if the plugin is already added (only check for plugin ID, not version)
            val pluginAlreadyAdded = text.contains(PLUGIN_ID_WITHOUT_VERSION)
            
            // Add plugin to plugins block if not already added
            if (!pluginAlreadyAdded) {
                val pluginsBlockRegex = Regex("plugins\\s*\\{[^}]*}")
                val pluginsBlockMatcher = pluginsBlockRegex.find(text)
                
                if (pluginsBlockMatcher != null) {
                    // Found plugins block, insert our plugin before the closing brace
                    val closingBraceOffset = pluginsBlockMatcher.range.last
                    val insertOffset = text.lastIndexOf('}', closingBraceOffset)
                    
                    // Insert with proper indentation and newline
                    document.insertString(insertOffset, "\n    $PLUGIN_ID\n")
                } else {
                    // No plugins block found, create one at the beginning of the file
                    document.insertString(0, "plugins {\n    $PLUGIN_ID\n}\n\n")
                }
            }
            
            // Add develocity configuration block if not already present
            if (!text.contains("develocity")) {
                // Add at the end of the file with proper spacing
                val insertPosition = document.textLength
                val prefix = if (text.endsWith("\n")) "" else "\n\n"
                document.insertString(insertPosition, "$prefix$DEVELOCITY_CONFIG\n")
            }
        }
    }
}