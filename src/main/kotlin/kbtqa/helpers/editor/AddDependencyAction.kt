package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.SimpleListCellRenderer
import org.jetbrains.kotlin.psi.KtFile

/**
 * Action that adds a context menu option for build.gradle.kts files
 * to insert dependency declarations.
 *
 * The snippets offered in the chooser popup depend on the caret context:
 * the first applicable [DependencySnippetProvider] from [PROVIDERS] is used.
 */
class AddDependencyAction : AnAction("Add Dependency", "Insert a dependency declaration", null), DumbAware {

    companion object {
        private val PROVIDERS = listOf(
            SwiftPMDependencySnippetProvider(),
            MavenDependencySnippetProvider()
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

        // Determine the snippet provider applicable to the caret context
        val context = DependencyInsertionContext(
            ktFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile,
            caretOffset = editor.caretModel.offset
        )
        val provider = PROVIDERS.first { it.isApplicable(context) }

        // Create and show popup with dependency options
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(provider.snippets)
            .setTitle(provider.popupTitle)
            .setRenderer(SimpleListCellRenderer.create("") { it.label })
            .setItemChosenCallback { snippet ->
                insertDependency(project, editor, snippet.code)
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