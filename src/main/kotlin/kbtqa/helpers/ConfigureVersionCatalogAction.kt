package kbtqa.helpers

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action that creates or validates a Gradle Version Catalog file (libs.versions.toml)
 * when right-clicking on a "gradle" folder in the Project View.
 */
class ConfigureVersionCatalogAction :
    AnAction("Configure Version Catalog", "Create or validate libs.versions.toml file", null),
    DumbAware {
    companion object {
        private const val GRADLE_FOLDER_NAME = "gradle"
        private const val VERSION_CATALOG_FILE_NAME = "libs.versions.toml"
        private val SECTION_ORDER = listOf("versions", "libraries", "bundles", "plugins")
        private val REQUIRED_SECTIONS = SECTION_ORDER.toSet()
        private val DEFAULT_SECTION_TEMPLATES = mapOf(
            "versions" to """kotlin = "2.2.20-Beta2"
ktor = "3.2.3"
""",
            "libraries" to """ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-json = { group = "io.ktor", name = "ktor-client-json", version.ref = "ktor" }
ktor-client-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktor" }
ktor-client-serialization = { group = "io.ktor", name = "ktor-client-serialization", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-client-android = { group = "io.ktor", name = "ktor-client-android", version.ref = "ktor" }
ktor-client-darwin = { group = "io.ktor", name = "ktor-client-darwin", version.ref = "ktor" }
ktor-client-java = { group = "io.ktor", name = "ktor-client-java", version.ref = "ktor" }
ktor-client-js = { group = "io.ktor", name = "ktor-client-js", version.ref = "ktor" }
""",
            "bundles" to """ktor-common = ["ktor-client-core", "ktor-client-json", "ktor-client-logging", "ktor-client-serialization", "ktor-client-content-negotiation"]
""",
            "plugins" to """kmp = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
"""
        )

        private fun generateDefaultCatalogContent(): String {
            return SECTION_ORDER
                .joinToString(separator = "\n\n") { section ->
                    "[${section}]\n${DEFAULT_SECTION_TEMPLATES.getOrDefault(section, "")}"
                }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        val isVisible = e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::isGradleFolder) == true
        e.presentation.isVisible = isVisible
        e.presentation.isEnabled = isVisible
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val gradleFolder = e.getData(CommonDataKeys.VIRTUAL_FILE)?.takeIf(::isGradleFolder) ?: return
        configureVersionCatalog(project, gradleFolder)
    }

    private fun configureVersionCatalog(project: Project, gradleFolder: VirtualFile) {
        WriteCommandAction.runWriteCommandAction(project) {
            val versionCatalogFile = gradleFolder.findChild(VERSION_CATALOG_FILE_NAME)
            if (versionCatalogFile == null) {
                createVersionCatalogFile(gradleFolder)
            } else {
                validateVersionCatalogFile(versionCatalogFile)
            }
        }
    }

    private fun createVersionCatalogFile(gradleFolder: VirtualFile) {
        try {
            val newFile = gradleFolder.createChildData(this, VERSION_CATALOG_FILE_NAME)
            writeText(newFile, generateDefaultCatalogContent())
        } catch (e: Exception) {
            println("Failed to create $VERSION_CATALOG_FILE_NAME: ${e.message}")
        }
    }

    private fun validateVersionCatalogFile(versionCatalogFile: VirtualFile) {
        try {
            val content = readText(versionCatalogFile)
            val missing = REQUIRED_SECTIONS.filterNot { hasSection(content, it) }.toSet()
            if (missing.isNotEmpty()) {
                addMissingSections(versionCatalogFile, content, missing)
            }
        } catch (e: Exception) {
            println("Failed to validate $VERSION_CATALOG_FILE_NAME: ${e.message}")
        }
    }

    private fun hasSection(content: String, sectionName: String): Boolean {
        return "\\[$sectionName\\]".toRegex().find(content) != null
    }

    private fun addMissingSections(
        versionCatalogFile: VirtualFile,
        currentContent: String,
        missingSections: Set<String>
    ) {
        try {
            val sectionsToAdd = SECTION_ORDER
                .filter { it in missingSections }
                .joinToString(separator = "\n\n") { section ->
                    "[${section}]\n${DEFAULT_SECTION_TEMPLATES.getOrDefault(section, "")}"
                }
            val newContent = buildString {
                append(currentContent)
                if (isNotEmpty() && !endsWith('\n')) {
                    append('\n')
                }
                append('\n')
                append(sectionsToAdd)
            }
            writeText(versionCatalogFile, newContent.toString())
        } catch (e: Exception) {
            println("Failed to add missing sections to $VERSION_CATALOG_FILE_NAME: ${e.message}")
        }
    }

    private fun isGradleFolder(vf: VirtualFile): Boolean =
        vf.isDirectory && vf.name == GRADLE_FOLDER_NAME

    private fun readText(file: VirtualFile): String {
        file.refresh(false, false)
        return String(file.contentsToByteArray())
    }

    private fun writeText(file: VirtualFile, text: String) {
        file.getOutputStream(this).use { it.write(text.toByteArray()) }
    }
}