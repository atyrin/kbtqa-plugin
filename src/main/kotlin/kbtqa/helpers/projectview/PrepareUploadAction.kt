package kbtqa.helpers.projectview

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Action that prepares a project for uploading to a bug tracker as a reproducer.
 * 
 * This action performs the following steps:
 * 1. Deletes cache folders (.gradle, .kotlin, build) from the project root and all subprojects
 * 2. Creates a zip archive of the cleaned project
 * 3. Opens the zip file location in Finder (macOS) or Windows Explorer
 */
class PrepareUploadAction :
    AnAction("Prepare Upload", "Prepare project for uploading to bug tracker as reproducer", null),
    DumbAware {

    companion object {
        // Cache folders that should always be deleted regardless of location
        private val ALWAYS_DELETE_CACHE_FOLDERS = setOf(".gradle", ".kotlin")
        // Gradle build script file names - used to identify Gradle module directories
        private val GRADLE_BUILD_FILES = setOf("build.gradle", "build.gradle.kts")
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        // Show action only when the selected item is the project root directory
        val isProjectRoot = project != null && 
                project.basePath != null && 
                selectedFile != null && 
                selectedFile.path == project.basePath
        
        e.presentation.isVisible = isProjectRoot
        e.presentation.isEnabled = isProjectRoot
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return
        val projectDir = File(basePath)

        if (!projectDir.exists() || !projectDir.isDirectory) {
            Messages.showErrorDialog(project, "Project directory not found: $basePath", "Prepare Upload Error")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing Project for Upload", true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    
                    // Step 1: Delete cache folders
                    indicator.text = "Deleting cache folders..."
                    indicator.fraction = 0.0
                    deleteCacheFolders(projectDir, indicator)
                    
                    if (indicator.isCanceled) return
                    
                    // Step 2: Create zip file
                    indicator.text = "Creating zip archive..."
                    indicator.fraction = 0.5
                    val zipFile = createZipArchive(projectDir, indicator)
                    
                    if (indicator.isCanceled) {
                        zipFile.delete()
                        return
                    }
                    
                    // Step 3: Refresh VFS and open in file explorer
                    indicator.text = "Opening file location..."
                    indicator.fraction = 0.9

                    // Refresh only the specific file to avoid freezing the UI with a full refresh
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(zipFile)

                    ApplicationManager.getApplication().invokeLater {
                        // Open the zip file location in file explorer
                        openInFileExplorer(zipFile)
                    }
                    
                    indicator.fraction = 1.0
                } catch (ex: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to prepare project: ${ex.message}",
                            "Prepare Upload Error"
                        )
                    }
                }
            }
        })
    }

    /**
     * Recursively deletes all cache folders (.gradle, .kotlin, build) from the project directory.
     */
    private fun deleteCacheFolders(projectDir: File, indicator: ProgressIndicator) {
        val foldersToDelete = mutableListOf<File>()
        
        // Find all cache folders
        findCacheFolders(projectDir, foldersToDelete)
        
        // Delete found folders
        val totalFolders = foldersToDelete.size
        foldersToDelete.forEachIndexed { index, folder ->
            if (indicator.isCanceled) return
            indicator.text2 = "Deleting: ${folder.relativeTo(projectDir).path}"
            indicator.fraction = (index.toDouble() / totalFolders) * 0.5
            deleteRecursively(folder)
        }
    }

    /**
     * Recursively finds all cache folders in the project directory.
     * - .gradle and .kotlin folders are always considered cache folders
     * - "build" folders are only considered cache folders if they are in a Gradle module directory
     *   (i.e., the parent directory contains build.gradle or build.gradle.kts)
     */
    private fun findCacheFolders(dir: File, result: MutableList<File>) {
        if (!dir.isDirectory) return
        
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                when {
                    file.name in ALWAYS_DELETE_CACHE_FOLDERS -> {
                        // Always delete .gradle and .kotlin folders
                        result.add(file)
                    }
                    file.name == "build" && isGradleModuleDirectory(dir) -> {
                        // Only delete "build" folder if it's in a Gradle module directory
                        result.add(file)
                    }
                    else -> {
                        // Recurse into other directories
                        findCacheFolders(file, result)
                    }
                }
            }
        }
    }

    /**
     * Checks if the given directory is a Gradle module directory
     * (contains build.gradle or build.gradle.kts).
     */
    private fun isGradleModuleDirectory(dir: File): Boolean {
        return dir.listFiles()?.any { it.isFile && it.name in GRADLE_BUILD_FILES } == true
    }

    /**
     * Recursively deletes a directory and all its contents.
     */
    private fun deleteRecursively(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { deleteRecursively(it) }
        }
        file.delete()
    }

    /**
     * Creates a zip archive of the project directory.
     * The zip file is created in the parent directory of the project.
     */
    private fun createZipArchive(projectDir: File, indicator: ProgressIndicator): File {
        val projectName = projectDir.name
        val zipFile = File(projectDir.parentFile, "${projectName}.zip")
        
        // Delete existing zip file if it exists
        if (zipFile.exists()) {
            zipFile.delete()
        }
        
        // Collect all files to zip (excluding cache folders)
        val filesToZip = mutableListOf<File>()
        collectFilesToZip(projectDir, filesToZip)
        
        val totalFiles = filesToZip.size
        
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            filesToZip.forEachIndexed { index, file ->
                if (indicator.isCanceled) return@use
                
                val relativePath = file.relativeTo(projectDir).path
                indicator.text2 = "Zipping: $relativePath"
                indicator.fraction = 0.5 + (index.toDouble() / totalFiles) * 0.4
                
                val entryPath = "$projectName/$relativePath"
                
                if (file.isDirectory) {
                    zipOut.putNextEntry(ZipEntry("$entryPath/"))
                    zipOut.closeEntry()
                } else {
                    zipOut.putNextEntry(ZipEntry(entryPath))
                    Files.copy(file.toPath(), zipOut)
                    zipOut.closeEntry()
                }
            }
        }
        
        return zipFile
    }

    /**
     * Collects all files to include in the zip archive, excluding cache folders.
     * Uses the same logic as findCacheFolders to determine which folders to exclude.
     */
    private fun collectFilesToZip(dir: File, result: MutableList<File>) {
        if (!dir.isDirectory) return
        
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val shouldExclude = when {
                    file.name in ALWAYS_DELETE_CACHE_FOLDERS -> true
                    file.name == "build" && isGradleModuleDirectory(dir) -> true
                    else -> false
                }
                if (!shouldExclude) {
                    result.add(file)
                    collectFilesToZip(file, result)
                }
            } else {
                result.add(file)
            }
        }
    }

    /**
     * Opens the file's parent directory in the system file explorer and selects the file.
     */
    private fun openInFileExplorer(file: File) {
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                os.contains("mac") -> {
                    // macOS: Use 'open -R' to reveal the file in Finder
                    Runtime.getRuntime().exec(arrayOf("open", "-R", file.absolutePath))
                }
                os.contains("win") -> {
                    // Windows: Use 'explorer /select,' to open Explorer and select the file
                    Runtime.getRuntime().exec(arrayOf("explorer", "/select,", file.absolutePath))
                }
                os.contains("linux") -> {
                    // Linux: Try to open the parent directory with xdg-open
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file.parentFile)
                    } else {
                        Runtime.getRuntime().exec(arrayOf("xdg-open", file.parentFile.absolutePath))
                    }
                }
                else -> {
                    // Fallback: Try Desktop API
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file.parentFile)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently ignore if we can't open the file explorer
            // The user will still see the success message with the file path
        }
    }
}
