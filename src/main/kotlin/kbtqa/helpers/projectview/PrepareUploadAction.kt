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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Action that prepares a project for uploading to a bug tracker as a reproducer.
 * 
 * This action performs the following steps:
 * 1. Creates a zip archive of the project, excluding cache folders (.gradle, .kotlin, .idea, build)
 * 2. Opens the zip file location in Finder (macOS) or Windows Explorer
 * 
 * Note: Cache folders are excluded from the zip but remain in their original location.
 */
class PrepareUploadAction :
    AnAction("Prepare Upload", "Prepare project for uploading to bug tracker as reproducer", null),
    DumbAware {

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

        // Show dialog to select directories and files to exclude
        val dialog = ExcludeDirectoriesDialog(project, projectDir, ALWAYS_EXCLUDE_CACHE_FOLDERS, DEFAULT_EXCLUDE_FILES)
        if (!dialog.showAndGet()) {
            // User cancelled the dialog
            return
        }
        
        val selectedExclusions = dialog.getSelectedExclusions()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Preparing Project for Upload", true, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    
                    // Step 1: Create zip file (excluding selected folders)
                    indicator.text = "Creating zip archive..."
                    indicator.fraction = 0.0
                    val zipFile = createZipArchive(projectDir, selectedExclusions, indicator)
                    
                    if (indicator.isCanceled) {
                        zipFile.delete()
                        return
                    }
                    
                    // Step 2: Refresh VFS and open in file explorer
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
     * Creates a zip archive of the project directory.
     * The zip file is created in the parent directory of the project.
     * @param projectDir The project directory to archive
     * @param excludedPaths Set of relative paths to exclude from the archive
     * @param indicator Progress indicator for the operation
     */
    private fun createZipArchive(projectDir: File, excludedPaths: Set<String>, indicator: ProgressIndicator): File {
        val projectName = projectDir.name
        val zipFile = File(projectDir.parentFile, "${projectName}.zip")
        
        // Rename existing zip file if it exists (adding _old_ and timestamp)
        if (zipFile.exists()) {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val oldZipFile = File(projectDir.parentFile, "${projectName}_old_${timestamp}.zip")
            zipFile.renameTo(oldZipFile)
        }
        
        // Collect all files to zip (excluding selected folders)
        val filesToZip = mutableListOf<File>()
        collectFilesToZip(projectDir, projectDir, excludedPaths, filesToZip)
        
        val totalFiles = filesToZip.size
        
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            filesToZip.forEachIndexed { index, file ->
                if (indicator.isCanceled) return@use
                
                val relativePath = file.relativeTo(projectDir).path
                indicator.text2 = "Zipping: $relativePath"
                indicator.fraction = (index.toDouble() / totalFiles) * 0.9
                
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
     * Collects all files to include in the zip archive, excluding selected folders and files.
     * @param projectRoot The root directory of the project (used for relative path calculation)
     * @param dir The current directory being scanned
     * @param excludedPaths Set of relative paths (directories and files) to exclude from the archive
     * @param result List to collect files to include in the archive
     */
    private fun collectFilesToZip(projectRoot: File, dir: File, excludedPaths: Set<String>, result: MutableList<File>) {
        if (!dir.isDirectory) return
        
        dir.listFiles()?.forEach { file ->
            // Calculate relative path for exclusion check
            val relativePath = file.relativeTo(projectRoot).path
            val shouldExclude = excludedPaths.contains(relativePath)
            
            if (file.isDirectory) {
                if (!shouldExclude) {
                    result.add(file)
                    collectFilesToZip(projectRoot, file, excludedPaths, result)
                }
            } else {
                // Also check file exclusions
                if (!shouldExclude) {
                    result.add(file)
                }
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

// Cache folders that should always be excluded from the zip archive
private val ALWAYS_EXCLUDE_CACHE_FOLDERS = setOf(".gradle", ".kotlin", ".idea", ".git", ".junie")

// Files that should be excluded by default from the zip archive
private val DEFAULT_EXCLUDE_FILES = setOf("local.properties")