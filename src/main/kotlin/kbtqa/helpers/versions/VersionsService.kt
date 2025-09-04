package kbtqa.helpers.versions

/**
 * Interface for services that retrieve versions for different tools.
 */
interface VersionsService {
    
    /**
     * Represents a channel of versions for a tool.
     */
    data class VersionChannel(
        val name: String,
        val description: String,
        val versions: List<String>
    )
    
    /**
     * The name of the tool this service handles (e.g., "Kotlin", "Android").
     */
    val toolName: String
    
    /**
     * Retrieves versions from all channels for this tool.
     */
    suspend fun getAllVersionChannels(): List<VersionChannel>
}