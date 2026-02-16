package kbtqa.helpers.versions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Manages async loading of tool versions, including pagination state.
 * Extracted from ToolVersionsDialog to separate loading logic from UI.
 */
class VersionLoadingController(
    private val project: Project?,
    private val toolsManager: ToolVersionsManager
) {

    private val logger = thisLogger()

    private val loadedTools = mutableMapOf<String, ToolVersionsManager.Tool>()
    private val loadingStates = mutableMapOf<String, Boolean>()
    private val paginationStates = mutableMapOf<String, PaginationState>()

    data class PaginationState(val prevUrl: String?, val nextUrl: String?)

    fun isLoading(toolName: String): Boolean = loadingStates[toolName] == true

    fun getLoadedTool(toolName: String): ToolVersionsManager.Tool? = loadedTools[toolName]

    fun getPaginationState(toolName: String): PaginationState? = paginationStates[toolName]

    /**
     * Loads versions for a tool. Uses paginated loading if the service supports it.
     */
    fun loadTool(
        tool: ToolVersionsManager.Tool,
        onSuccess: (ToolVersionsManager.Tool) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        if (tool.service is PaginatedVersionsService) {
            loadPaginatedPage(tool, null, onSuccess, onError, onComplete)
            return
        }

        val loadedTool = loadedTools[tool.name]
        if (loadedTool != null) {
            onSuccess(loadedTool)
            return
        }

        if (loadingStates[tool.name] == true) {
            return
        }

        loadingStates[tool.name] = true

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading ${tool.name} Versions", true) {
            private var updatedTool: ToolVersionsManager.Tool? = null
            private var error: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching ${tool.name} versions..."
                indicator.isIndeterminate = true

                try {
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            updatedTool = toolsManager.fetchVersionsForTool(tool)
                        }
                    }
                } catch (e: Exception) {
                    error = e
                }
            }

            override fun onSuccess() {
                ApplicationManager.getApplication().invokeLater {
                    loadingStates[tool.name] = false

                    if (error != null) {
                        logger.warn("Failed to load versions for ${tool.name}", error)
                        onError("Failed to load versions for ${tool.name}")
                    } else if (updatedTool != null) {
                        loadedTools[tool.name] = updatedTool!!
                        onSuccess(updatedTool!!)
                    }

                    onComplete()
                }
            }

            override fun onCancel() {
                ApplicationManager.getApplication().invokeLater {
                    loadingStates[tool.name] = false
                    onComplete()
                }
            }
        })
    }

    /**
     * Loads a specific page for a paginated tool service.
     */
    fun loadPaginatedPage(
        tool: ToolVersionsManager.Tool,
        url: String?,
        onSuccess: (ToolVersionsManager.Tool) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        if (loadingStates[tool.name] == true) {
            return
        }

        loadingStates[tool.name] = true

        val paginatedService = tool.service as? PaginatedVersionsService
        if (paginatedService == null) {
            loadingStates[tool.name] = false
            onError("Failed to load ${tool.name} versions (service unavailable)")
            onComplete()
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading ${tool.name} Versions", true) {
            private var result: PaginatedVersionsService.PaginatedResult? = null
            private var error: Throwable? = null

            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching ${tool.name} versions..."
                indicator.isIndeterminate = true

                try {
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            result = paginatedService.fetchPage(url)
                        }
                    }
                } catch (e: Exception) {
                    error = e
                }
            }

            override fun onSuccess() {
                ApplicationManager.getApplication().invokeLater {
                    loadingStates[tool.name] = false

                    if (error != null || result == null) {
                        logger.warn("Failed to load ${tool.name} versions", error)
                        onError("Failed to load ${tool.name} versions")
                    } else {
                        val res = result!!
                        if (res.errorMessage != null) {
                            onError(res.errorMessage)
                        } else {
                            paginationStates[tool.name] = PaginationState(res.prevUrl, res.nextUrl)

                            val channel = VersionsService.VersionChannel(
                                name = "All",
                                description = "${tool.name} versions from GitHub releases",
                                versions = res.versions
                            )
                            val updatedTool = ToolVersionsManager.Tool(tool.name, tool.service, listOf(channel))
                            loadedTools[tool.name] = updatedTool
                            onSuccess(updatedTool)
                        }
                    }

                    onComplete()
                }
            }

            override fun onCancel() {
                ApplicationManager.getApplication().invokeLater {
                    loadingStates[tool.name] = false
                    onComplete()
                }
            }
        })
    }
}
