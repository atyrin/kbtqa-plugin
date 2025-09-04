package kbtqa.helpers.versions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Manager service that coordinates multiple tool version services.
 * Provides a unified interface for fetching versions from different tools.
 */
@Service
class ToolVersionsManager {

    companion object {
        fun getInstance(): ToolVersionsManager = ApplicationManager.getApplication().service()
    }

    private val logger = thisLogger()

    /**
     * Represents a tool with its version channels.
     */
    data class Tool(
        val name: String,
        val service: VersionsService,
        val channels: List<VersionsService.VersionChannel> = emptyList()
    )

    /**
     * Gets all available tool version services.
     */
    fun getAllVersionServices(): List<VersionsService> {
        return listOf(
            ApplicationManager.getApplication().service<KotlinVersionsService>(),
            ApplicationManager.getApplication().service<AndroidVersionsService>(),
            ApplicationManager.getApplication().service<KSPVersionsService>()
        )
    }

    /**
     * Fetches versions for all available tools.
     * Returns a list of Tool objects with their version channels loaded.
     */
    suspend fun getAllToolsWithVersions(): List<Tool> {
        val tools = mutableListOf<Tool>()
        val services = getAllVersionServices()
        
        for (service in services) {
            try {
                logger.info("Fetching versions for tool: ${service.toolName}")
                val channels = service.getAllVersionChannels()
                tools.add(Tool(service.toolName, service, channels))
                logger.info("Successfully loaded ${channels.size} channels for ${service.toolName}")
            } catch (e: Exception) {
                logger.warn("Failed to load versions for ${service.toolName}", e)
                // Add tool with empty channels in case of error
                tools.add(Tool(service.toolName, service, emptyList()))
            }
        }
        
        return tools
    }

}