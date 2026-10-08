package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.SimpleListCellRenderer
import kbtqa.helpers.editor.MavenKotlinPlugin.ARTIFACT_ID_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.BUILD_ANCHORS
import kbtqa.helpers.editor.MavenKotlinPlugin.BUILD_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.CONFIGURATION_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.DEPENDENCIES_ANCHORS
import kbtqa.helpers.editor.MavenKotlinPlugin.DEPENDENCIES_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.DEPENDENCY_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.EXECUTIONS_ANCHORS
import kbtqa.helpers.editor.MavenKotlinPlugin.EXECUTIONS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.EXTENSIONS_ANCHORS
import kbtqa.helpers.editor.MavenKotlinPlugin.EXTENSIONS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.EXTENSIONS_TAG_TEXT
import kbtqa.helpers.editor.MavenKotlinPlugin.GROUP_ID_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.ID_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.KOTLIN_VERSION_PROPERTY
import kbtqa.helpers.editor.MavenKotlinPlugin.KOTLIN_VERSION_REFERENCE
import kbtqa.helpers.editor.MavenKotlinPlugin.PARENT_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.PLUGINS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.SOURCE_DIRECTORY_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.SOURCE_DIRS_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.TEST_SOURCE_DIRECTORY_TAG
import kbtqa.helpers.editor.MavenKotlinPlugin.VERSION_TAG
import org.jetbrains.annotations.VisibleForTesting

/**
 * Action that adds a context menu option for pom.xml files to set up the Kotlin Maven plugin in one
 * of the three ways the Kotlin docs describe: smart defaults, manual Kotlin-only, or manual Kotlin
 * plus Java. [MavenKotlinPlugin] lists what a mode rewrites; everything else in the pom is kept.
 *
 * Versions the pom already declares are kept. A missing Kotlin plugin is added with
 * `${kotlin.version}`; a standalone pom that does not declare the property gets it empty, with the
 * caret in it, for the user to fill in. The manual modes add `kotlin-stdlib`, which smart defaults
 * would otherwise provide, at the plugin's version; the Kotlin + Java one declares a missing Maven
 * compiler at its latest stable Maven 3 release.
 */
