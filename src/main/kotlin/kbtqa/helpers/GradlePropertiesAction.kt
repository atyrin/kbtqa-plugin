package kbtqa.helpers

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
            "kotlin.internal.kmp.kmpPublicationStrategy=standardKMPPublication",
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
        
        // Create and show popup with property options
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(PROPERTIES)
            .setTitle("Select Gradle Property")
            .setItemChosenCallback { property ->
                insertProperty(project, editor, property)
            }
            .createPopup()
            .showInBestPositionFor(e.dataContext)
    }

    private fun insertProperty(project: Project, editor: Editor, property: String) {
        val document = editor.document
        
        // Insert the property at the end of the file with a newline if needed
        WriteCommandAction.runWriteCommandAction(project) {
            val text = document.text
            val insertPosition = document.textLength
            
            // Add a newline before the property if the file doesn't end with one
            val propertyToInsert = if (text.isEmpty() || text.endsWith("\n")) {
                property
            } else {
                "\n$property"
            }
            
            // Add a newline after the property
            document.insertString(insertPosition, "$propertyToInsert\n")
        }
    }
}