package kbtqa.helpers.editor

import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*

/**
 * Action that adds version catalog configuration to settings.gradle.kts files
 * to enable overwriting versions from the version catalog.
 */
class OverwriteVersionCatalogAction :
    BaseSettingsGradleAction("Overwrite version catalog", "Configure DSL for overwriting versions from the version catalog", null) {

    companion object {
        private const val DEPENDENCY_RESOLUTION_MANAGEMENT_BLOCK_NAME = "dependencyResolutionManagement"
        private const val VERSION_CATALOGS_BLOCK_NAME = "versionCatalogs"
        private const val VERSION_CATALOG_EXAMPLE = """version("kotlin", "new-version")"""
    }

    override fun performConfiguration(project: Project, ktFile: KtFile) {
        executeWriteAction(project) {
            val factory = KtPsiFactory(project)
            configureVersionCatalogs(ktFile, factory) { content ->
                // Add new dependencyResolutionManagement block at the end of the file
                ktFile.addContentToFile(factory, content)
            }
        }
    }

    private fun configureVersionCatalogs(
        ktFile: KtFile,
        factory: KtPsiFactory,
        addNewBlock: (String) -> Unit
    ) {
        val dependencyResolutionBlock = ktFile.findBlock(DEPENDENCY_RESOLUTION_MANAGEMENT_BLOCK_NAME)
        if (dependencyResolutionBlock != null) {
            // dependencyResolutionManagement block exists, check for versionCatalogs inside
            val versionCatalogsBlock = findVersionCatalogsInBlock(dependencyResolutionBlock)
            if (versionCatalogsBlock != null) {
                // versionCatalogs block exists, add create("libs") inside if not present
                addLibsCatalogToVersionCatalogs(versionCatalogsBlock, factory)
            } else {
                // versionCatalogs block doesn't exist, add it to dependencyResolutionManagement
                addVersionCatalogsToBlock(dependencyResolutionBlock, factory)
            }
        } else {
            // dependencyResolutionManagement block doesn't exist, create it with versionCatalogs
            val newBlockContent = """
            |dependencyResolutionManagement {
            |    versionCatalogs {
            |        create("libs") {
            |            $VERSION_CATALOG_EXAMPLE
            |        }
            |    }
            |}
            """.trimMargin()
            addNewBlock(newBlockContent)
        }
    }

    private fun findVersionCatalogsInBlock(parentBlock: KtCallExpression): KtCallExpression? {
        val lambdaBody = parentBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
        return lambdaBody?.let { body ->
            PsiTreeUtil.findChildrenOfType(body, KtCallExpression::class.java)
                .find { it.calleeExpression?.text == VERSION_CATALOGS_BLOCK_NAME }
        }
    }

    private fun addVersionCatalogsToBlock(parentBlock: KtCallExpression, factory: KtPsiFactory) {
        val lambdaBody = parentBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
        if (lambdaBody != null) {
            val versionCatalogsContent = """versionCatalogs {
    create("libs") {
        $VERSION_CATALOG_EXAMPLE
    }
}"""
            val versionCatalogsBlock = factory.createExpression(versionCatalogsContent)
            lambdaBody.addBefore(versionCatalogsBlock, lambdaBody.lastChild)
            lambdaBody.addBefore(factory.createNewLine(), lambdaBody.lastChild)
        }
    }

    private fun addLibsCatalogToVersionCatalogs(versionCatalogsBlock: KtCallExpression, factory: KtPsiFactory) {
        val lambdaBody = versionCatalogsBlock.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
        if (lambdaBody != null) {
            // Check if create("libs") already exists
            val existingLibsCatalog = PsiTreeUtil.findChildrenOfType(lambdaBody, KtCallExpression::class.java)
                .find { call ->
                    call.calleeExpression?.text == "create" &&
                    call.valueArguments.firstOrNull()?.getArgumentExpression()?.text == "\"libs\""
                }
            
            if (existingLibsCatalog == null) {
                // Add create("libs") block
                val libsCatalogContent = """create("libs") {
    $VERSION_CATALOG_EXAMPLE
}"""
                val libsCatalogBlock = factory.createExpression(libsCatalogContent)
                lambdaBody.addBefore(libsCatalogBlock, lambdaBody.lastChild)
                lambdaBody.addBefore(factory.createNewLine(), lambdaBody.lastChild)
            }
        }
    }
}