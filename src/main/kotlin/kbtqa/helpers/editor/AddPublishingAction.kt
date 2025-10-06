package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

/**
 * Action that adds a context menu option for build.gradle.kts files
 * to insert Maven publishing configuration.
 */
class AddPublishingAction : AnAction("Add Publishing", "Insert Maven publishing configuration", null), DumbAware {

    companion object {
        private const val MAVEN_PUBLISH_PLUGIN = """
plugins {
    `maven-publish`
}
"""
        
        private const val PUBLISHING_BLOCK = """
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.gradle.sample"
            artifactId = "library"
            version = "1.1"
            from(components["java"])
        }
    }
}
"""
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Always show the action, but enable it only for build.gradle.kts files
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null && file.name == "build.gradle.kts"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        insertPublishingConfiguration(project, editor)
    }

    private fun insertPublishingConfiguration(project: Project, editor: Editor) {
        val document = editor.document
        val text = document.text

        WriteCommandAction.runWriteCommandAction(project) {
            // First, check if maven-publish plugin is already added
            if (!text.contains("`maven-publish`")) {
                // Find the plugins block
                val pluginsBlockRegex = "plugins\\s*\\{[^}]*}".toRegex()
                val pluginsMatch = pluginsBlockRegex.find(text)
                
                if (pluginsMatch != null) {
                    // Insert maven-publish inside the existing plugins block
                    val pluginsBlock = pluginsMatch.value
                    val closingBraceIndex = pluginsBlock.lastIndexOf("}")
                    val insertPosition = document.text.indexOf(pluginsBlock) + closingBraceIndex
                    
                    // Insert before the closing brace of plugins block
                    document.insertString(insertPosition, "\n    `maven-publish`\n")
                } else {
                    // If no plugins block found, insert at the cursor position
                    val insertPosition = editor.caretModel.offset
                    document.insertString(insertPosition, MAVEN_PUBLISH_PLUGIN)
                }
            }
            
            // Now add the publishing block if it doesn't exist
            if (!text.contains("publishing\\s*\\{".toRegex())) {
                // Insert at the cursor position
                val insertPosition = editor.caretModel.offset
                document.insertString(insertPosition, PUBLISHING_BLOCK)
            }
        }
    }
}