package kbtqa.helpers

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

/**
 * Action that adds build cache configuration to settings.gradle.kts files
 * and ensures org.gradle.caching=true is set in gradle.properties.
 */
class ConfigureBuildCacheAction : AnAction("Configure Build Cache", "Configure build cache settings", null), DumbAware {

    companion object {
        private const val BUILD_CACHE_CONFIG = """buildCache {
    local {
        directory = File(rootDir, "build-cache")
    }
}"""
        
        private const val GRADLE_CACHING_PROPERTY = "org.gradle.caching=true"
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
        
        insertBuildCacheConfig(project, editor)
        ensureGradleCachingProperty(project)
    }

    private fun insertBuildCacheConfig(project: Project, editor: Editor) {
        val document = editor.document
        
        WriteCommandAction.runWriteCommandAction(project) {
            val insertPosition = editor.caretModel.offset
            val text = document.text
            
            // Check if we need to add newlines around the configuration
            val needsNewlineBefore = insertPosition > 0 && !text.substring(insertPosition - 1, insertPosition).equals("\n")
            val needsNewlineAfter = insertPosition < text.length && !text.substring(insertPosition, insertPosition + 1).equals("\n")
            
            // Build the configuration string with appropriate newlines
            val configToInsert = buildString {
                if (needsNewlineBefore) append("\n")
                append(BUILD_CACHE_CONFIG)
                if (needsNewlineAfter) append("\n")
            }
            
            // Insert the configuration at the cursor position
            document.insertString(insertPosition, configToInsert)
        }
    }

    private fun ensureGradleCachingProperty(project: Project) {
        // Find gradle.properties file in the project root
        val projectDir = project.baseDir ?: return
        val gradlePropertiesFile = projectDir.findChild("gradle.properties") ?: return
        
        val document = FileDocumentManager.getInstance().getDocument(gradlePropertiesFile) ?: return
        val text = document.text
        
        // Check if the property already exists
        if (text.contains("org.gradle.caching=")) {
            return // Property already exists, don't modify it
        }
        
        WriteCommandAction.runWriteCommandAction(project) {
            // Add the property at the end of the file
            val propertyToInsert = if (text.endsWith("\n") || text.isEmpty()) {
                GRADLE_CACHING_PROPERTY + "\n"
            } else {
                "\n" + GRADLE_CACHING_PROPERTY + "\n"
            }
            
            document.insertString(text.length, propertyToInsert)
        }
    }
}