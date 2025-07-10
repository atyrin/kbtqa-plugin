package kbtqa.stacktraces

import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import java.awt.datatransfer.StringSelection
import java.util.regex.Pattern

/**
 * Action that adds a button to copy stacktraces to clipboard from build tool windows and terminal windows.
 */
class StacktraceCopyAction : AnAction("Copy Stacktrace", "Copy stacktrace to clipboard", StacktraceIcons.COPY_STACKTRACE), DumbAware {

    companion object {
        // Pattern to match Java/Kotlin stacktrace lines
        private val STACKTRACE_LINE_PATTERN = Pattern.compile(
            "\\s*at\\s+([\\w.$]+)\\.(\\w+)\\(([\\w.$]+\\.\\w+:\\d+)\\).*"
        )
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val consoleView = e.getData(LangDataKeys.CONSOLE_VIEW)
        
        // Enable the action only if we have an editor or console view
        e.presentation.isEnabledAndVisible = editor != null || consoleView != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val consoleView = e.getData(LangDataKeys.CONSOLE_VIEW)
        
        when {
            editor != null -> copyStacktraceFromEditor(editor)
            consoleView != null -> copyStacktraceFromConsole(consoleView)
        }
    }

    private fun copyStacktraceFromEditor(editor: Editor) {
        val document = editor.document
        val text = document.text
        val stacktrace = extractStacktrace(text)
        copyToClipboard(stacktrace)
    }

    private fun copyStacktraceFromConsole(consoleView: ConsoleView) {
        if (consoleView is ConsoleViewImpl) {
            val text = consoleView.text
            val stacktrace = extractStacktrace(text)
            copyToClipboard(stacktrace)
        }
    }

    private fun extractStacktrace(text: String): String {
        val lines = text.lines()
        val stacktraceLines = mutableListOf<String>()
        var inStacktrace = false
        var exceptionLine: String? = null
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // Skip lines that start with "at org.gradle."
            if (line.trim().startsWith("at org.gradle.") || line.startsWith("\tat org.gradle.")) {
                continue
            }
            
            // Look for exception class name followed by message
            if (trimmedLine.contains("Exception") || trimmedLine.contains("Error")) {
                if (!trimmedLine.startsWith("at ") && !inStacktrace) {
                    exceptionLine = line
                }
            }
            
            if (STACKTRACE_LINE_PATTERN.matcher(line).matches()) {
                // If we found a stacktrace line and we're not already in a stacktrace,
                // add the exception line if we have one
                if (!inStacktrace && exceptionLine != null) {
                    stacktraceLines.add(exceptionLine)
                }
                
                inStacktrace = true
                stacktraceLines.add(line)
            } else if (inStacktrace && trimmedLine.startsWith("at ")) {
                // Continue collecting stacktrace lines
                stacktraceLines.add(line)
            } else if (trimmedLine.startsWith("Caused by:")) {
                // Include "Caused by" lines
                stacktraceLines.add(line)
                // We're still in a stacktrace
                inStacktrace = true
            } else if (inStacktrace && trimmedLine.isEmpty()) {
                // Empty line might indicate end of stacktrace section
                inStacktrace = false
            }
        }
        
        return if(stacktraceLines.isEmpty()) "No stacktrace found" else stacktraceLines.joinToString("\n")
    }

    private fun copyToClipboard(text: String) {
        if (text.isNotEmpty()) {
            CopyPasteManager.getInstance().setContents(StringSelection(text))
            
            // Show notification that stacktrace was copied
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Stacktrace Copy")
                .createNotification("Stacktrace copied to clipboard", NotificationType.INFORMATION)
                .notify(null)
        }
    }
}