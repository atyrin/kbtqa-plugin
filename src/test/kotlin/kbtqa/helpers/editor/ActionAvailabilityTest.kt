package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kbtqa.helpers.projectview.PrepareUploadAction

/**
 * Tests that each QA Helper action is enabled only for the Gradle files it supports.
 */
class ActionAvailabilityTest : BasePlatformTestCase() {

    private val fileNames = listOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties", "build.gradle", "Main.kt")

    private lateinit var files: Map<String, VirtualFile>

    override fun setUp() {
        super.setUp()
        files = fileNames.associateWith { myFixture.tempDirFixture.createFile("module/$it") }
    }

    private fun presentationFor(action: AnAction, file: VirtualFile?) = TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()
    ).also(action::update).presentation

    private fun assertEnabledOnlyFor(action: AnAction, vararg enabledFor: String) {
        for ((name, file) in files) {
            val presentation = presentationFor(action, file)
            assertEquals("${action.templateText} enabled for $name", name in enabledFor, presentation.isEnabled)
            assertTrue("${action.templateText} visible for $name", presentation.isVisible)
        }
        assertFalse("${action.templateText} enabled without a file", presentationFor(action, null).isEnabled)
    }

    fun testBuildScriptActions() {
        assertEnabledOnlyFor(AddDependencyAction(), "build.gradle.kts")
        assertEnabledOnlyFor(AddCompilerOptionsAction(), "build.gradle.kts")
        assertEnabledOnlyFor(AddJvmPublishingAction(), "build.gradle.kts")
        assertEnabledOnlyFor(CreateKmpSourceSetsAction(), "build.gradle.kts")
    }

    fun testSettingsScriptActions() {
        assertEnabledOnlyFor(ConfigureBuildScanAction(), "settings.gradle.kts")
        assertEnabledOnlyFor(ConfigureBuildCacheAction(), "settings.gradle.kts")
        assertEnabledOnlyFor(OverwriteVersionCatalogAction(), "settings.gradle.kts")
    }

    fun testRepositoriesActionSupportsBothScripts() {
        assertEnabledOnlyFor(ConfigureRepositoriesAction(), "build.gradle.kts", "settings.gradle.kts")
    }

    fun testGradlePropertiesAction() {
        assertEnabledOnlyFor(GradlePropertiesAction(), "gradle.properties")
    }

    fun testGroupIsShownOnlyForSupportedFiles() {
        val group = QAHelpersActionGroup()
        for ((name, file) in files) {
            val supported = name in setOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties")
            val presentation = presentationFor(group, file)
            assertEquals("visible for $name", supported, presentation.isVisible)
            assertEquals("enabled for $name", supported, presentation.isEnabled)
        }
        assertFalse(presentationFor(group, null).isVisible)
    }

    fun testGroupContainsAllHelpers() {
        val children = QAHelpersActionGroup().getChildren(null).map { it.javaClass }
        assertEquals(
            listOf(
                ConfigureRepositoriesAction::class.java, GradlePropertiesAction::class.java,
                AddDependencyAction::class.java, AddCompilerOptionsAction::class.java,
                AddJvmPublishingAction::class.java, CreateKmpSourceSetsAction::class.java,
                ConfigureBuildScanAction::class.java, ConfigureBuildCacheAction::class.java,
                OverwriteVersionCatalogAction::class.java
            ),
            children.filterNot { com.intellij.openapi.actionSystem.Separator::class.java.isAssignableFrom(it) }
        )
    }

    fun testPrepareUploadIsHiddenForFilesOtherThanProjectRoot() {
        val action = PrepareUploadAction()
        assertTrue("shown without a selection (Tools menu)", presentationFor(action, null).isEnabledAndVisible)
        assertFalse("hidden for a file", presentationFor(action, files.getValue("build.gradle.kts")).isVisible)
    }
}
