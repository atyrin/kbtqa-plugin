package kbtqa.gradle

import com.intellij.openapi.project.Project
import com.intellij.execution.process.ProcessHandler
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Manages multiple GradleOutputConsole instances to ensure each node has its unique console.
 * Each console is identified by a combination of project path and command type.
 */
class GradleConsoleManager(private val project: Project) {
    
    private val consoles = mutableMapOf<String, GradleOutputConsole>()
    private val tabbedPane = JBTabbedPane()
    
    val component: JComponent
        get() = tabbedPane

    /**
     * Executes a command and displays output in a unique console for the given node.
     */
    fun executeAndDisplay(
        projectPath: String,
        commandType: String,
        processHandler: ProcessHandler?,
        commandDescription: String
    ) {
        val consoleKey = createConsoleKey(projectPath, commandType)
        val console = getOrCreateConsole(consoleKey, projectPath, commandType)
        
        // Switch to the console tab
        switchToConsole(consoleKey)
        
        // Execute the command
        console.executeAndDisplay(processHandler, commandDescription)
    }
    
    /**
     * Gets or creates a console for the specified key.
     */
    private fun getOrCreateConsole(
        consoleKey: String, 
        projectPath: String, 
        commandType: String
    ): GradleOutputConsole {
        return consoles.getOrPut(consoleKey) {
            val console = GradleOutputConsole(project)
            val tabTitle = createTabTitle(projectPath, commandType)
            tabbedPane.addTab(tabTitle, console.component)
            
            // Add closeable tab component
            val tabIndex = tabbedPane.tabCount - 1
            val tabComponent = createCloseableTabComponent(tabTitle, consoleKey)
            tabbedPane.setTabComponentAt(tabIndex, tabComponent)
            
            console
        }
    }
    
    /**
     * Creates a custom tab component with a close button.
     */
    private fun createCloseableTabComponent(title: String, consoleKey: String): JComponent {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        
        // Add the title label
        val label = JBLabel(title)
        panel.add(label, BorderLayout.CENTER)
        
        // Add the close button
        val closeButton = JButton("×")
        closeButton.preferredSize = Dimension(17, 17)
        closeButton.toolTipText = "Close tab"
        closeButton.isBorderPainted = false
        closeButton.isContentAreaFilled = false
        closeButton.isFocusable = false
        closeButton.margin = JBUI.emptyInsets()
        
        closeButton.addActionListener { 
            closeTab(consoleKey)
        }
        
        panel.add(closeButton, BorderLayout.EAST)
        return panel
    }
    
    /**
     * Closes a tab and cleans up the associated console.
     */
    private fun closeTab(consoleKey: String) {
        val console = consoles[consoleKey] ?: return
        val tabIndex = findTabIndex(console)
        
        if (tabIndex >= 0) {
            // Remove the tab
            tabbedPane.removeTabAt(tabIndex)
            // Remove from consoles map
            consoles.remove(consoleKey)
        }
    }
    
    /**
     * Switches to the console tab for the given key.
     */
    private fun switchToConsole(consoleKey: String) {
        val console = consoles[consoleKey] ?: return
        val tabIndex = findTabIndex(console)
        if (tabIndex >= 0) {
            tabbedPane.selectedIndex = tabIndex
        }
    }
    
    /**
     * Finds the tab index for the given console.
     */
    private fun findTabIndex(console: GradleOutputConsole): Int {
        for (i in 0 until tabbedPane.tabCount) {
            if (tabbedPane.getComponentAt(i) == console.component) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Creates a unique key for console identification.
     */
    private fun createConsoleKey(projectPath: String, commandType: String): String {
        val pathKey = if (projectPath.isEmpty()) "root" else projectPath
        return "${pathKey}:${commandType}"
    }
    
    /**
     * Creates a user-friendly tab title.
     */
    private fun createTabTitle(projectPath: String, commandType: String): String {
        val pathDisplay = if (projectPath.isEmpty()) "Root" else projectPath
        return "$pathDisplay - $commandType"
    }
    
    /**
     * Clears all consoles.
     */
    fun clearAll() {
        consoles.values.forEach { it.clear() }
    }
    
    /**
     * Clears console for a specific node.
     */
    fun clear(projectPath: String, commandType: String) {
        val consoleKey = createConsoleKey(projectPath, commandType)
        consoles[consoleKey]?.clear()
    }
}