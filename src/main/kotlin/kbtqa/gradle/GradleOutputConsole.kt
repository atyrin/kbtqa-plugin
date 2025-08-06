package kbtqa.gradle

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.openapi.project.Project
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.util.Key
import javax.swing.JComponent

/**
 * Console for displaying Gradle command output.
 */
class GradleOutputConsole(private val project: Project) {
    
    private val consoleView: ConsoleView = ConsoleViewImpl(project, true)
    
    val component: JComponent
        get() = consoleView.component

    /**
     * Executes a Gradle command and displays its output in the console.
     */
    fun executeAndDisplay(processHandler: ProcessHandler?, commandDescription: String) {
        if (processHandler == null) {
            consoleView.print("Failed to execute command: $commandDescription\n", ConsoleViewContentType.ERROR_OUTPUT)
            return
        }

        consoleView.print("Executing: $commandDescription\n", ConsoleViewContentType.SYSTEM_OUTPUT)
        consoleView.print("=".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        processHandler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                val contentType = when (outputType) {
                    com.intellij.execution.process.ProcessOutputTypes.STDERR -> ConsoleViewContentType.ERROR_OUTPUT
                    else -> ConsoleViewContentType.NORMAL_OUTPUT
                }
                consoleView.print(event.text, contentType)
            }

            override fun processTerminated(event: ProcessEvent) {
                consoleView.print("\n" + "=".repeat(50) + "\n", ConsoleViewContentType.SYSTEM_OUTPUT)
                consoleView.print("Command finished with exit code: ${event.exitCode}\n", ConsoleViewContentType.SYSTEM_OUTPUT)
            }
        })

        consoleView.attachToProcess(processHandler)
    }

    /**
     * Clears the console output.
     */
    fun clear() {
        consoleView.clear()
    }
}