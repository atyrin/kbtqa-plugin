package kbtqa.helpers.editor

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import java.io.IOException

/**
 * Action for build.gradle.kts files that creates the source set directories of a KMP module,
 * each with a simple class named after its source set (e.g. `src/iosMain/kotlin/IosMain.kt`).
 *
 * Targets are read from the `kotlin {}` block of the build script; shared source sets follow
 * the Kotlin default hierarchy template. Existing classes are never overwritten.
 */
class CreateKmpSourceSetsAction : AnAction(
    "Create KMP Source Sets",
    "Create platform and shared source sets of the targets declared in the build script, each with a class",
    null
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        // Always show the action, but enable it only for build.gradle.kts files
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null && file.name == "build.gradle.kts"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val ktFile = e.getData(CommonDataKeys.PSI_FILE) as? KtFile ?: return
        val moduleDir = ktFile.virtualFile?.parent ?: return

        // Make the PSI reflect the latest (possibly unsaved) edits of the build script
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val dialog = CreateKmpSourceSetsDialog(project, moduleDir, KmpBuildScriptParser.parse(ktFile))
        if (!dialog.showAndGet()) return

        createSourceSets(project, moduleDir, dialog.plannedSourceSets, dialog.packageName)
    }

    private fun createSourceSets(
        project: Project,
        moduleDir: VirtualFile,
        sourceSets: List<PlannedSourceSet>,
        packageName: String
    ) {
        var created = 0
        var skipped = 0
        val failed = mutableListOf<String>()

        WriteCommandAction.runWriteCommandAction(project, "Create KMP Source Sets", null, {
            for (sourceSet in sourceSets) {
                val relativePath = sourceSet.relativeFilePath(packageName)
                if (moduleDir.findFileByRelativePath(relativePath) != null) {
                    skipped++
                    continue
                }
                try {
                    val dir = VfsUtil.createDirectoryIfMissing(moduleDir, relativePath.substringBeforeLast('/'))
                        ?: throw IOException("Cannot create directory for $relativePath")
                    val file = dir.createChildData(this, relativePath.substringAfterLast('/'))
                    VfsUtil.saveText(file, sourceSet.fileContent(packageName))
                    created++
                } catch (ex: IOException) {
                    thisLogger().warn("Failed to create $relativePath", ex)
                    failed += sourceSet.name
                }
            }
        })

        val message = buildString {
            append("Created $created class(es) in ${moduleDir.name}")
            if (skipped > 0) append(", skipped $skipped existing")
            append(".")
            if (failed.isNotEmpty()) append("<br>Failed: ${failed.joinToString()}.")
            if (created > 0) append("<br>Reload the Gradle project if new directories are not marked as source roots.")
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("QA Helpers")
            .createNotification(
                "KMP source sets",
                message,
                if (failed.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING
            )
            .notify(project)
    }
}
