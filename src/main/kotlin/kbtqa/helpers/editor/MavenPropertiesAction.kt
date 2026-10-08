package kbtqa.helpers.editor

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import kbtqa.helpers.editor.MavenKotlinPlugin.CONFIGURATION_TAG
import org.jetbrains.annotations.VisibleForTesting

/**
 * Action that adds a context menu option for pom.xml files to insert Kotlin and Maven compiler
 * options, grouped as [MavenProperties.GROUPS] lists them.
 *
 * Properties go into the `<properties>` tag enclosing the caret, falling back to the one in the root
 * `<project>` tag, which is created when it does not exist yet. Options that have no property
 * counterpart go into the `<configuration>` of `kotlin-maven-plugin` instead; when the pom does not
 * declare that plugin, a hint points to _Configure Kotlin Plugin_. Compiler plugins have an action of
 * their own, [AddKotlinCompilerPluginAction].
 */
class MavenPropertiesAction :
    AnAction("Add Maven Property", "Insert a Kotlin or Maven compiler option", null), DumbAware {

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

        showPropertiesPopup(project, editor, xmlFile, e.dataContext)
    }

    private fun showPropertiesPopup(project: Project, editor: Editor, xmlFile: XmlFile, dataContext: DataContext) {
        // Create and show popup that recreates itself after each selection
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(MavenProperties.ENTRIES)
            .setTitle("Select Maven Property")
            .setResizable(true)
            .setRenderer(GroupedListRenderer<MavenOption>({ it.label }, { MavenProperties.GROUP_TITLES.getValue(it) }))
            .setItemChosenCallback { option ->
                val inserted = when (option) {
                    is MavenProperty -> {
                        insertProperty(project, editor, xmlFile, option)
                        true
                    }
                    is KotlinPluginOption -> insertPluginOption(project, editor, xmlFile, option)
                }
                if (inserted) {
                    // Recreate the popup after insertion to keep it open
                    showPropertiesPopup(project, editor, xmlFile, dataContext)
                } else {
                    HintManager.getInstance().showErrorHint(editor, MavenKotlinPlugin.NOT_DECLARED_HINT)
                }
            }
            .createPopup()
            .showInBestPositionFor(dataContext)
    }

    /** What choosing [property] in the popup does. */
    @VisibleForTesting
    internal fun insertProperty(project: Project, editor: Editor, xmlFile: XmlFile, property: MavenProperty) {
        if (!xmlFile.isValid) return

        WriteCommandAction.runWriteCommandAction(project, "Add Maven Property", null, {
            val target = MavenPomEditing.editKeepingComments(project, xmlFile, editor.document) {
                val properties = findOrCreatePropertiesTag(project, editor, xmlFile) ?: return@editKeepingComments null
                // Already declared here — take the user to it instead of adding a duplicate
                val tag = properties.findFirstSubTag(property.name)
                    ?: MavenPomEditing.addChildTag(project, properties, property.tagText)
                // Formatting the inserted tag may reparse it, so keep hold of the caret target through a pointer
                SmartPointerManager.createPointer(tag)
            }
            target?.element?.let { MavenPomEditing.moveCaretIntoTag(editor, it) }
        }, xmlFile)
    }

    /**
     * What choosing [option] in the popup does. Returns `false`, changing nothing, when the pom does
     * not declare `kotlin-maven-plugin`.
     */
    @VisibleForTesting
    internal fun insertPluginOption(project: Project, editor: Editor, xmlFile: XmlFile, option: KotlinPluginOption): Boolean =
        MavenPomEditing.editKotlinPlugin(project, editor, xmlFile, "Add Maven Property") {
            addPluginOption(project, it, option)
        }

    /**
     * Adds [option] to the plugin's `<configuration>` unless something already sets it, and returns
     * the tag that takes the caret.
     */
    private fun addPluginOption(project: Project, plugin: XmlTag, option: KotlinPluginOption): XmlTag? {
        val configuration = MavenPomEditing.findOrCreateChild(project, plugin, CONFIGURATION_TAG, anchors = emptyList())
            ?: return null
        val parent = if (option.container == null) {
            configuration
        } else {
            MavenPomEditing.findOrCreateChild(project, configuration, option.container, anchors = emptyList()) ?: return null
        }

        val tag = parent.findSubTags(option.tagName).firstOrNull { option.isSetBy(it.value.text) }
            ?: MavenPomEditing.addChildTag(project, parent, option.tagText)
        return option.caretTag?.let { tag.findFirstSubTag(it) } ?: tag
    }

    /**
     * Returns the `<properties>` tag enclosing the caret (so a `<profile>` section is honoured),
     * the one in the root `<project>` tag, or a newly created one.
     */
    private fun findOrCreatePropertiesTag(project: Project, editor: Editor, xmlFile: XmlFile): XmlTag? {
        enclosingPropertiesTag(xmlFile, editor.caretModel.offset)?.let {
            return MavenPomEditing.expandIfSelfClosed(project, it, MavenProperties.PROPERTIES_TAG)
        }
        return MavenPomEditing.findOrCreateRootSection(
            project,
            xmlFile,
            MavenProperties.PROPERTIES_TAG,
            MavenProperties.PROPERTIES_ANCHORS
        )
    }

    private fun enclosingPropertiesTag(xmlFile: XmlFile, caretOffset: Int): XmlTag? {
        // findElementAt returns null at the very end of the file
        val leaf = xmlFile.findElementAt(caretOffset)
            ?: xmlFile.findElementAt((caretOffset - 1).coerceAtLeast(0))
            ?: return null

        var tag = PsiTreeUtil.getParentOfType(leaf, XmlTag::class.java, false)
        while (tag != null) {
            if (tag.name == MavenProperties.PROPERTIES_TAG) return tag
            tag = tag.parentTag
        }
        return null
    }
}
