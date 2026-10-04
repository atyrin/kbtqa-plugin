package kbtqa.helpers.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile

/**
 * Tests for the caret-context detection of [SwiftPMDependencySnippetProvider] and [MavenDependencySnippetProvider].
 */
class DependencySnippetProviderTest : BasePlatformTestCase() {

    private val swiftPM = SwiftPMDependencySnippetProvider()

    private fun contextAtCaret(script: String): DependencyInsertionContext {
        val file = myFixture.configureByText("build.gradle.kts", script) as KtFile
        return DependencyInsertionContext(file, myFixture.caretOffset)
    }

    fun testSwiftPMApplicableInsideBlock() {
        val context = contextAtCaret(
            """
            kotlin {
                iosArm64()
                swiftPMDependencies {
                    <caret>
                }
            }
            """.trimIndent()
        )
        assertTrue(swiftPM.isApplicable(context))
    }

    fun testSwiftPMApplicableInsideNestedCall() {
        val context = contextAtCaret(
            """
            kotlin {
                swiftPMDependencies {
                    swiftPackage(
                        url = url("https://github.com/apple/swift-protobuf.git"),
                        products = listOf(<caret>),
                    )
                }
            }
            """.trimIndent()
        )
        assertTrue(swiftPM.isApplicable(context))
    }

    fun testSwiftPMNotApplicableOutsideBlock() {
        val context = contextAtCaret(
            """
            kotlin {
                swiftPMDependencies {
                }
                sourceSets {
                    commonMain.dependencies {
                        implementation(<caret>)
                    }
                }
            }
            """.trimIndent()
        )
        assertFalse(swiftPM.isApplicable(context))
    }

    fun testSwiftPMNotApplicableAtTopLevel() {
        assertFalse(swiftPM.isApplicable(contextAtCaret("<caret>\nkotlin { swiftPMDependencies { } }")))
    }

    fun testSwiftPMNotApplicableWithoutKotlinFile() {
        assertFalse(swiftPM.isApplicable(DependencyInsertionContext(ktFile = null, caretOffset = 0)))
    }

    fun testMavenProviderIsAlwaysApplicable() {
        val maven = MavenDependencySnippetProvider()
        assertTrue(maven.isApplicable(DependencyInsertionContext(ktFile = null, caretOffset = 0)))
        assertTrue(maven.snippets.all { it.code.startsWith("\"") && it.code.endsWith("\"") && it.code.count { c -> c == ':' } == 2 })
    }

    fun testSnippetsParseAsKotlin() {
        for (snippet in swiftPM.snippets + MavenDependencySnippetProvider().snippets) {
            val file = myFixture.configureByText("build.gradle.kts", "dependencies {\n${snippet.code}\n}\n")
            assertFalse(
                "Snippet '${snippet.label}' has syntax errors",
                com.intellij.psi.util.PsiTreeUtil.hasErrorElements(file)
            )
        }
    }
}
