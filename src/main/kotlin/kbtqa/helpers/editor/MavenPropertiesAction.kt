package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

/**
 * Action that adds a context menu option for pom.xml files to insert Kotlin compiler properties
 * into the `<properties>` section.
 *
 * The property is added to the `<properties>` tag enclosing the caret, falling back to the one in
 * the root `<project>` tag, which is created when it does not exist yet.
 */
class MavenPropertiesAction :
    AnAction("Add Maven Property", "Insert a Kotlin Maven compiler property", null), DumbAware {

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
            .setRenderer(EntryRenderer(MavenProperties.ENTRIES_WITH_SEPARATOR_ABOVE))
            .setItemChosenCallback { property ->
                insertProperty(project, editor, xmlFile, property)
                // Recreate the popup after insertion to keep it open
                showPropertiesPopup(project, editor, xmlFile, dataContext)
            }
            .createPopup()
            .showInBestPositionFor(dataContext)
    }

    /** Visible for testing: the whole document mutation, independent of the popup. */
    internal fun insertProperty(project: Project, editor: Editor, xmlFile: XmlFile, property: MavenProperty) {
        if (!xmlFile.isValid) return

        WriteCommandAction.runWriteCommandAction(project, "Add Maven Property", null, {
            val documentManager = PsiDocumentManager.getInstance(project)
            val document = editor.document
            // Make the PSI tree match what the user sees before reading it
            documentManager.commitDocument(document)

            val properties = findOrCreatePropertiesTag(project, editor, xmlFile) ?: return@runWriteCommandAction

            val existing = properties.findFirstSubTag(property.name)
            val target = if (existing != null) {
                // Already declared here — take the user to it instead of adding a duplicate
                existing
            } else {
                addProperty(project, properties, property) ?: return@runWriteCommandAction
            }

            // PSI modifications block the document; offsets are only usable once it is unblocked
            documentManager.doPostponedOperationsAndUnblockDocument(document)
            moveCaretIntoTag(editor, target)
        }, xmlFile)
    }

    /**
     * Adds [property] to [properties] and returns the inserted tag, reformatting the surrounding
     * block so that the new tag is indented like its siblings.
     */
    private fun addProperty(project: Project, properties: XmlTag, property: MavenProperty): XmlTag? {
        MavenPomEditing.addChildTag(project, properties, property.tagText)
        return MavenPomEditing.reformat(project, properties)?.findFirstSubTag(property.name)
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

    private fun moveCaretIntoTag(editor: Editor, tag: XmlTag) {
        if (!tag.isValid) return
        val offset = tag.textRange.startOffset + MavenProperties.caretOffsetInTagText(tag.text)
        editor.selectionModel.removeSelection()
        editor.caretModel.moveToOffset(offset)
        editor.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    }

    /** Draws a non-clickable separator line above the first entry of each property group. */
    private class EntryRenderer(
        private val separatorsAbove: Set<MavenProperty>
    ) : javax.swing.ListCellRenderer<MavenProperty> {
        private val defaultRenderer = DefaultListCellRenderer()

        override fun getListCellRendererComponent(
            list: JList<out MavenProperty>,
            value: MavenProperty,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val base = defaultRenderer.getListCellRendererComponent(list, value.label, index, isSelected, cellHasFocus)
            if (!separatorsAbove.contains(value)) return base

            val panel = JPanel(BorderLayout())
            panel.border = JBUI.Borders.empty(4, 0, 0, 0)
            panel.add(JSeparator(SwingConstants.HORIZONTAL), BorderLayout.NORTH)
            panel.add(base, BorderLayout.CENTER)
            panel.isOpaque = true
            panel.background = list.background
            return panel
        }
    }
}
