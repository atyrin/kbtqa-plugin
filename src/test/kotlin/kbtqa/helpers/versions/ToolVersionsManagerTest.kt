package kbtqa.helpers.versions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Tests for [ToolVersionsManager.fetchVersionsForTool].
 */
class ToolVersionsManagerTest {

    private class FakeVersionsService(
        private val result: () -> List<VersionsService.VersionChannel>
    ) : VersionsService {
        override val toolName: String = "Fake"
        override suspend fun getAllVersionChannels(): List<VersionsService.VersionChannel> = result()
    }

    @Test
    fun `fetched channels are attached to the tool`() {
        val channels = listOf(VersionsService.VersionChannel("Stable", "Stable releases", listOf("2.0.0", "1.9.0")))
        val service = FakeVersionsService { channels }

        val tool = runBlocking {
            ToolVersionsManager().fetchVersionsForTool(ToolVersionsManager.Tool("Fake", service))
        }

        assertEquals("Fake", tool.name)
        assertSame(service, tool.service)
        assertEquals(channels, tool.channels)
    }

    @Test
    fun `failing service yields a tool without channels`() {
        val service = FakeVersionsService { throw IllegalStateException("network down") }
        val previous = listOf(VersionsService.VersionChannel("Old", "Stale", listOf("1.0")))

        val tool = runBlocking {
            ToolVersionsManager().fetchVersionsForTool(ToolVersionsManager.Tool("Fake", service, previous))
        }

        assertEquals("Fake", tool.name)
        assertEquals(emptyList<VersionsService.VersionChannel>(), tool.channels)
    }
}
