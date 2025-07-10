package kbtqa.stacktraces

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPlainText
import java.awt.datatransfer.StringSelection
import java.util.regex.Pattern

/**
 * Line marker provider that shows a "Copy Stacktrace" button near the first line of a stacktrace.
 */
class StacktraceLineMarkerProvider : LineMarkerProvider {

    companion object {
        // Pattern to match Java/Kotlin stacktrace lines
        private val STACKTRACE_LINE_PATTERN = Pattern.compile(
            "\\s*at\\s+([\\w.$]+)\\.(\\w+)\\(([\\w.$]+\\.\\w+:\\d+)\\).*"
        )
        
        // Pattern to match exception lines
        private val EXCEPTION_LINE_PATTERN = Pattern.compile(
            ".*(?:Exception|Error)(?::|\\s+in\\s+).*"
        )
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // We're only interested in plain text elements (like in console output)
        if (element !is PsiPlainText) return null
        
        val text = element.text
        
        // Check if this is an exception line or a stacktrace line
        if (isExceptionLine(text) || isStacktraceLine(text)) {
            return createLineMarkerInfo(element)
        }
        
        return null
    }

    private fun isExceptionLine(text: String): Boolean {
        return EXCEPTION_LINE_PATTERN.matcher(text).matches()
    }

    private fun isStacktraceLine(text: String): Boolean {
        return STACKTRACE_LINE_PATTERN.matcher(text).matches()
    }

    private fun createLineMarkerInfo(element: PsiElement): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(
            element,
            element.textRange,
            StacktraceIcons.COPY_STACKTRACE,
            { "Copy Stacktrace" },
            { _, psiElement ->
                val document = psiElement.containingFile.viewProvider.document
                if (document != null) {
                    val stacktrace = extractStacktrace(document, psiElement)
                    copyToClipboard(stacktrace)
                }
            },
            GutterIconRenderer.Alignment.RIGHT,
            { "Copy Stacktrace" }
        )
    }

    private fun extractStacktrace(document: Document, element: PsiElement): String {
        val lineNumber = document.getLineNumber(element.textOffset)
        val lines = document.text.lines()
        val stacktraceLines = mutableListOf<String>()
        
        // Add the current line (exception or first stacktrace line)
        stacktraceLines.add(lines[lineNumber])
        
        // Look for additional stacktrace lines below
        var currentLine = lineNumber + 1
        while (currentLine < lines.size) {
            val line = lines[currentLine]
            if (isStacktraceLine(line) || line.trim().startsWith("at ") || line.trim().startsWith("Caused by:")) {
                stacktraceLines.add(line)
                currentLine++
            } else if (line.trim().isEmpty()) {
                // Empty line might indicate end of stacktrace section
                break
            } else {
                break
            }
        }
        
        return stacktraceLines.joinToString("\n")
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