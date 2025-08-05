package kbtqa.helpers

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.*

/**
 * Action that adds build cache configuration to settings.gradle.kts files
 * and ensures org.gradle.caching=true is set in gradle.properties.
 */
class ConfigureBuildCacheAction : BaseSettingsGradleAction("Configure Build Cache", "Configure build cache settings", null) {
    companion object {
        private const val BUILD_CACHE_BLOCK_NAME = "buildCache"
        private const val BUILD_CACHE_CONFIG = """buildCache {
    local {
        directory = File(rootDir, "build-cache")
    }
}"""

        private const val GRADLE_CACHING_PROPERTY_KEY = "org.gradle.caching"
        private const val GRADLE_CACHING_PROPERTY = "org.gradle.caching=true"
    }

    override fun performConfiguration(project: Project, ktFile: KtFile) {
        configureBuildCache(project, ktFile)
        ensureGradleCachingPropertyIsEnabled(project)
    }

    private fun configureBuildCache(project: Project, ktFile: KtFile) {
        if (ktFile.hasBlock(BUILD_CACHE_BLOCK_NAME)) {
            return
        }

        executeWriteAction(project) {
            val factory = KtPsiFactory(project)
            ktFile.addContentToFile(factory, BUILD_CACHE_CONFIG)
        }
    }

    private fun ensureGradleCachingPropertyIsEnabled(project: Project) {
        val projectDir = project.baseDir ?: return
        val gradlePropertiesFile = projectDir.findChild("gradle.properties") ?: return
        val document = FileDocumentManager.getInstance().getDocument(gradlePropertiesFile) ?: return
        val text = document.text

        val propertyKeyExists = text.lines().any { line ->
            line.trim().startsWith(GRADLE_CACHING_PROPERTY_KEY)
        }

        if (propertyKeyExists) {
            return
        }

        WriteCommandAction.runWriteCommandAction(project) {
            val propertyToInsert = if (text.isEmpty() || text.endsWith('\n')) {
                GRADLE_CACHING_PROPERTY + "\n"
            } else {
                "\n" + GRADLE_CACHING_PROPERTY + "\n"
            }
            document.insertString(document.textLength, propertyToInsert)
        }
    }
}