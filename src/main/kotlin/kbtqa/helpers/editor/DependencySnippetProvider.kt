package kbtqa.helpers.editor

import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile

/**
 * A dependency snippet offered by the Add Dependency action.
 *
 * @property label the text shown in the chooser popup
 * @property code the code inserted at the caret when the snippet is chosen
 */
data class DependencySnippet(val label: String, val code: String)

/**
 * Context describing where the Add Dependency action was invoked.
 *
 * @property ktFile the PSI file the action was invoked in, if it is a Kotlin file
 * @property caretOffset the caret offset at the moment of invocation
 */
data class DependencyInsertionContext(val ktFile: KtFile?, val caretOffset: Int)

/**
 * Strategy that supplies dependency snippets for a specific insertion context
 * (e.g. classic Maven coordinates or SwiftPM packages).
 */
interface DependencySnippetProvider {

    /**
     * The title of the chooser popup shown for this provider's snippets.
     */
    val popupTitle: String

    /**
     * Returns true if this provider's snippets are applicable in the given context.
     */
    fun isApplicable(context: DependencyInsertionContext): Boolean

    /**
     * The snippets offered by this provider.
     */
    val snippets: List<DependencySnippet>
}

/**
 * Provides classic Maven GAV dependency snippets.
 * Always applicable, serving as the default fallback provider.
 */
class MavenDependencySnippetProvider : DependencySnippetProvider {

    override val popupTitle: String = "Select Dependency"

    override fun isApplicable(context: DependencyInsertionContext): Boolean = true

    override val snippets: List<DependencySnippet> = listOf(
        "\"org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2\"",
        "\"com.squareup.okio:okio:3.15.0\""
    ).map { DependencySnippet(label = it, code = it) }
}

/**
 * Provides SwiftPM dependency snippets.
 * Applicable only when the caret is inside a `swiftPMDependencies {}` block
 * (detected via Kotlin PSI, at any nesting depth).
 *
 * See [Kotlin Multiplatform SwiftPM import](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html).
 */
class SwiftPMDependencySnippetProvider : DependencySnippetProvider {

    companion object {
        private const val SWIFT_PM_DEPENDENCIES_BLOCK = "swiftPMDependencies"

        private const val SWIFT_PACKAGE_SNIPPET = """swiftPackage(
            url = url("https://github.com/firebase/firebase-ios-sdk.git"),
            version = from("12.5.0"),
            products = listOf(product("FirebaseAnalytics")),
        )"""

        private val SPM_APPLE_PROTOBUF_SNIPPET = """
            swiftPackage(
                url = url("https://github.com/apple/swift-protobuf.git"),
                version = exact("1.32.0"),
                products = listOf(),
            )
        """.trimIndent()

        private const val LOCAL_SWIFT_PACKAGE_SNIPPET = """localSwiftPackage(
            directory = project.layout.projectDirectory.dir("/path/to/ExamplePackage/"),
            products = listOf("ExamplePackage"),
        )"""
    }

    override val popupTitle: String = "Select SwiftPM Dependency"

    override fun isApplicable(context: DependencyInsertionContext): Boolean {
        val ktFile = context.ktFile ?: return false
        var element = ktFile.findElementAt(context.caretOffset) ?: return false

        // Walk up the PSI tree looking for a swiftPMDependencies call at any nesting depth
        while (true) {
            val call = PsiTreeUtil.getParentOfType(element, KtCallExpression::class.java) ?: return false
            if (call.calleeExpression?.text == SWIFT_PM_DEPENDENCIES_BLOCK) return true
            element = call
        }
    }

    override val snippets: List<DependencySnippet> = listOf(
        DependencySnippet(label = "swiftPackage (Firebase)", code = SWIFT_PACKAGE_SNIPPET),
        DependencySnippet(label = "swiftPackage (Apple/swift-protobuf)", code = SPM_APPLE_PROTOBUF_SNIPPET),
        DependencySnippet(label = "localSwiftPackage (local package)", code = LOCAL_SWIFT_PACKAGE_SNIPPET)
    )
}
