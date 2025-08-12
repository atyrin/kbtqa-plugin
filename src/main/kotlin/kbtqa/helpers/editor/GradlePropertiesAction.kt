package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

/**
 * Action that adds a context menu option for gradle.properties files
 * to insert predefined properties.
 */
class GradlePropertiesAction : AnAction("Add Gradle Property", "Insert a Gradle property", null), DumbAware {

    companion object {
        private val PROPERTIES = listOf(
            "kotlin.internal.compiler.arguments.log.level=warning",
            "kotlin.native.enableKlibsCrossCompilation=false",
            "kotlin.compiler.runViaBuildToolsApi=true",
            "kotlin.kmp.isolated-projects.support=enable",
            "kotlin.internal.kmp.kmpPublicationStrategy=uklibPublicationInASingleComponentWithKMPPublication",
            "kotlin.internal.kmp.kmpResolutionStrategy=interlibraryUklibAndPSMResolution_PreferUklibs",
            "kotlin.stdlib.default.dependency=false",
            "kotlin.mpp.enableCInteropCommonization=true",
            "",
            "org.gradle.unsafe.isolated-projects=true",
            "org.gradle.configuration-cache=true",
            "org.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=2g -XX:+UseParallelGC -XX:-UseGCOverheadLimit",
        )
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
            .createPopupChooserBuilder(PROPERTIES)
            .setTitle("Select Gradle Property")
            .setItemChosenCallback { property ->
                insertProperty(project, editor, property)
                // Recreate the popup after insertion to keep it open
                showPropertiesPopup(project, editor, dataContext)
            }
            .createPopup()
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
}