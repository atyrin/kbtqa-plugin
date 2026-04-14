package kbtqa.helpers.skills

/**
 * Data model for a skill repository configuration.
 * Each repository points to a git repo containing agent skills in a specific subdirectory.
 */
data class SkillRepository(
    var name: String = "",
    var url: String = "",
    var skillsPath: String = ""
) {
    override fun toString(): String = name.ifEmpty { url }
}
