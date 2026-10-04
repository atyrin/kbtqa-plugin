package kbtqa.helpers.editor

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*

/**
 * Reader of the `kotlin {}` block of a `build.gradle.kts` script, working on its Kotlin PSI.
 *
 * It does not evaluate the script: targets are recognised by their DSL calls (`jvm()`, `iosArm64()`,
 * `js { … }`, `android { … }`, …) made directly in the `kotlin {}` block, including calls nested in
 * expressions such as `listOf(iosX64(), iosArm64()).forEach { … }`, but not calls inside nested lambdas.
 * Must be called with read access.
 */
object KmpBuildScriptParser {

    private const val KOTLIN_BLOCK_NAME = "kotlin"
    private const val SOURCE_SETS_BLOCK_NAME = "sourceSets"
    private const val TARGET_NAME_PARAMETER = "name"
    private val SOURCE_SET_FACTORY_CALLS = setOf("create", "register", "maybeCreate")
    private val SOURCE_SET_FACTORY_DELEGATES = setOf("creating", "registering")

    private val presetsByDslName: Map<String, KmpTargetPreset> =
        KmpTargetPreset.entries.flatMap { preset -> preset.dslNames.map { it to preset } }.toMap()

    fun parse(file: KtFile): KmpBuildScriptInfo {
        val kotlinBlock = findKotlinBlock(file)?.lambdaBody()
            ?: return KmpBuildScriptInfo(kotlinBlockFound = false, targets = emptyList(), customSourceSets = emptyList())
        val calls = ownCalls(kotlinBlock)
        return KmpBuildScriptInfo(
            kotlinBlockFound = true,
            targets = parseTargets(calls),
            customSourceSets = parseCustomSourceSets(calls)
        )
    }

    /** The outermost `kotlin {}` block, e.g. the top-level one rather than one nested in `subprojects {}`. */
    private fun findKotlinBlock(file: KtFile): KtCallExpression? =
        PsiTreeUtil.findChildrenOfType(file, KtCallExpression::class.java)
            .filter { it.calleeName == KOTLIN_BLOCK_NAME && it.lambdaBody() != null }
            .minByOrNull { call ->
                generateSequence(call.parent) { it.parent }.takeWhile { it !is KtFile }.count { it is KtLambdaExpression }
            }

    /**
     * Calls made by the statements of [block] itself, in source order: calls nested in arguments and receivers
     * are included, calls inside nested lambdas and local declarations are not.
     */
    private fun ownCalls(block: KtBlockExpression): List<KtCallExpression> {
        val result = mutableListOf<KtCallExpression>()
        fun visit(element: PsiElement) {
            if (element is KtLambdaExpression || element is KtNamedFunction || element is KtClassOrObject) return
            if (element is KtCallExpression) result += element
            var child = element.firstChild
            while (child != null) {
                visit(child)
                child = child.nextSibling
            }
        }
        block.statements.forEach(::visit)
        return result
    }

    private fun parseTargets(calls: List<KtCallExpression>): List<KmpTarget> {
        val targets = LinkedHashMap<String, KmpTarget>()
        for (call in calls) {
            val preset = presetsByDslName[call.calleeName] ?: continue
            if (call.hasExplicitReceiver()) continue
            val configuration = call.lambdaBody()
            if (preset.requiresBlock && configuration == null) continue

            val name = targetName(call) ?: preset.defaultName
            val configurationCalls = configuration
                ?.let { PsiTreeUtil.findChildrenOfType(it, KtCallExpression::class.java) }
                .orEmpty()
                .mapNotNullTo(mutableSetOf()) { it.calleeName }

            // A target may be declared more than once, e.g. `jvm()` and later `jvm { … }`
            val existing = targets[name]
            targets[name] = existing?.copy(configurationCalls = existing.configurationCalls + configurationCalls)
                ?: KmpTarget(preset, name, configurationCalls)
        }
        return targets.values.toList()
    }

    /** Explicit target name: `jvm("desktop")` or `jvm(name = "desktop")`; `null` for `js(IR)` or no arguments. */
    private fun targetName(call: KtCallExpression): String? {
        val arguments = call.valueArgumentList?.arguments.orEmpty()
        val nameArgument = arguments.firstOrNull { it.getArgumentName()?.asName?.asString() == TARGET_NAME_PARAMETER }
            ?: arguments.firstOrNull()?.takeUnless { it.isNamed() }
        return nameArgument?.getArgumentExpression()?.plainString()
    }

    /** Source sets created in `sourceSets {}`: `val fooMain by creating`, `create("fooMain")`, … */
    private fun parseCustomSourceSets(calls: List<KtCallExpression>): List<String> {
        val blocks = calls
            .filter { it.calleeName == SOURCE_SETS_BLOCK_NAME && !it.hasExplicitReceiver() }
            .mapNotNull { it.lambdaBody() }
        return blocks.flatMap { block ->
            val byDelegate = PsiTreeUtil.findChildrenOfType(block, KtProperty::class.java)
                .filter { property ->
                    val delegate = property.delegateExpression
                    val factory = (delegate as? KtCallExpression)?.calleeName
                        ?: (delegate as? KtNameReferenceExpression)?.getReferencedName()
                    factory in SOURCE_SET_FACTORY_DELEGATES
                }
                .mapNotNull { it.name }
            val byCall = PsiTreeUtil.findChildrenOfType(block, KtCallExpression::class.java)
                .filter { it.calleeName in SOURCE_SET_FACTORY_CALLS }
                .mapNotNull { it.valueArgumentList?.arguments?.firstOrNull()?.getArgumentExpression()?.plainString() }
            byDelegate + byCall
        }.distinct()
    }

    private val KtCallExpression.calleeName: String?
        get() = (calleeExpression as? KtNameReferenceExpression)?.getReferencedName()

    private fun KtCallExpression.lambdaBody(): KtBlockExpression? =
        lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression

    /** Whether the call is made on a receiver other than `this`, e.g. `it.jvm()`. */
    private fun KtCallExpression.hasExplicitReceiver(): Boolean {
        val qualified = parent as? KtQualifiedExpression ?: return false
        return qualified.selectorExpression == this && qualified.receiverExpression !is KtThisExpression
    }

    /** Value of a string literal without templates, e.g. `"desktop"`; `null` for anything else. */
    private fun KtExpression.plainString(): String? {
        val template = this as? KtStringTemplateExpression ?: return null
        return (template.entries.singleOrNull() as? KtLiteralStringTemplateEntry)?.text
    }
}
