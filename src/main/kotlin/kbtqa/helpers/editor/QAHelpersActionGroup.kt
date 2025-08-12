package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware

/**
 * Action group that contains QA helper actions for Gradle files.
 */
class QAHelpersActionGroup : ActionGroup("QA Helpers", "Helper actions for QA tasks", null), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        // Always show the action group, regardless of file type
        e.presentation.isVisible = true
        
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        // Enable the action group only for gradle.properties, settings.gradle.kts, and build.gradle.kts files
        e.presentation.isEnabled = file != null && 
                (file.name == "gradle.properties" || 
                 file.name == "settings.gradle.kts" || 
                 file.name == "build.gradle.kts")
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            GradlePropertiesAction(),
            ConfigureRepositoriesAction(),
            AddDependencyAction(),
            ConfigureBuildScanAction(),
            ConfigureBuildCacheAction(),
            OverwriteVersionCatalogAction()
        )
    }
}