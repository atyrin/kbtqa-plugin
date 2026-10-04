package kbtqa.helpers.projectview

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [ConfigureVersionCatalogAction] on a project's `gradle` folder.
 */
class ConfigureVersionCatalogActionTest : BasePlatformTestCase() {

    private val action = ConfigureVersionCatalogAction()

    private fun event(file: VirtualFile) = TestActionEvent.createTestEvent(
        action,
        SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file)
            .build()
    )

    private fun runOn(dir: VirtualFile) {
        val event = event(dir)
        action.update(event)
        assertTrue(event.presentation.isEnabledAndVisible)
        action.actionPerformed(event)
    }

    private fun gradleDir(): VirtualFile = myFixture.tempDirFixture.findOrCreateDir("gradle")

    private fun catalogText(): String = VfsUtil.loadText(gradleDir().findChild("libs.versions.toml")!!)

    private fun sectionHeaders(text: String) = Regex("""^\[(\w+)]""", RegexOption.MULTILINE)
        .findAll(text).map { it.groupValues[1] }.toList()

    fun testAvailableOnlyOnGradleFolder() {
        val gradle = gradleDir()
        val otherDir = myFixture.tempDirFixture.findOrCreateDir("src")
        val fileNamedGradle = myFixture.tempDirFixture.createFile("other/gradle")

        assertTrue(event(gradle).also(action::update).presentation.isEnabledAndVisible)
        assertFalse(event(otherDir).also(action::update).presentation.isVisible)
        assertFalse(event(fileNamedGradle).also(action::update).presentation.isVisible)
    }

    fun testCreatesCatalogWithAllSections() {
        runOn(gradleDir())

        val text = catalogText()
        assertEquals(listOf("versions", "libraries", "bundles", "plugins"), sectionHeaders(text))
        assertTrue(text.contains("kotlin = \""))
        assertTrue(text.contains("kmp = { id = \"org.jetbrains.kotlin.multiplatform\", version.ref = \"kotlin\" }"))
    }

    fun testAddsOnlyMissingSectionsAfterExistingContent() {
        val existing = "[versions]\nkotlin = \"2.0.0\"\n\n[plugins]\nmine = { id = \"my.plugin\" }"
        myFixture.tempDirFixture.createFile("gradle/libs.versions.toml", existing)

        runOn(gradleDir())

        val text = catalogText()
        assertTrue(text.startsWith("$existing\n\n[libraries]\n"))
        assertEquals(listOf("versions", "plugins", "libraries", "bundles"), sectionHeaders(text))
        assertFalse("Existing versions must not be overwritten", text.contains("kotlin = \"2.2"))
    }

    fun testCompleteCatalogIsLeftUntouched() {
        val existing = "[versions]\n\n[libraries]\n\n[bundles]\n\n[plugins]\n"
        myFixture.tempDirFixture.createFile("gradle/libs.versions.toml", existing)

        runOn(gradleDir())

        assertEquals(existing, catalogText())
    }
}
