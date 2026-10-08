package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * Action that adds a context menu option for pom.xml files to declare the Kotlin repositories in
 * both `<repositories>` and `<pluginRepositories>`.
 *
 * Existing sections are merged into rather than replaced: a repository whose URL is already
 * declared is left alone, so running the action twice changes nothing.
 */
class ConfigureMavenRepositoriesAction :
    AnAction("Configure Repositories", "Insert Kotlin repository configurations", null), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Always show the action, but enable it only for pom.xml files
        e.presentation.isVisible = true
        e.presentation.isEnabled = MavenProperties.isPomFile(file?.name)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val xmlFile = e.getData(CommonDataKeys.PSI_FILE) as? XmlFile ?: return

        configureRepositories(project, xmlFile)
    }

    private fun configureRepositories(project: Project, xmlFile: XmlFile) {
        if (!xmlFile.isValid) return

        WriteCommandAction.runWriteCommandAction(project, "Configure Repositories", null, {
            val document = PsiDocumentManager.getInstance(project).getDocument(xmlFile) ?: return@runWriteCommandAction
            MavenPomEditing.editKeepingComments(project, xmlFile, document) {
                addMissingRepositories(
                    project,
                    xmlFile,
                    MavenRepositories.REPOSITORIES_TAG,
                    MavenRepositories.REPOSITORY_TAG,
                    MavenRepositories.REPOSITORIES_ANCHORS
                )
                addMissingRepositories(
                    project,
                    xmlFile,
                    MavenRepositories.PLUGIN_REPOSITORIES_TAG,
                    MavenRepositories.PLUGIN_REPOSITORY_TAG,
                    MavenRepositories.PLUGIN_REPOSITORIES_ANCHORS
                )
            }
        }, xmlFile)
    }

    private fun addMissingRepositories(
        project: Project,
        xmlFile: XmlFile,
        sectionTagName: String,
        entryTagName: String,
        anchors: List<String>
    ) {
        val section = MavenPomEditing.findOrCreateRootSection(project, xmlFile, sectionTagName, anchors) ?: return

        val missing = MavenRepositories.missingFrom(declaredUrls(section))
        if (missing.isEmpty()) return

        for (repository in missing) {
            MavenPomEditing.addChildTag(project, section, repository.tagText(entryTagName))
        }
    }

    private fun declaredUrls(section: XmlTag): List<String> =
        section.subTags.mapNotNull { it.findFirstSubTag(MavenRepositories.URL_TAG)?.value?.trimmedText }
}
