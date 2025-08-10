package kbtqa.gradle

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Service for executing Gradle commands and retrieving project information.
 */
@Service(Service.Level.PROJECT)
class GradleCommandService(private val project: Project) {

    /**
     * Executes a Gradle command and returns the process handler.
     */
    fun executeGradleCommand(
        projectPath: String = "",
        task: String,
        additionalArgs: List<String> = emptyList()
    ): ProcessHandler? {
        return try {
            val commandLine = createGradleCommandLine(projectPath, task, additionalArgs)
            val processHandler = ProcessHandlerFactory.getInstance().createProcessHandler(commandLine)
            ProcessTerminatedListener.attach(processHandler)
            processHandler.startNotify()
            processHandler
        } catch (e: ExecutionException) {
            null
        }
    }

    /**
     * Lists all configurations for a project.
     */
    fun listConfigurations(projectPath: String = ""): ProcessHandler? {
        return executeGradleCommand(projectPath, "resolvableConfigurations", listOf("--console=plain"))
    }

    /**
     * Lists all dependencies for a project.
     */
    fun listDependencies(projectPath: String = ""): ProcessHandler? {
        return executeGradleCommand(projectPath, "dependencies", listOf("--console=plain"))
    }

    /**
     * Shows dependency insights for a specific dependency.
     */
    fun dependencyInsight(projectPath: String = "", dependency: String): ProcessHandler? {
        return executeGradleCommand(
            projectPath, 
            "dependencyInsight", 
            listOf("--dependency", dependency, "--console=plain")
        )
    }

    /**
     * Shows outgoing variants for a project.
     */
    fun outgoingVariants(projectPath: String = ""): ProcessHandler? {
        return executeGradleCommand(projectPath, "outgoingVariants", listOf("--console=plain"))
    }

    /**
     * Lists all subprojects in the Gradle build.
     */
    fun listProjects(): ProcessHandler? {
        return executeGradleCommand("", "projects", listOf("--console=plain"))
    }

    /**
     * Executes a Gradle command synchronously and captures its output.
     * Returns the output as a string, or null if execution fails.
     */
    fun executeGradleCommandAndCaptureOutput(
        projectPath: String = "",
        task: String,
        additionalArgs: List<String> = emptyList()
    ): String? {
        var processOutput: com.intellij.execution.process.ProcessOutput? = null
        val taskModal = object : Task.Modal(project, "Executing Gradle Task", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    val commandLine = createGradleCommandLine(projectPath, task, additionalArgs)
                    val handler = CapturingProcessHandler(commandLine)
                    processOutput = handler.runProcess(30000)
                } catch (e: ExecutionException) {
                    // ExecutionException is caught, processOutput remains null
                }
            }
        }
        ProgressManager.getInstance().run(taskModal)

        return processOutput?.let {
            if (it.exitCode == 0) {
                it.stdout
            } else {
                null
            }
        }
    }

    /**
     * Discovers actual subprojects by executing gradle projects command and parsing output.
     * Returns a list of subproject names.
     */
    fun discoverActualSubprojects(): List<String> {
        val output = executeGradleCommandAndCaptureOutput("", "projects", listOf("--console=plain"))
        return if (output != null) {
            parseProjectsOutput(output)
        } else {
            emptyList()
        }
    }

    /**
     * Parses the output of 'gradle projects' command to extract subproject names.
     * Expected format:
     * Root project 'project-name'
     * +--- Project ':subproject1'
     * +--- Project ':subproject2'
     * \--- Project ':subproject3'
     */
    private fun parseProjectsOutput(output: String): List<String> {
        val subprojects = mutableListOf<String>()
        val lines = output.split('\n')
        
        for (line in lines) {
            val trimmedLine = line.trim()
            // Look for lines that represent subprojects
            // They start with +--- Project ':' or \--- Project ':'
            if (trimmedLine.startsWith("+--- Project ':") || trimmedLine.startsWith("\\--- Project ':")) {
                // Extract the project name between ':' and the closing quote
                val projectPattern = """Project ':(.*?)'""".toRegex()
                val matchResult = projectPattern.find(trimmedLine)
                matchResult?.let { match ->
                    val projectName = match.groupValues[1]
                    if (projectName.isNotEmpty()) {
                        subprojects.add(projectName)
                    }
                }
            }
        }
        
        return subprojects
    }

    private fun createGradleCommandLine(
        projectPath: String,
        task: String,
        additionalArgs: List<String>
    ): GeneralCommandLine {
        val baseDir = project.baseDir?.toNioPath()?.toFile() ?: throw ExecutionException("Project base directory not found")
        
        val gradleExecutable = if (SystemInfo.isWindows) {
            File(baseDir, "gradlew.bat")
        } else {
            File(baseDir, "gradlew")
        }

        val executable = if (gradleExecutable.exists()) {
            gradleExecutable.absolutePath
        } else {
            "gradle" // Fall back to system gradle
        }

        val commandLine = GeneralCommandLine(executable)
        commandLine.workDirectory = baseDir

        // Add project path if specified
        if (projectPath.isNotEmpty()) {
            commandLine.addParameter("$projectPath:$task")
        } else {
            commandLine.addParameter(task)
        }

        // Add additional arguments
        additionalArgs.forEach { commandLine.addParameter(it) }

        return commandLine
    }
}