package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * Action that adds a context menu option for build.gradle.kts files
 * to insert Maven publishing configuration.
 */
class AddJvmPublishingAction : AnAction("Add JVM Publishing", "Insert Maven publishing configuration", null), DumbAware {

    companion object {
        private val PLUGINS_BLOCK_REGEX = "plugins\\s*\\{[^}]*}".toRegex()

        private const val MAVEN_PUBLISH_PLUGIN = """plugins {
    `maven-publish`
}"""
        
        private const val PUBLISHING_BLOCK = """
publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "org.gradle.sample"
            artifactId = "library"
            version = "1.1"
            from(components["java"])
        }
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

        insertPublishingConfiguration(project, editor)
    }

    private fun insertPublishingConfiguration(project: Project, editor: Editor) {
        val document = editor.document
        val text = document.text

        WriteCommandAction.runWriteCommandAction(project) {
            // First, check if maven-publish plugin is already added
            if (!text.contains("`maven-publish`")) {
                // Find the plugins block
                val pluginsMatch = PLUGINS_BLOCK_REGEX.find(text)
                
                if (pluginsMatch != null) {
                    // Insert maven-publish inside the existing plugins block
                    val pluginsBlock = pluginsMatch.value
                    val closingBraceIndex = pluginsBlock.lastIndexOf("}")
                    val insertPosition = document.text.indexOf(pluginsBlock) + closingBraceIndex
                    
                    // Insert before the closing brace of plugins block
                    document.insertString(insertPosition, "\n    `maven-publish`\n")
                } else {
                    // If no plugins block found, create one: Gradle requires it before any other statement
                    val insertPosition = pluginsBlockOffset(project, document)
                    val pluginsBlock = if (insertPosition == 0) "$MAVEN_PUBLISH_PLUGIN\n\n" else "\n\n$MAVEN_PUBLISH_PLUGIN"
                    document.insertString(insertPosition, pluginsBlock)
                }
            }
            
            // Now add the publishing block if it doesn't exist
            if (!text.contains("publishing\\s*\\{".toRegex())) {
                // Insert at the cursor position, but never above the plugins block
                val pluginsBlockEnd = PLUGINS_BLOCK_REGEX.find(document.text)?.range?.last?.plus(1) ?: 0
                val insertPosition = maxOf(editor.caretModel.offset, pluginsBlockEnd)
                document.insertString(insertPosition, PUBLISHING_BLOCK)
            }
        }
    }

    /**
     * Returns the offset where a new `plugins {}` block belongs: after the imports and the
     * top-level `buildscript {}` block, the only things Gradle allows before it.
     */
    private fun pluginsBlockOffset(project: Project, document: Document): Int {
        val documentManager = PsiDocumentManager.getInstance(project)
        documentManager.commitDocument(document)
        val ktFile = documentManager.getPsiFile(document) as? KtFile ?: return 0
        val importsEnd = ktFile.importList?.takeIf { it.imports.isNotEmpty() }?.textRange?.endOffset
        val buildscriptEnd = PsiTreeUtil.findChildrenOfType(ktFile, KtCallExpression::class.java)
            .find { it.calleeExpression?.text == "buildscript" && PsiTreeUtil.getParentOfType(it, KtCallExpression::class.java) == null }
            ?.textRange?.endOffset
        return maxOf(importsEnd ?: 0, buildscriptEnd ?: 0)
    }
}