class ConfigureKotlinPluginAction :
    AnAction("Configure Kotlin Plugin", "Set up the Kotlin Maven plugin with smart defaults or manually", null),
    DumbAware {

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

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(KotlinPluginMode.entries)
            .setTitle("Configure Kotlin Plugin")
            .setRenderer(SimpleListCellRenderer.create("") { it.label })
            .setItemChosenCallback { mode -> onModeChosen(project, xmlFile, editor, mode) }
            .createPopup()
            .showInBestPositionFor(e.dataContext)
    }

    private fun onModeChosen(project: Project, xmlFile: XmlFile, editor: Editor?, mode: KotlinPluginMode) {
        val declaresCompilerPlugin = MavenPomEditing.findBuildPlugin(xmlFile, MavenKotlinPlugin::isMavenCompilerPlugin) != null
        if (mode != KotlinPluginMode.KOTLIN_AND_JAVA || declaresCompilerPlugin) {
            applyMode(project, xmlFile, editor, mode)
            return
        }

        // The Maven compiler is about to be declared, so look up its latest version first
        MavenPluginVersions.lookUpLatest(
            project,
            MavenKotlinPlugin.COMPILER_PLUGIN_ARTIFACT_ID,
            MavenKotlinPlugin.COMPILER_PLUGIN_FALLBACK_VERSION
        ) { version -> applyMode(project, xmlFile, editor, mode, version) }
    }

    /**
     * What choosing [mode] in the popup does; [compilerPluginVersion] is only used when the Kotlin +
     * Java setup has to declare the Maven compiler.
     */
    @VisibleForTesting
    internal fun applyMode(
        project: Project,
        xmlFile: XmlFile,
        editor: Editor?,
        mode: KotlinPluginMode,
        compilerPluginVersion: String = MavenKotlinPlugin.COMPILER_PLUGIN_FALLBACK_VERSION
    ) {
        if (!xmlFile.isValid) return

        WriteCommandAction.runWriteCommandAction(project, "Configure Kotlin Plugin", null, {
            val document = PsiDocumentManager.getInstance(project).getDocument(xmlFile) ?: return@runWriteCommandAction
            val addedVersionProperty = MavenPomEditing.editKeepingComments(project, xmlFile, document) {
                configure(project, xmlFile, mode, compilerPluginVersion)
            }

            if (addedVersionProperty && editor != null) {
                // The version is left for the user to fill in, so that is where the caret goes
                xmlFile.rootTag?.findFirstSubTag(MavenProperties.PROPERTIES_TAG)
                    ?.findFirstSubTag(KOTLIN_VERSION_PROPERTY)
                    ?.let { MavenPomEditing.moveCaretIntoTag(editor, it) }
            }
        }, xmlFile)
    }

    /** Applies [mode] to the pom and returns whether an empty `kotlin.version` property was added. */
    private fun configure(project: Project, xmlFile: XmlFile, mode: KotlinPluginMode, compilerPluginVersion: String): Boolean {
        val root = xmlFile.rootTag ?: return false

        // Decide everything up front: separating a created root section with a blank line edits the
        // document directly, and the reparse that follows invalidates held PSI
        val existingPlugin = MavenPomEditing.findBuildPlugin(xmlFile, MavenKotlinPlugin::isKotlinMavenPlugin)
        val addStdlib = MavenKotlinPlugin.requiresStdlib(mode) && !hasStdlib(root)
        val stdlibVersion = MavenKotlinPlugin.kotlinArtifactVersion(
            existingPlugin?.let { MavenPomEditing.childText(it, VERSION_TAG) }
        )
        val writesVersionReference =
            existingPlugin == null || (addStdlib && stdlibVersion == KOTLIN_VERSION_REFERENCE)
        val addVersionProperty = writesVersionReference && MavenKotlinPlugin.needsKotlinVersionProperty(
            declaresProperty = root.findFirstSubTag(MavenProperties.PROPERTIES_TAG)
                ?.findFirstSubTag(KOTLIN_VERSION_PROPERTY) != null,
            hasParent = root.findFirstSubTag(PARENT_TAG) != null
        )

        if (addVersionProperty) {
            addToRootSection(
                project, xmlFile, MavenProperties.PROPERTIES_TAG, MavenProperties.PROPERTIES_ANCHORS,
                MavenProperty(KOTLIN_VERSION_PROPERTY).tagText
            )
        }
        if (addStdlib) {
            addToRootSection(
                project, xmlFile, DEPENDENCIES_TAG, DEPENDENCIES_ANCHORS,
                MavenKotlinPlugin.stdlibTagText(stdlibVersion)
            )
        }
        configureBuild(project, xmlFile, mode, compilerPluginVersion)
        return addVersionProperty
    }

    private fun addToRootSection(
        project: Project,
        xmlFile: XmlFile,
        sectionName: String,
        anchors: List<String>,
        tagText: String
    ) {
        val section = MavenPomEditing.findOrCreateRootSection(project, xmlFile, sectionName, anchors) ?: return
        MavenPomEditing.addChildTag(project, section, tagText)
    }

    private fun configureBuild(project: Project, xmlFile: XmlFile, mode: KotlinPluginMode, compilerPluginVersion: String) {
        val build = MavenPomEditing.findOrCreateRootSection(project, xmlFile, BUILD_TAG, BUILD_ANCHORS) ?: return
        val plugins = MavenPomEditing.findOrCreateChild(project, build, PLUGINS_TAG, anchors = emptyList()) ?: return

        var compilerPlugin = MavenPomEditing.findPluginIn(plugins, MavenKotlinPlugin::isMavenCompilerPlugin)
        var kotlinPlugin = MavenPomEditing.findPluginIn(plugins, MavenKotlinPlugin::isKotlinMavenPlugin)
            ?: addKotlinPlugin(project, plugins, before = compilerPlugin)
            ?: return

        if (mode == KotlinPluginMode.KOTLIN_AND_JAVA) {
            // Kotlin has to compile first, so that the Java code can see the Kotlin classes
            if (compilerPlugin == null) {
                compilerPlugin = MavenPomEditing.addChildTagAfter(
                    project, plugins, kotlinPlugin, MavenKotlinPlugin.compilerPluginTagText(compilerPluginVersion)
                )
            } else {
                kotlinPlugin = moveBefore(plugins, kotlinPlugin, compilerPlugin)
            }
        }

        applyExtensions(project, kotlinPlugin, mode)
        replaceOwnedExecutions(project, kotlinPlugin, ::isOwnedKotlinExecution, MavenKotlinPlugin.kotlinExecutions(mode))
        compilerPlugin?.let {
            replaceOwnedExecutions(project, it, ::isOwnedCompilerExecution, MavenKotlinPlugin.compilerExecutions(mode))
        }
        applySourceDirectories(project, build, mode)
    }

    private fun addKotlinPlugin(project: Project, plugins: XmlTag, before: XmlTag?): XmlTag? =
        if (before != null) {
            MavenPomEditing.addChildTagBefore(project, plugins, before, MavenKotlinPlugin.KOTLIN_PLUGIN_TAG_TEXT)
        } else {
            MavenPomEditing.addChildTag(project, plugins, MavenKotlinPlugin.KOTLIN_PLUGIN_TAG_TEXT)
        }

    /** Returns [plugin] moved in front of [anchor] when it came after it; the moved copy replaces the original. */
    private fun moveBefore(plugins: XmlTag, plugin: XmlTag, anchor: XmlTag): XmlTag {
        val order = plugins.subTags
        if (order.indexOf(plugin) < order.indexOf(anchor)) return plugin

        val moved = plugins.addBefore(plugin.copy(), anchor) as? XmlTag ?: return plugin
        MavenPomEditing.deleteTag(plugin)
        return moved
    }

    private fun applyExtensions(project: Project, plugin: XmlTag, mode: KotlinPluginMode) {
        val extensions = plugin.findFirstSubTag(EXTENSIONS_TAG)
        when {
            !MavenKotlinPlugin.usesExtensions(mode) -> extensions?.let(MavenPomEditing::deleteTag)
            extensions == null -> MavenPomEditing.insertChild(project, plugin, EXTENSIONS_TAG_TEXT, EXTENSIONS_ANCHORS)
            !extensions.value.trimmedText.equals("true", ignoreCase = true) -> extensions.value.text = "true"
        }
    }

    /**
     * Drops the executions [isOwned] claims for the modes, then adds the ones [recipe] lists. What
     * the dropped executions configured is lifted to the plugin level first.
     */
    private fun replaceOwnedExecutions(
        project: Project,
        plugin: XmlTag,
        isOwned: (XmlTag) -> Boolean,
        recipe: List<String>
    ) {
        val existing = plugin.findFirstSubTag(EXECUTIONS_TAG)
        val owned = existing?.subTags?.filter(isOwned).orEmpty()
        liftConfiguration(project, plugin, owned)
        owned.forEach(MavenPomEditing::deleteTag)

        if (recipe.isEmpty()) {
            // Leave no empty <executions> behind, but keep one that still holds, say, kapt
            if (existing != null && existing.subTags.isEmpty()) MavenPomEditing.deleteTag(existing)
            return
        }

        val executions = MavenPomEditing.findOrCreateChild(project, plugin, EXECUTIONS_TAG, EXECUTIONS_ANCHORS) ?: return
        recipe.forEach { MavenPomEditing.addChildTag(project, executions, it) }
    }

    /**
     * Copies the settings of [executions], except the `sourceDirs` the modes manage, into the
     * plugin's own `<configuration>`, so that compiler arguments and the like survive replacing
     * them. A setting already present at the plugin level wins, and so does the first execution
     * when several set the same thing.
     */
    private fun liftConfiguration(project: Project, plugin: XmlTag, executions: List<XmlTag>) {
        val settings = executions
            .flatMap { it.findFirstSubTag(CONFIGURATION_TAG)?.subTags.orEmpty().asList() }
            .filter { it.name != SOURCE_DIRS_TAG }
        if (settings.isEmpty()) return

        val configuration = MavenPomEditing.findOrCreateChild(project, plugin, CONFIGURATION_TAG, anchors = emptyList())
            ?: return
        for (setting in settings) {
            if (configuration.findFirstSubTag(setting.name) == null) {
                configuration.addSubTag(setting.copy() as XmlTag, false)
            }
        }
    }

    private fun applySourceDirectories(project: Project, build: XmlTag, mode: KotlinPluginMode) {
        val wanted = MavenKotlinPlugin.sourceDirectories(mode).toMap()
        for (name in listOf(SOURCE_DIRECTORY_TAG, TEST_SOURCE_DIRECTORY_TAG)) {
            val existing = build.findFirstSubTag(name)
            val value = wanted[name]
            when {
                // Only the Kotlin directories belong to a mode; a custom one is the user's
                existing != null && value == null && MavenKotlinPlugin.isKotlinSourceDirectory(existing.value.trimmedText) ->
                    MavenPomEditing.deleteTag(existing)

                existing == null && value != null -> {
                    val anchors = if (name == TEST_SOURCE_DIRECTORY_TAG) listOf(SOURCE_DIRECTORY_TAG) else emptyList()
                    MavenPomEditing.insertChild(project, build, "<$name>$value</$name>", anchors, appendWhenNoAnchor = false)
                }
            }
        }
    }

    private fun isOwnedKotlinExecution(execution: XmlTag): Boolean =
        MavenKotlinPlugin.isOwnedKotlinExecution(MavenPomEditing.goalsOf(execution))

    private fun isOwnedCompilerExecution(execution: XmlTag): Boolean =
        MavenKotlinPlugin.isOwnedCompilerExecution(MavenPomEditing.childText(execution, ID_TAG))

    private fun hasStdlib(root: XmlTag): Boolean =
        root.findFirstSubTag(DEPENDENCIES_TAG)?.findSubTags(DEPENDENCY_TAG).orEmpty().any {
            MavenKotlinPlugin.isKotlinStdlib(
                MavenPomEditing.childText(it, GROUP_ID_TAG),
                MavenPomEditing.childText(it, ARTIFACT_ID_TAG)
            )
        }
}
