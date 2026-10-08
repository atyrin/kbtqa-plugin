package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import kbtqa.helpers.editor.MavenKotlinPlugin.BUILD_ANCHORS
import kbtqa.helpers.editor.MavenKotlinPlugin.BUILD_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.CONFIGURATION_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.EXECUTIONS_ANCHORS
import kbtqa.helpers.editor.MavenKotlinPlugin.EXECUTIONS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.PLUGINS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.VERSION_TAG
import org.jetbrains.annotations.VisibleForTesting

/**
 * Action that adds a context menu option for pom.xml files to pin the compilation JDK with
 * `maven-toolchains-plugin`, as the Kotlin docs describe under "Set JDK version"; the Kotlin plugin
 * picks the selected toolchain up as well.
 *
 * Like the other helpers, it adds what is missing and keeps what is there: the plugin (at its latest
 * stable Maven 3 release), the `toolchain` execution, and `<toolchains><jdk><version>`, which is left
 * empty with the caret in it for the user to fill in. The JDKs themselves still have to be described
 * in a `toolchains.xml`.
 */
class ConfigureToolchainAction :
    AnAction("Configure JDK Toolchain", "Pin the compilation JDK with maven-toolchains-plugin", null), DumbAware {

    companion object {
        private const val ARTIFACT_ID = "maven-toolchains-plugin"

        /** Used when the latest version cannot be looked up: the newest stable 3.x release at the time of writing. */
        private const val FALLBACK_VERSION = "3.3.0"

        private const val TOOLCHAIN_GOAL = "toolchain"
        private const val TOOLCHAINS_TAG = "toolchains"
        private const val JDK_TAG = "jdk"
        private const val TOOLCHAIN_EXECUTION_TAG_TEXT = "<execution><goals><goal>$TOOLCHAIN_GOAL</goal></goals></execution>"
    }

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
        val editor = e.getData(CommonDataKeys.EDITOR)

        if (MavenPomEditing.findBuildPlugin(xmlFile, ::isToolchainsPlugin) != null) {
            configureToolchain(project, xmlFile, editor, FALLBACK_VERSION)
            return
        }
        // The plugin is about to be declared, so look up its latest version first
        MavenPluginVersions.lookUpLatest(project, ARTIFACT_ID, FALLBACK_VERSION) { version ->
            configureToolchain(project, xmlFile, editor, version)
        }
    }

    /** [pluginVersion] is only used when the plugin has to be declared; an existing one keeps its own. */
    @VisibleForTesting
    internal fun configureToolchain(project: Project, xmlFile: XmlFile, editor: Editor?, pluginVersion: String) {
        if (!xmlFile.isValid) return

        WriteCommandAction.runWriteCommandAction(project, "Configure JDK Toolchain", null, {
            val document = PsiDocumentManager.getInstance(project).getDocument(xmlFile) ?: return@runWriteCommandAction
            MavenPomEditing.editKeepingComments(project, xmlFile, document) {
                addMissingParts(project, xmlFile, pluginVersion)
            }

            if (editor != null) {
                // An empty version is the user's to fill in, and an existing one is worth seeing either way
                MavenPomEditing.findBuildPlugin(xmlFile, ::isToolchainsPlugin)
                    ?.let { findJdk(it) }
                    ?.findFirstSubTag(VERSION_TAG)
                    ?.let { MavenPomEditing.moveCaretIntoTag(editor, it) }
            }
        }, xmlFile)
    }

    private fun addMissingParts(project: Project, xmlFile: XmlFile, pluginVersion: String) {
        val build = MavenPomEditing.findOrCreateRootSection(project, xmlFile, BUILD_TAG, BUILD_ANCHORS) ?: return
        val plugins = MavenPomEditing.findOrCreateChild(project, build, PLUGINS_TAG, anchors = emptyList()) ?: return
        val plugin = MavenPomEditing.findPluginIn(plugins, ::isToolchainsPlugin)
            ?: MavenPomEditing.addChildTag(
                project, plugins,
                MavenKotlinPlugin.pluginTagText(MavenKotlinPlugin.APACHE_PLUGINS_GROUP_ID, ARTIFACT_ID, pluginVersion)
            )

        if (!hasToolchainExecution(plugin)) {
            val executions = MavenPomEditing.findOrCreateChild(project, plugin, EXECUTIONS_TAG, EXECUTIONS_ANCHORS) ?: return
            MavenPomEditing.addChildTag(project, executions, TOOLCHAIN_EXECUTION_TAG_TEXT)
        }
        val jdk = findOrCreateJdk(project, plugin) ?: return
        if (jdk.findFirstSubTag(VERSION_TAG) == null) {
            MavenPomEditing.insertChild(project, jdk, "<$VERSION_TAG></$VERSION_TAG>", anchors = emptyList(), appendWhenNoAnchor = false)
        }
    }

    private fun findOrCreateJdk(project: Project, plugin: XmlTag): XmlTag? {
        val configuration = MavenPomEditing.findOrCreateChild(project, plugin, CONFIGURATION_TAG, anchors = emptyList()) ?: return null
        val toolchains = MavenPomEditing.findOrCreateChild(project, configuration, TOOLCHAINS_TAG, anchors = emptyList()) ?: return null
        return MavenPomEditing.findOrCreateChild(project, toolchains, JDK_TAG, anchors = emptyList())
    }

    private fun findJdk(plugin: XmlTag): XmlTag? =
        plugin.findFirstSubTag(CONFIGURATION_TAG)
            ?.findFirstSubTag(TOOLCHAINS_TAG)
            ?.findFirstSubTag(JDK_TAG)

    private fun hasToolchainExecution(plugin: XmlTag): Boolean =
        plugin.findFirstSubTag(EXECUTIONS_TAG)?.subTags.orEmpty().any {
            TOOLCHAIN_GOAL in MavenPomEditing.goalsOf(it)
        }

    private fun isToolchainsPlugin(groupId: String?, artifactId: String?): Boolean =
        MavenKotlinPlugin.isApacheMavenPlugin(groupId, artifactId, ARTIFACT_ID)
}
