package kbtqa.helpers.skills

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the persisted [SkillsSettingsState].
 */
class SkillsSettingsStateTest {

    @Test
    fun `default settings point to the Kotlin agent skills repository`() {
        val state = SkillsSettingsState()
        assertEquals(
            listOf(SkillRepository("Kotlin Agent Skills", "https://github.com/Kotlin/kotlin-agent-skills.git", "skills")),
            state.repositories
        )

        val loaded = SkillsSettingsState().apply { repositories = mutableListOf(SkillRepository(url = "https://example.org/r.git")) }
        state.loadState(loaded)
        assertEquals(loaded.repositories, state.repositories)
        assertEquals("https://example.org/r.git", state.repositories.single().toString())
    }
}
