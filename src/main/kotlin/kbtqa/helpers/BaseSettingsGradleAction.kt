package kbtqa.helpers

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*

/**
 * Abstract base class for actions that operate on settings.gradle.kts files.
 * Provides common functionality for PSI manipulation and file validation.
 */
abstract class BaseSettingsGradleAction(
    text: String,
    description: String,
    icon: javax.swing.Icon? = null
) : AnAction(text, description, icon), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null && file.name == "settings.gradle.kts"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ktFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return

        performConfiguration(project, ktFile)
    }

    /**
     * Implement this method to define the specific configuration logic for the action.
     */
    protected abstract fun performConfiguration(project: Project, ktFile: KtFile)

    /**
     * Executes the given action within a write command.
     */
    protected fun executeWriteAction(project: Project, action: () -> Unit) {
        WriteCommandAction.runWriteCommandAction(project, action)
    }

    /**
     * Finds a KtCallExpression by its name in the given PSI file.
     */
    protected fun KtFile.findBlock(name: String): KtCallExpression? {
        return PsiTreeUtil.findChildrenOfType(this, KtCallExpression::class.java)
            .find { it.calleeExpression?.text == name }
    }

    /**
     * Adds content to the end of the file with proper spacing.
     */
    protected fun KtFile.addContentToFile(factory: KtPsiFactory, content: String) {
        val configElement = factory.createExpression(content)
        add(factory.createNewLine(2))
        add(configElement)
    }

    /**
     * Checks if a block with the given name exists in the file.
     */
    protected fun KtFile.hasBlock(blockName: String): Boolean {
        return findBlock(blockName) != null
    }
}