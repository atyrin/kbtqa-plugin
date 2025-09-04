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
        
        // Run the version fetching in a background task with progress indicator
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Tool Versions", true) {
            
            private var tools: List<ToolVersionsManager.Tool> = emptyList()
            private var error: Throwable? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching versions from repositories..."
                indicator.isIndeterminate = true
                
                try {
                    val manager = ToolVersionsManager.getInstance()
                    
                    // Run the suspend function in a coroutine
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            tools = manager.getAllToolsWithVersions()
                        }
                    }
                    
                    logger.info("Successfully loaded versions for ${tools.size} tools")
                } catch (e: Exception) {
                    logger.warn("Failed to fetch tool versions", e)
                    error = e
                }
            }
            
            override fun onSuccess() {
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to fetch tool versions: ${error!!.message}",
                            "Error Loading Versions"
                        )
                    } else if (tools.isEmpty()) {
                        Messages.showWarningDialog(
                            project,
                            "No tool versions were found. Please check your internet connection.",
                            "No Versions Found"
                        )
                    } else {
                        // Show the dialog with the fetched tool versions
                        val dialog = ToolVersionsDialog(project, tools)
                        dialog.show()
                    }
                }
            }
            
            override fun onCancel() {
                logger.info("Tool versions loading was cancelled")
            }
            
            override fun onThrowable(error: Throwable) {
                logger.warn("Unexpected error while loading tool versions", error)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Unexpected error while loading tool versions: ${error.message}",
                        "Error"
                    )
                }
            }
        })
    }
}