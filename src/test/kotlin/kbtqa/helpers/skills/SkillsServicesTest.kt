package kbtqa.helpers.skills

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.EmptyProgressIndicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/**
 * Tests for [SkillsRepositoryService] and [SkillsInstallerService] against a local git repository.
 * Skipped when no `git` executable is available.
 */
class SkillsServicesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repoDir: File
    private lateinit var targetDir: File

    private val repo: SkillRepository
        get() = SkillRepository(name = "Local", url = "file://${repoDir.absolutePath}", skillsPath = "skills")

    private val indicator = EmptyProgressIndicator(ModalityState.nonModal())

    @Before
    fun setUp() {
        assumeTrue("git is not available", gitAvailable())
        repoDir = tempFolder.newFolder("skills-repo")
        targetDir = File(tempFolder.root, "project/.junie/skills")
        file("skills/kotlin-tooling/SKILL.md", "# Kotlin tooling")
        file("skills/kotlin-tooling/scripts/run.sh", "echo run")
        file("skills/android/SKILL.md", "# Android")
        file("skills/.template/SKILL.md", "# Template")
        file("skills/README.md", "Not a skill")
        file("other/ignored/SKILL.md", "# Outside the skills path")
    }

    private fun file(relativePath: String, content: String) {
        File(repoDir, relativePath).apply { parentFile.mkdirs() }.writeText(content)
    }

    private fun commit() {
        git("init", "-q")
        git("add", "-A")
        git("-c", "user.name=Test", "-c", "user.email=test@example.org", "-c", "commit.gpgsign=false", "commit", "-q", "-m", "skills")
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder("git", *args).directory(repoDir).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals("git ${args.joinToString(" ")} failed: $output", 0, process.waitFor())
    }

    private fun gitAvailable(): Boolean = runCatching {
        ProcessBuilder("git", "--version").start().waitFor(10, TimeUnit.SECONDS)
    }.getOrDefault(false)

    @Test
    fun `skills are listed sorted without hidden directories and files`() {
        commit()

        assertEquals(
            listOf(SkillInfo("android", "skills/android"), SkillInfo("kotlin-tooling", "skills/kotlin-tooling")),
            SkillsRepositoryService().fetchSkills(repo)
        )
    }

    @Test
    fun `missing skills path is reported`() {
        commit()

        val error = assertThrows(RuntimeException::class.java) {
            SkillsRepositoryService().fetchSkills(repo.copy(skillsPath = "agent-skills"))
        }
        assertEquals("Skills directory 'agent-skills' not found in repository", error.message)
    }

    @Test
    fun `clone failure is reported`() {
        val missing = "file://${File(tempFolder.root, "does-not-exist").absolutePath}"

        val error = assertThrows(RuntimeException::class.java) {
            SkillsRepositoryService().fetchSkills(repo.copy(url = missing))
        }
        assertTrue(error.message, error.message!!.startsWith("Failed to clone repository"))
    }

    @Test
    fun `selected skills are installed with their contents`() {
        commit()

        SkillsInstallerService().installSkills(repo, listOf("kotlin-tooling"), targetDir, indicator)

        assertEquals(listOf("kotlin-tooling"), targetDir.list()!!.toList())
        assertEquals("# Kotlin tooling", File(targetDir, "kotlin-tooling/SKILL.md").readText())
        assertEquals("echo run", File(targetDir, "kotlin-tooling/scripts/run.sh").readText())
    }

    @Test
    fun `reinstalling a skill replaces the previous copy`() {
        commit()
        File(targetDir, "android/stale.md").apply { parentFile.mkdirs() }.writeText("stale")
        File(targetDir, "my-own-skill/SKILL.md").apply { parentFile.mkdirs() }.writeText("mine")

        SkillsInstallerService().installSkills(repo, listOf("android"), targetDir, indicator)

        assertFalse(File(targetDir, "android/stale.md").exists())
        assertEquals("# Android", File(targetDir, "android/SKILL.md").readText())
        assertEquals("Other installed skills are kept", "mine", File(targetDir, "my-own-skill/SKILL.md").readText())
    }

    @Test
    fun `missing skill fails the installation`() {
        commit()

        val error = assertThrows(RuntimeException::class.java) {
            SkillsInstallerService().installSkills(repo, listOf("android", "removed"), targetDir, indicator)
        }
        assertTrue(error.message, error.message!!.startsWith("Skill 'removed' was not found in the repository"))
    }

    @Test
    fun `symlinked skill is rejected`() {
        val outside = tempFolder.newFolder("outside").apply { File(this, "secret.txt").writeText("secret") }
        assumeTrue(runCatching {
            Files.createSymbolicLink(File(repoDir, "skills/linked").toPath(), outside.toPath())
        }.isSuccess)
        commit()

        val error = assertThrows(RuntimeException::class.java) {
            SkillsInstallerService().installSkills(repo, listOf("linked"), targetDir, indicator)
        }
        assertTrue(error.message, error.message!!.contains("symlink"))
        assertFalse(File(targetDir, "linked/secret.txt").exists())
    }
}
