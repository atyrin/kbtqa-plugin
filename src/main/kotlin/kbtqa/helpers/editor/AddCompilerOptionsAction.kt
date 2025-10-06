package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project

/**
 * Action that adds a context menu option for build.gradle.kts files
 * to insert Kotlin compiler options settings.
 */
class AddCompilerOptionsAction : AnAction("Add Compiler Options", "Insert Kotlin compiler options configuration", null), DumbAware {

    companion object {
        private const val COMPILER_OPTIONS = """
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        //languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3)
        freeCompilerArgs.set(listOf("-Xrender-internal-diagnostic-names",
//            "-Xcontext-parameters",
//            "-XXLanguage:+NameBasedDestructuring",
        ))
    }
}
"""
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

        insertCompilerOptions(project, editor)
    }

    private fun insertCompilerOptions(project: Project, editor: Editor) {
        val document = editor.document

        // Insert the compiler options at the cursor position
        WriteCommandAction.runWriteCommandAction(project) {
            // Get the cursor position
            val insertPosition = editor.caretModel.offset

            // Insert the compiler options at the cursor position
            document.insertString(insertPosition, COMPILER_OPTIONS)
        }
    }
}
