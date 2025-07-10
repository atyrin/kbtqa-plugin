package kbtqa.helpers

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory

/**
 * Action that adds a context menu option for build.gradle.kts files
 * to insert dependency declarations.
 */
class AddDependencyAction : AnAction("Add Dependency", "Insert a dependency declaration", null), DumbAware {

    companion object {
        private val DEPENDENCIES = listOf(
            "\"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2\"",
            "\"com.squareup.okio:okio:3.15.0\""
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        // Always show the action, but enable it only for build.gradle.kts files
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null && file.name == "build.gradle.kts"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        // Create and show popup with dependency options
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(DEPENDENCIES)
            .setTitle("Select Dependency")
            .setItemChosenCallback { dependency ->
                insertDependency(project, editor, dependency)
            }
            .createPopup()
            .showInBestPositionFor(e.dataContext)
    }

    private fun insertDependency(project: Project, editor: Editor, dependency: String) {
        val document = editor.document
        
        // Insert the dependency at the cursor position
        WriteCommandAction.runWriteCommandAction(project) {
            // Get the cursor position
            val insertPosition = editor.caretModel.offset
            
            // Insert the dependency at the cursor position
            document.insertString(insertPosition, dependency)
        }
    }
}