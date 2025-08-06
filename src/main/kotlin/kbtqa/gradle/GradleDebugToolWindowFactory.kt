package kbtqa.gradle

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Gradle Debug Tool Window.
 * The tool window is only available for Gradle projects.
 */
class GradleDebugToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val gradleDebugPanel = GradleDebugPanel(project)
        val content = ContentFactory.getInstance().createContent(gradleDebugPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun isApplicable(project: Project): Boolean {
        // Only show the tool window for Gradle projects
        return isGradleProject(project)
    }

    private fun isGradleProject(project: Project): Boolean {
        val baseDir: VirtualFile = project.baseDir ?: return false
        
        // Check for common Gradle files
        return baseDir.findChild("build.gradle.kts") != null ||
               baseDir.findChild("build.gradle") != null ||
               baseDir.findChild("settings.gradle.kts") != null ||
               baseDir.findChild("settings.gradle") != null
    }
}