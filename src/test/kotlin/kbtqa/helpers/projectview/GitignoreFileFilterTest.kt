package kbtqa.helpers.projectview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for [GitignoreFileFilter] pattern matching and `.gitignore` discovery.
 */
class GitignoreFileFilterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val projectDir: File
        get() = tempFolder.root

    private fun writeGitignore(content: String, dirRelativePath: String = "") {
        val dir = if (dirRelativePath.isEmpty()) projectDir else File(projectDir, dirRelativePath).apply { mkdirs() }
        File(dir, ".gitignore").writeText(content)
    }

    private fun filter(skipDirNames: Set<String> = emptySet()) = GitignoreFileFilter(projectDir, skipDirNames)

    @Test
    fun `no gitignore files - no rules and no matches`() {
        val filter = filter()
        assertFalse(filter.hasRules())
        assertNull(filter.match("src/Main.kt", isDirectory = false))
    }

    @Test
    fun `unanchored file pattern matches at any depth`() {
        writeGitignore("*.log")
        val filter = filter()
        assertEquals("*.log", filter.match("debug.log", false)?.pattern)
        assertEquals("*.log", filter.match("logs/app/debug.log", false)?.pattern)
        assertNull(filter.match("debug.txt", false))
    }

    @Test
    fun `unanchored name matches files and directories at any depth`() {
        writeGitignore("node_modules")
        val filter = filter()
        assertNotNull(filter.match("node_modules", true))
        assertNotNull(filter.match("sub/node_modules", true))
        assertNull(filter.match("node_modules_backup", true))
    }

    @Test
    fun `anchored pattern matches only at root`() {
        writeGitignore("/mydir")
        val filter = filter()
        assertNotNull(filter.match("mydir", true))
        assertNull(filter.match("sub/mydir", true))
    }

    @Test
    fun `pattern with inner slash is anchored`() {
        writeGitignore("docs/generated")
        val filter = filter()
        assertNotNull(filter.match("docs/generated", true))
        assertNull(filter.match("sub/docs/generated", true))
    }

    @Test
    fun `trailing slash matches directories only`() {
        writeGitignore("mydir/")
        val filter = filter()
        assertNotNull(filter.match("mydir", isDirectory = true))
        assertNull(filter.match("mydir", isDirectory = false))
    }

    @Test
    fun `path inside ignored directory is ignored`() {
        writeGitignore("mydir/")
        val filter = filter()
        assertNotNull(filter.match("mydir/sub/file.txt", isDirectory = false))
        assertNotNull(filter.match("mydir/sub", isDirectory = true))
    }

    @Test
    fun `negation re-includes a file`() {
        writeGitignore("*.log\n!keep.log")
        val filter = filter()
        assertNotNull(filter.match("debug.log", false))
        assertNull(filter.match("keep.log", false))
        assertNull(filter.match("logs/keep.log", false))
    }

    @Test
    fun `comments and blank lines are skipped`() {
        writeGitignore("# comment\n\n*.tmp\n   \n# another\n")
        val filter = filter()
        assertTrue(filter.hasRules())
        assertNotNull(filter.match("file.tmp", false))
        assertNull(filter.match("comment", false))
    }

    @Test
    fun `question mark matches single character`() {
        writeGitignore("file?.txt")
        val filter = filter()
        assertNotNull(filter.match("file1.txt", false))
        assertNull(filter.match("file12.txt", false))
        assertNull(filter.match("file.txt", false))
    }

    @Test
    fun `double star prefix matches at any depth`() {
        writeGitignore("**/tmp")
        val filter = filter()
        assertNotNull(filter.match("tmp", true))
        assertNotNull(filter.match("a/b/tmp", true))
        assertNull(filter.match("a/b/tmpx", true))
    }

    @Test
    fun `double star suffix matches everything inside`() {
        writeGitignore("cache/**")
        val filter = filter()
        assertNotNull(filter.match("cache/a", false))
        assertNotNull(filter.match("cache/a/b/c", false))
        assertNull(filter.match("other/a", false))
    }

    @Test
    fun `double star in the middle matches zero or more directories`() {
        writeGitignore("a/**/b")
        val filter = filter()
        assertNotNull(filter.match("a/b", true))
        assertNotNull(filter.match("a/x/b", true))
        assertNotNull(filter.match("a/x/y/b", true))
        assertNull(filter.match("a/x/c", true))
    }

    @Test
    fun `star does not cross directory boundary`() {
        writeGitignore("src/*.kt")
        val filter = filter()
        assertNotNull(filter.match("src/Main.kt", false))
        assertNull(filter.match("src/sub/Main.kt", false))
    }

    @Test
    fun `nested gitignore rules are scoped to their directory`() {
        writeGitignore("out/", dirRelativePath = "module")
        val filter = filter()
        assertNotNull(filter.match("module/out", true))
        assertNull(filter.match("other/out", true))
        assertNull(filter.match("out", true))
    }

    @Test
    fun `nested gitignore pattern is prefixed with its directory`() {
        writeGitignore("*.tmp", dirRelativePath = "module")
        val filter = filter()
        assertEquals("module/*.tmp", filter.match("module/work/file.tmp", false)?.pattern)
        assertNull(filter.match("file.tmp", false))
    }

    @Test
    fun `nested gitignore wins over root rules`() {
        writeGitignore("*.log")
        writeGitignore("!keep.log", dirRelativePath = "module")
        val filter = filter()
        assertNotNull(filter.match("module/other.log", false))
        assertNull(filter.match("module/keep.log", false))
    }

    @Test
    fun `git info exclude file is loaded`() {
        File(projectDir, ".git/info").mkdirs()
        File(projectDir, ".git/info/exclude").writeText("secret.txt")
        val filter = filter()
        assertNotNull(filter.match("secret.txt", false))
    }

    @Test
    fun `gitignore inside skipped directory is not parsed`() {
        writeGitignore("workspace.xml", dirRelativePath = ".idea")
        val filter = filter(skipDirNames = setOf(".idea"))
        assertFalse(filter.hasRules())
        assertNull(filter.match("workspace.xml", false))
        assertNull(filter.match(".idea/workspace.xml", false))
    }

    @Test
    fun `gitignore inside gradle build directory is not parsed`() {
        File(projectDir, "build.gradle.kts").writeText("")
        writeGitignore("*.bin", dirRelativePath = "build")
        val filter = filter()
        assertFalse(filter.hasRules())
        assertNull(filter.match("build/file.bin", false))
    }

    @Test
    fun `gitignore inside non-gradle build directory is parsed`() {
        writeGitignore("*.bin", dirRelativePath = "build")
        val filter = filter()
        assertTrue(filter.hasRules())
        assertNotNull(filter.match("build/file.bin", false))
    }

    @Test
    fun `gitignore inside dot git directory is not parsed`() {
        writeGitignore("everything", dirRelativePath = ".git")
        val filter = filter()
        assertFalse(filter.hasRules())
    }

    @Test
    fun `backslash separators are normalized for matching`() {
        writeGitignore("mydir/")
        val filter = filter()
        assertNotNull(filter.match("mydir\\sub\\file.txt", isDirectory = false))
    }

    @Test
    fun `pattern with special regex characters is treated literally`() {
        writeGitignore("file(1).txt")
        val filter = filter()
        assertNotNull(filter.match("file(1).txt", false))
        assertNull(filter.match("file1.txt", false))
    }
}
