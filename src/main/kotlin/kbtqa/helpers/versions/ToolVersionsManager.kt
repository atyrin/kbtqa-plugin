package kbtqa.helpers.versions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Manager service that coordinates multiple tool version services.
 * Provides a unified interface for fetching versions from different tools.
 */
@Service(Service.Level.APP)
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
     * Gets all available tool version services lazily.
     * Services are only instantiated when first accessed.
     */
    fun getAllVersionServices(): List<Lazy<VersionsService>> {
        return listOf(
            lazy { ApplicationManager.getApplication().service<KotlinVersionsService>() },
            lazy { ApplicationManager.getApplication().service<AndroidVersionsService>() },
            lazy { ApplicationManager.getApplication().service<KSPVersionsService>() },
            lazy { ApplicationManager.getApplication().service<DokkaVersionsService>() }
        )
    }

    /**
     * Gets all available tools without loading their versions.
     * Returns a list of Tool objects with empty channels that can be loaded on-demand.
     */
    fun getAllToolsWithoutVersions(): List<Tool> {
        val tools = mutableListOf<Tool>()
        val lazyServices = getAllVersionServices()
        
        for (lazyService in lazyServices) {
            try {
                // Only instantiate the service to get its name, but don't fetch versions
                val service = lazyService.value
                tools.add(Tool(service.toolName, service, emptyList()))
                logger.info("Added tool without versions: ${service.toolName}")
            } catch (e: Exception) {
                logger.warn("Failed to instantiate service", e)
            }
        }
        
        return tools
    }

    /**
     * Fetches versions for a specific tool on-demand.
     * Returns a new Tool object with loaded version channels.
     */
    suspend fun fetchVersionsForTool(tool: Tool): Tool {
        return try {
            logger.info("Fetching versions for tool: ${tool.name}")
            val channels = tool.service.getAllVersionChannels()
            val updatedTool = Tool(tool.name, tool.service, channels)
            logger.info("Successfully loaded ${channels.size} channels for ${tool.name}")
            updatedTool
        } catch (e: Exception) {
            logger.warn("Failed to load versions for ${tool.name}", e)
            // Return tool with empty channels in case of error
            Tool(tool.name, tool.service, emptyList())
        }
    }

    /**
     * Fetches versions for all available tools.
     * Returns a list of Tool objects with their version channels loaded.
     */
    suspend fun getAllToolsWithVersions(): List<Tool> {
        val tools = mutableListOf<Tool>()
        val lazyServices = getAllVersionServices()
        
        for (lazyService in lazyServices) {
            try {
                // Lazy service is only instantiated here when .value is accessed
                val service = lazyService.value
                logger.info("Fetching versions for tool: ${service.toolName}")
                val channels = service.getAllVersionChannels()
                tools.add(Tool(service.toolName, service, channels))
                logger.info("Successfully loaded ${channels.size} channels for ${service.toolName}")
            } catch (e: Exception) {
                // Service instantiation might fail, so handle it gracefully
                val service = try {
                    lazyService.value
                } catch (serviceException: Exception) {
                    logger.warn("Failed to instantiate service", serviceException)
                    null
                }
                
                val serviceName = service?.toolName ?: "Unknown Service"
                logger.warn("Failed to load versions for $serviceName", e)
                
                // Add tool with empty channels in case of error (only if service could be instantiated)
                if (service != null) {
                    tools.add(Tool(service.toolName, service, emptyList()))
                }
            }
        }
        
        return tools
    }

}