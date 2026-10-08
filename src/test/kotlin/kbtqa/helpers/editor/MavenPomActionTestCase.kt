package kbtqa.helpers.editor

import com.intellij.lang.xml.XMLLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base class for tests of QA Helper actions that edit a `pom.xml`.
 *
 * Assertions are made on a fresh parse of the resulting text, so they check what the user sees in
 * the editor rather than the in-memory PSI built by the action. Documents always use `\n` line
 * breaks, so expected text compares the same on every OS.
 */
abstract class MavenPomActionTestCase : BasePlatformTestCase() {

    /** Opens [text] as a pom.xml; a `<caret>` marker sets the caret. */
    protected fun configurePom(text: String): XmlFile = myFixture.configureByText("pom.xml", text) as XmlFile

    protected val pom: XmlFile
        get() = myFixture.file as XmlFile

    /** The resulting pom, failing if it is not well-formed XML. */
    protected val pomText: String
        get() = myFixture.editor.document.text.also {
            assertFalse("Result is not well-formed:\n$it", PsiTreeUtil.hasErrorElements(parse(it)))
        }

    protected fun parse(text: String): XmlFile =
        PsiFileFactory.getInstance(project).createFileFromText("check.xml", XMLLanguage.INSTANCE, text) as XmlFile

    /** The tag reached from `<project>` by following the first sub-tag named after each element of [path]. */
    protected fun tag(text: String, vararg path: String): XmlTag? {
        var tag = parse(text).rootTag
        for (name in path) tag = tag?.findFirstSubTag(name)
        return tag
    }

    /** Names of the sub-tags of the tag at [path]. */
    protected fun subTagNames(text: String, vararg path: String): List<String> =
        tag(text, *path)?.subTags?.map { it.name }.orEmpty()

    /** Values of the sub-tags named [name] of [parent]. */
    protected fun values(parent: XmlTag?, name: String): List<String> =
        parent?.findSubTags(name)?.map { it.value.trimmedText }.orEmpty()

    /** The `<build><plugins>` entry with [artifactId]. */
    protected fun plugin(text: String, artifactId: String): XmlTag? =
        tag(text, "build", "plugins")?.findSubTags("plugin")?.firstOrNull {
            it.findFirstSubTag("artifactId")?.value?.trimmedText == artifactId
        }

    /** The tag reached from this one by following the first sub-tag named after each element of [path]. */
    protected fun XmlTag?.at(vararg path: String): XmlTag? {
        var tag = this
        for (name in path) tag = tag?.findFirstSubTag(name)
        return tag
    }

    /** The line with the caret, trimmed, with `|` where the caret is. */
    protected fun caretLine(): String {
        val document = myFixture.editor.document
        val offset = myFixture.caretOffset
        val line = document.getLineNumber(offset)
        val before = document.getText(TextRange(document.getLineStartOffset(line), offset))
        val after = document.getText(TextRange(offset, document.getLineEndOffset(line)))
        return before.trimStart() + "|" + after.trimEnd()
    }

    /** Lines commented out with Ctrl+/, which puts `<!--` at column 0, in sorted order. */
    protected fun columnZeroComments(text: String): List<String> = text.lines().filter { it.startsWith("<!--") }.sorted()
}
