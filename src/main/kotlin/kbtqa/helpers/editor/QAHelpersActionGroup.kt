package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware

/**
 * Action group that contains QA helper actions for Gradle and Maven build files.
 *
 * The children offered depend on the file the menu is opened on: Gradle files get the Gradle
 * helpers, `pom.xml` gets the Maven ones.
 */
class QAHelpersActionGroup : ActionGroup("QA Helpers", "Helper actions for QA tasks", null), DumbAware {

    companion object {
        private val GRADLE_FILES = setOf("gradle.properties", "settings.gradle.kts", "build.gradle.kts")
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val fileName = e.getData(CommonDataKeys.VIRTUAL_FILE)?.name

        // Show and enable the action group only for the supported Gradle and Maven files
        val isSupported = isGradleFile(fileName) || MavenProperties.isPomFile(fileName)

        e.presentation.isVisible = isSupported
        e.presentation.isEnabled = isSupported
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val fileName = e?.getData(CommonDataKeys.VIRTUAL_FILE)?.name
        return when {
            MavenProperties.isPomFile(fileName) -> mavenChildren()
            // Action indexing passes no event; expose everything so all children stay discoverable
            e == null -> gradleChildren() + mavenChildren()
            else -> gradleChildren()
        }
    }

    private fun isGradleFile(fileName: String?): Boolean = fileName != null && fileName in GRADLE_FILES

    private fun mavenChildren(): Array<AnAction> = arrayOf(
        ConfigureMavenRepositoriesAction(),
        MavenPropertiesAction(),
        ConfigureKotlinPluginAction(),
        AddKotlinCompilerPluginAction(),
        ConfigureToolchainAction()
    )

    private fun gradleChildren(): Array<AnAction> = arrayOf(
        ConfigureRepositoriesAction(),
        Separator.create("Gradle Properties"),
        GradlePropertiesAction(),
        Separator.create("Build Script"),
        AddDependencyAction(),
        AddCompilerOptionsAction(),
        AddJvmPublishingAction(),
        CreateKmpSourceSetsAction(),
        Separator.create("Settings"),
        ConfigureBuildScanAction(),
        ConfigureBuildCacheAction(),
        OverwriteVersionCatalogAction()
    )
}
