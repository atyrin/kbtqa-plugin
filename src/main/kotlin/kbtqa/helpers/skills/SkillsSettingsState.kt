package kbtqa.helpers.skills

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent state for skill repository configurations.
 * Stores the list of configured skill repositories across IDE sessions.
 */
@State(
    name = "SkillsSetupSettings",
    storages = [Storage("kbtqaSkillsSetup.xml")]
)
class SkillsSettingsState : PersistentStateComponent<SkillsSettingsState> {

    var repositories: MutableList<SkillRepository> = mutableListOf(
        SkillRepository(
            name = "Kotlin Agent Skills",
            url = "https://github.com/Kotlin/kotlin-agent-skills.git",
            skillsPath = "skills"
        )
    )

    override fun getState(): SkillsSettingsState = this

    override fun loadState(state: SkillsSettingsState) {
        repositories = state.repositories
    }

    companion object {
        fun getInstance(): SkillsSettingsState =
            ApplicationManager.getApplication().getService(SkillsSettingsState::class.java)
    }
}
