package kbtqa.helpers.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.SimpleListCellRenderer
import kbtqa.helpers.editor.MavenKotlinPlugin.ARTIFACT_ID_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.COMPILER_PLUGINS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.CONFIGURATION_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.DEPENDENCIES_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.DEPENDENCY_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.GROUP_ID
import kbtqa.helpers.editor.MavenKotlinPlugin.GROUP_ID_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.OPTION_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.PLUGIN_DEPENDENCIES_ANCHORS
import kbtqa.helpers.editor.MavenKotlinPlugin.PLUGIN_OPTIONS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.PLUGIN_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.VERSION_TAG
import org.jetbrains.annotations.VisibleForTesting

/**
 * Action that adds a context menu option for pom.xml files to enable one of the Kotlin compiler
 * plugins [KotlinCompilerPlugins] lists in `kotlin-maven-plugin`.
 *
 * Enabling one takes three parts, each added only when missing: the plugin's artifact as a dependency
 * of `kotlin-maven-plugin`, at the Kotlin plugin's version; its name in `<compilerPlugins>`; and, for
 * plugins that do nothing without one, an example `<pluginOptions>` entry with the caret in it. When
 * the pom does not declare `kotlin-maven-plugin`, a hint points to _Configure Kotlin Plugin_.
 */
class AddKotlinCompilerPluginAction :
    AnAction("Add Kotlin Compiler Plugin", "Enable a Kotlin compiler plugin in kotlin-maven-plugin", null), DumbAware {

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
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val xmlFile = e.getData(CommonDataKeys.PSI_FILE) as? XmlFile ?: return

        showPluginsPopup(project, editor, xmlFile, e.dataContext)
    }

    private fun showPluginsPopup(project: Project, editor: Editor, xmlFile: XmlFile, dataContext: DataContext) {
        // Create and show popup that recreates itself after each selection
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(KotlinCompilerPlugins.ENTRIES)
            .setTitle("Select Kotlin Compiler Plugin")
            .setRenderer(SimpleListCellRenderer.create("") { it.name })
            .setItemChosenCallback { compilerPlugin ->
                if (enableCompilerPlugin(project, editor, xmlFile, compilerPlugin)) {
                    showPluginsPopup(project, editor, xmlFile, dataContext)
                } else {
                    HintManager.getInstance().showErrorHint(editor, MavenKotlinPlugin.NOT_DECLARED_HINT)
                }
            }
            .createPopup()
            .showInBestPositionFor(dataContext)
    }

    /**
     * What choosing [compilerPlugin] in the popup does. Returns `false`, changing nothing, when the
     * pom does not declare `kotlin-maven-plugin`.
     */
    @VisibleForTesting
    internal fun enableCompilerPlugin(project: Project, editor: Editor, xmlFile: XmlFile, compilerPlugin: KotlinCompilerPlugin): Boolean =
        MavenPomEditing.editKotlinPlugin(project, editor, xmlFile, "Add Kotlin Compiler Plugin") {
            addCompilerPlugin(project, it, compilerPlugin)
        }

    /**
     * Adds the missing parts of [compilerPlugin] to [plugin] and returns the tag that takes the caret:
     * the option, which is meant to be edited, or else the name.
     */
    private fun addCompilerPlugin(project: Project, plugin: XmlTag, compilerPlugin: KotlinCompilerPlugin): XmlTag? {
        // Without the artifact on the plugin's classpath the name in <compilerPlugins> does not resolve
        val dependencies = MavenPomEditing.findOrCreateChild(project, plugin, DEPENDENCIES_TAG, PLUGIN_DEPENDENCIES_ANCHORS)
            ?: return null
        val declared = dependencies.findSubTags(DEPENDENCY_TAG).any {
            MavenPomEditing.childText(it, GROUP_ID_TAG) == GROUP_ID &&
                MavenPomEditing.childText(it, ARTIFACT_ID_TAG) == compilerPlugin.artifactId
        }
        if (!declared) {
            // The compiler plugin has to match the compiler, so it follows the Kotlin plugin's version
            val version = MavenKotlinPlugin.kotlinArtifactVersion(MavenPomEditing.childText(plugin, VERSION_TAG))
            MavenPomEditing.addChildTag(
                project, dependencies, MavenKotlinPlugin.kotlinDependencyTagText(compilerPlugin.artifactId, version)
            )
        }

        val configuration = MavenPomEditing.findOrCreateChild(project, plugin, CONFIGURATION_TAG, anchors = emptyList())
            ?: return null
        val names = MavenPomEditing.findOrCreateChild(project, configuration, COMPILER_PLUGINS_TAG, anchors = emptyList())
            ?: return null
        val nameTag = names.findSubTags(PLUGIN_TAG).firstOrNull { it.value.trimmedText == compilerPlugin.name }
            ?: MavenPomEditing.addChildTag(project, names, "<$PLUGIN_TAG>${compilerPlugin.name}</$PLUGIN_TAG>")

        // An option for a plugin missing from <compilerPlugins> fails the build, hence only after the name
        val optionText = compilerPlugin.optionText ?: return nameTag
        val options = MavenPomEditing.findOrCreateChild(project, configuration, PLUGIN_OPTIONS_TAG, anchors = emptyList())
            ?: return nameTag
        return options.findSubTags(OPTION_TAG).firstOrNull { compilerPlugin.isOptionSetBy(it.value.text) }
            ?: MavenPomEditing.addChildTag(project, options, "<$OPTION_TAG>$optionText</$OPTION_TAG>")
    }
}
