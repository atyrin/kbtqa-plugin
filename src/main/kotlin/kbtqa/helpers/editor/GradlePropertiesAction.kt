package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants

/**
 * Action that adds a context menu option for gradle.properties files
 * to insert predefined properties.
 */
class GradlePropertiesAction : AnAction("Add Gradle Property", "Insert a Gradle property", null), DumbAware {

    private sealed interface PropertyItem {
        data class Entry(val text: String) : PropertyItem
        data object Separator : PropertyItem
    }

    companion object {
        private val PROPERTIES: List<PropertyItem> = listOf(
            PropertyItem.Entry("kotlin.internal.compiler.arguments.log.level=warning"),
            PropertyItem.Entry("kotlin.native.enableKlibsCrossCompilation=false"),
            PropertyItem.Entry("kotlin.compiler.runViaBuildToolsApi=true"),
            PropertyItem.Entry("kotlin.kmp.isolated-projects.support=enable"),
            PropertyItem.Entry("kotlin.internal.kmp.kmpPublicationStrategy=uklibPublicationInASingleComponentWithKMPPublication"),
            PropertyItem.Entry("kotlin.internal.kmp.kmpResolutionStrategy=interlibraryUklibAndPSMResolution_PreferUklibs"),
            PropertyItem.Entry("kotlin.internal.kmp.enableUKlibs=true"),
            PropertyItem.Entry("kotlin.stdlib.default.dependency=false"),
            PropertyItem.Entry("kotlin.mpp.enableCInteropCommonization=true"),
            PropertyItem.Separator,
            PropertyItem.Entry("org.gradle.unsafe.isolated-projects=true"),
            PropertyItem.Entry("org.gradle.configuration-cache=true"),
            PropertyItem.Entry("org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=2g -XX:+UseParallelGC -XX:-UseGCOverheadLimit"),
            PropertyItem.Separator,
            PropertyItem.Entry("org.jetbrains.dokka.experimental.gradle.pluginMode=V2EnabledWithHelpers"),
            PropertyItem.Entry("org.jetbrains.dokka.gradle.enableLogHtmlPublicationLink=false"),
            PropertyItem.Entry("org.jetbrains.dokka.experimental.tryK2=true"),
            PropertyItem.Separator,
            PropertyItem.Entry("android.builtInKotlin=false"),
        )

        // Derived lists for rendering non-clickable separators above items
        private val ENTRIES_ONLY: List<PropertyItem.Entry>
        private val ENTRIES_WITH_SEPARATOR_ABOVE: Set<PropertyItem.Entry>

        init {
            val entries = mutableListOf<PropertyItem.Entry>()
            val sepsAbove = mutableSetOf<PropertyItem.Entry>()
            var prevWasSeparator = false
            for (item in PROPERTIES) {
                when (item) {
                    is PropertyItem.Entry -> {
                        entries += item
                        if (prevWasSeparator) sepsAbove += item
                        prevWasSeparator = false
                    }
                    is PropertyItem.Separator -> prevWasSeparator = true
                }
            }
            ENTRIES_ONLY = entries
            ENTRIES_WITH_SEPARATOR_ABOVE = sepsAbove
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        // Always show the action, but enable it only for gradle.properties files
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null && file.name == "gradle.properties"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        showPropertiesPopup(project, editor, e.dataContext)
    }
    
    private fun showPropertiesPopup(project: Project, editor: Editor, dataContext: DataContext) {
        // Create and show popup that recreates itself after each selection
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(ENTRIES_ONLY)
            .setTitle("Select Gradle Property")
            .setResizable(true)
            .setRenderer(EntryRenderer(ENTRIES_WITH_SEPARATOR_ABOVE))
            .setItemChosenCallback { item ->
                insertProperty(project, editor, item.text)
                // Recreate the popup after insertion to keep it open
                showPropertiesPopup(project, editor, dataContext)
            }
            .createPopup()
//            .also { it.setMinSize(JBUI.size(800, 400)) }
            .showInBestPositionFor(dataContext)
    }

    private fun insertProperty(project: Project, editor: Editor, property: String) {
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(project) {
            val caretModel = editor.caretModel
            val offset = caretModel.offset
            val lineNumber = document.getLineNumber(offset)
            val lineStartOffset = document.getLineStartOffset(lineNumber)
            val lineEndOffset = document.getLineEndOffset(lineNumber)
            val lineText = document.charsSequence.subSequence(lineStartOffset, lineEndOffset).toString()

            if (lineText.isNotBlank()) {
                // Line is not blank, insert property on the next line
                val propertyToInsert = "\n" + property
                document.insertString(lineEndOffset, propertyToInsert)
                caretModel.moveToOffset(lineEndOffset + propertyToInsert.length)
            } else {
                // Line is blank, insert property at the current caret position
                document.insertString(offset, property)
                caretModel.moveToOffset(offset + property.length)
            }
        }
    }

    private class EntryRenderer(
        private val separatorsAbove: Set<PropertyItem.Entry>
    ) : javax.swing.ListCellRenderer<PropertyItem.Entry> {
        private val defaultRenderer = DefaultListCellRenderer()
        override fun getListCellRendererComponent(
            list: JList<out PropertyItem.Entry>,
            value: PropertyItem.Entry,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val base = defaultRenderer.getListCellRendererComponent(list, value.text, index, isSelected, cellHasFocus)
            if (!separatorsAbove.contains(value)) return base

            val panel = JPanel(BorderLayout())
            panel.border = JBUI.Borders.empty(4, 0, 0, 0)
            val sep = JSeparator(SwingConstants.HORIZONTAL)
            panel.add(sep, BorderLayout.NORTH)
            panel.add(base, BorderLayout.CENTER)
            panel.isOpaque = true
            panel.background = list.background
            return panel
        }
    }
}