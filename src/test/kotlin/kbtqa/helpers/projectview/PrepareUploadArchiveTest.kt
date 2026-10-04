package kbtqa.helpers.projectview

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

/**
 * Tests for the archive created by [PrepareUploadAction].
 */
class PrepareUploadArchiveTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val projectDir: File by lazy { tempFolder.newFolder("reproducer") }

    private fun file(relativePath: String, content: String = relativePath) {
        File(projectDir, relativePath).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun createArchive(excludedPaths: Set<String> = emptySet()): File =
        PrepareUploadAction().createZipArchive(projectDir, excludedPaths, EmptyProgressIndicator(ModalityState.nonModal()))

    private fun entries(zip: File): Map<String, String?> = ZipFile(zip).use { zipFile ->
        zipFile.entries().asSequence().associate { entry ->
            entry.name to if (entry.isDirectory) null else zipFile.getInputStream(entry).reader().readText()
        }
    }

    @Test
    fun `archive is created next to the project with entries under the project name`() {
        file("settings.gradle.kts", "rootProject.name = \"reproducer\"")
        file("app/src/main/kotlin/Main.kt", "fun main() {}")

        val zip = createArchive()

        assertEquals(File(tempFolder.root, "reproducer.zip"), zip)
        assertEquals(
            mapOf(
                "reproducer/settings.gradle.kts" to "rootProject.name = \"reproducer\"",
                "reproducer/app/" to null,
                "reproducer/app/src/" to null,
                "reproducer/app/src/main/" to null,
                "reproducer/app/src/main/kotlin/" to null,
                "reproducer/app/src/main/kotlin/Main.kt" to "fun main() {}"
            ),
            entries(zip)
        )
    }

    @Test
    fun `excluded directories and files are left out with their contents`() {
        file("build.gradle.kts")
        file("local.properties")
        file(".gradle/8.14/cache.bin")
        file("build/libs/app.jar")
        file("app/build/tmp/out.txt")
        file("app/src/App.kt")

        // Separators are hardcoded on purpose: the dialog reports '/'-separated paths
        val zip = createArchive(setOf(".gradle", "build", "app/build", "local.properties"))

        assertEquals(
            setOf("reproducer/build.gradle.kts", "reproducer/app/", "reproducer/app/src/", "reproducer/app/src/App.kt"),
            entries(zip).keys
        )
    }

    @Test
    fun `excluded path matches exactly and not by prefix`() {
        file("build/out.txt")
        file("buildSrc/build.gradle.kts")

        val zip = createArchive(setOf("build"))

        assertEquals(setOf("reproducer/buildSrc/", "reproducer/buildSrc/build.gradle.kts"), entries(zip).keys)
    }

    @Test
    fun `existing archive is kept under an old name`() {
        file("a.txt", "new")
        val previous = File(tempFolder.root, "reproducer.zip").apply { writeText("previous archive") }

        val zip = createArchive()

        assertEquals(previous, zip)
        assertEquals(mapOf("reproducer/a.txt" to "new"), entries(zip))
        val oldArchives = tempFolder.root.listFiles { f -> f.name.matches(Regex("reproducer_old_\\d{8}_\\d{6}\\.zip")) }!!
        assertEquals(1, oldArchives.size)
        assertEquals("previous archive", oldArchives.single().readText())
    }

    @Test
    fun `empty project yields an empty archive`() {
        val zip = createArchive()
        assertTrue(zip.exists())
        assertTrue(entries(zip).isEmpty())
    }
}
