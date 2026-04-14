package kbtqa.helpers.skills

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

/**
 * Action that opens the Skills Setup Wizard dialog.
 * Available from the Tools menu.
 */
class SkillsSetupAction : AnAction(
    "Skills Setup Wizard",
    "Browse and install AI agent skills from GitHub repositories",
    null
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
        e.presentation.isVisible = true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val dialog = SkillsSetupDialog(e.project)
        dialog.show()
    }
}
