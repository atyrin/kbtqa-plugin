package kbtqa.helpers.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Base class for tests of QA Helper actions that modify a Gradle Kotlin script.
 *
 * Assertions are made on a fresh parse of the resulting text, so they check what the user
 * sees in the editor rather than the in-memory PSI built by the action.
 */
abstract class GradleScriptActionTestCase : BasePlatformTestCase() {

    /**
     * Opens [text] as [fileName] (a `<caret>` marker sets the caret), runs [action] and returns the
     * resulting script, failing if it does not parse.
     */
    protected fun runAction(action: AnAction, fileName: String, text: String): String {
        myFixture.configureByText(fileName, text)
        myFixture.testAction(action)
        val result = myFixture.editor.document.text
        assertFalse("Result has syntax errors:\n$result", PsiTreeUtil.hasErrorElements(parse(result)))
        return result
    }

    /** Runs [action] a second time on the current file and asserts it changes nothing. */
    protected fun assertIdempotent(action: AnAction) {
        val before = myFixture.editor.document.text
        myFixture.testAction(action)
        assertEquals(before, myFixture.editor.document.text)
    }

    protected fun parse(text: String): KtFile = KtPsiFactory(project).createFile("check.gradle.kts", text)

    /** Names of the top-level calls of the script, in order. */
    protected fun topLevelCalls(text: String): List<String> =
        PsiTreeUtil.findChildrenOfType(parse(text), KtCallExpression::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, KtCallExpression::class.java) == null }
            .map { it.calleeExpression?.text.orEmpty() }

    /**
     * Statements of the block reached by following the first call named after each element of [path],
     * e.g. `statements(text, "pluginManagement", "repositories")`.
     */
    protected fun statements(text: String, vararg path: String): List<String> {
        var scope: PsiElement = parse(text)
        for (name in path) {
            val call = PsiTreeUtil.findChildrenOfType(scope, KtCallExpression::class.java)
                .firstOrNull { it.calleeExpression?.text == name }
                ?: throw AssertionError("No '$name' block in ${path.joinToString(" > ")}:\n$text")
            scope = call.lambdaArguments.single().getLambdaExpression()!!.bodyExpression!!
        }
        return (scope as KtBlockExpression).statements.map { it.text }
    }

    protected fun String.occurrences(substring: String): Int = windowed(substring.length).count { it == substring }
}
