package kbtqa.helpers.versions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*

/**
 * Action that displays available versions for different tools (Kotlin, Android, etc.).
 * This action is available via IDE search and can be invoked from anywhere.
 */
class ToolVersionsAction : AnAction(
    "Show Tool Versions (KGP, AGP)",
    "Display available versions for different development tools (Kotlin, Android Gradle Plugin, etc.)",
    null
), DumbAware {

    private val logger = thisLogger()

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        // Always enable this action - it should be available from anywhere in the IDE
        e.presentation.isEnabled = true
        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        
        logger.info("Tool Versions action triggered")
        
        try {
            val manager = ToolVersionsManager.getInstance()
            
            // Get tools without loading versions (this is fast and synchronous)
            val tools = manager.getAllToolsWithoutVersions()
            
            if (tools.isEmpty()) {
                Messages.showWarningDialog(
                    project,
                    "No tool version services were found.",
                    "No Tools Available"
                )
            } else {
                logger.info("Opening dialog with ${tools.size} tools (versions will be loaded on-demand)")
                // Show the dialog - versions will be loaded on-demand when tools are selected
                val dialog = ToolVersionsDialog(project, tools)
                dialog.show()
            }
        } catch (e: Exception) {
            logger.warn("Failed to get tool services", e)
            Messages.showErrorDialog(
                project,
                "Failed to initialize tool version services: ${e.message}",
                "Error"
            )
        }
    }
}