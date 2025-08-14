package kbtqa.helpers.versions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.*

/**
 * Action that displays available Kotlin versions from different channels.
 * This action is available via IDE search and can be invoked from anywhere.
 */
class KotlinVersionsAction : AnAction(
    "Show Kotlin Versions",
    "Display available Kotlin versions from different channels (Stable, Dev, Experimental)",
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
        
        logger.info("Kotlin Versions action triggered")
        
        // Run the version fetching in a background task with progress indicator
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Kotlin Versions", true) {
            
            private var versionChannels: List<KotlinVersionsService.VersionChannel> = emptyList()
            private var error: Throwable? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching Kotlin versions from repositories..."
                indicator.isIndeterminate = true
                
                try {
                    val service = ApplicationManager.getApplication().service<KotlinVersionsService>()
                    
                    // Run the suspend function in a coroutine
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            versionChannels = service.getAllVersionChannels()
                        }
                    }
                    
                    logger.info("Successfully loaded ${versionChannels.size} version channels")
                } catch (e: Exception) {
                    logger.warn("Failed to fetch Kotlin versions", e)
                    error = e
                }
            }
            
            override fun onSuccess() {
                ApplicationManager.getApplication().invokeLater {
                    if (error != null) {
                        Messages.showErrorDialog(
                            project,
                            "Failed to fetch Kotlin versions: ${error!!.message}",
                            "Error Loading Versions"
                        )
                    } else if (versionChannels.isEmpty()) {
                        Messages.showWarningDialog(
                            project,
                            "No Kotlin versions were found. Please check your internet connection.",
                            "No Versions Found"
                        )
                    } else {
                        // Show the dialog with the fetched versions
                        val dialog = KotlinVersionsDialog(project, versionChannels)
                        dialog.show()
                    }
                }
            }
            
            override fun onCancel() {
                logger.info("Kotlin versions loading was cancelled")
            }
            
            override fun onThrowable(error: Throwable) {
                logger.warn("Unexpected error while loading Kotlin versions", error)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "Unexpected error while loading Kotlin versions: ${error.message}",
                        "Error"
                    )
                }
            }
        })
    }
}