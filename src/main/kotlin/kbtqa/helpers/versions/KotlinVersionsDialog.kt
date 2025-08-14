package kbtqa.helpers.versions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleListCellRenderer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.GridLayout
import java.awt.FlowLayout

/**
 * Dialog that displays available Kotlin versions from different channels.
 */
class KotlinVersionsDialog(
    val project: Project?,
    private val versionChannels: List<KotlinVersionsService.VersionChannel>
) : DialogWrapper(project) {
    
    private val logger = thisLogger()
    
    init {
        title = "Kotlin Versions"
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        
        if (versionChannels.isEmpty()) {
            mainPanel.add(JBLabel("Failed to load Kotlin versions. Please check your internet connection."), BorderLayout.CENTER)
            return mainPanel
        }
        
        val tabbedPane = JBTabbedPane()
        
        for (channel in versionChannels) {
            val tabPanel = createChannelPanel(channel)
            tabbedPane.addTab(channel.name, tabPanel)
        }
        
        mainPanel.add(tabbedPane, BorderLayout.CENTER)
        
        // Add instructions at the bottom
        val instructionsLabel = JBLabel("<html><i>Double-click on any version or press Enter to copy it to clipboard</i></html>")
        instructionsLabel.border = JBUI.Borders.empty(10, 0, 0, 0)
        mainPanel.add(instructionsLabel, BorderLayout.SOUTH)
        
        mainPanel.preferredSize = Dimension(600, 400)
        return mainPanel
    }
    
    private fun createChannelPanel(channel: KotlinVersionsService.VersionChannel): JComponent {
        val panel = JPanel(BorderLayout())
        
        // Add channel description
        val descriptionLabel = JBLabel("<html><b>${channel.description}</b></html>")
        descriptionLabel.border = JBUI.Borders.empty(0, 0, 10, 0)
        panel.add(descriptionLabel, BorderLayout.NORTH)
        
        if (channel.versions.isEmpty()) {
            panel.add(JBLabel("No versions available for this channel."), BorderLayout.CENTER)
            return panel
        }
        
        // Use column layout for Dev and Experimental tabs, single list for Stable
        val centerPanel = if (channel.name == "Dev" || channel.name == "Experimental") {
            createColumnsPanel(channel.versions)
        } else {
            createSingleListPanel(channel.versions)
        }
        
        panel.add(centerPanel, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * Creates a single list panel (used for Stable channel)
     */
    private fun createSingleListPanel(versions: List<String>): JComponent {
        val safeVersions = versions.map { it.toString() }
        val versionList = JBList(CollectionListModel(safeVersions))
        versionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        versionList.cellRenderer = SimpleListCellRenderer.create<String> { label, value, _ ->
            label.text = value
        }
        
        addListInteractionListeners(versionList)
        return JBScrollPane(versionList)
    }
    
    /**
     * Creates a columns panel grouped by major version (used for Dev and Experimental channels)
     */
    private fun createColumnsPanel(versions: List<String>): JComponent {
        val groupedVersions = groupVersionsByMajor(versions)
        
        if (groupedVersions.isEmpty()) {
            return JBLabel("No versions available for this channel.")
        }
        
        val columnsPanel = JPanel(GridLayout(1, groupedVersions.size, 10, 0))
        
        for ((majorVersion, versionList) in groupedVersions) {
            val columnPanel = JPanel(BorderLayout())
            
            // Add header for the column
            val headerLabel = JBLabel("<html><b>Version ${majorVersion}.x</b></html>")
            headerLabel.border = JBUI.Borders.empty(0, 0, 5, 0)
            columnPanel.add(headerLabel, BorderLayout.NORTH)
            
            // Create list for this major version group
            val safeVersions = versionList.map { it.toString() }
            val versionJBList = JBList(CollectionListModel(safeVersions))
            versionJBList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            versionJBList.cellRenderer = SimpleListCellRenderer.create<String> { label, value, _ ->
                label.text = value
            }
            
            addListInteractionListeners(versionJBList)
            
            val scrollPane = JBScrollPane(versionJBList)
            columnPanel.add(scrollPane, BorderLayout.CENTER)
            
            columnsPanel.add(columnPanel)
        }
        
        return columnsPanel
    }
    
    /**
     * Adds mouse and keyboard listeners to a version list for copying functionality
     */
    private fun addListInteractionListeners(versionList: JBList<String>) {
        // Add double-click listener to copy version to clipboard
        versionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    versionList.selectedValue?.let { version ->
                        copyToClipboard(version)
                        showCopiedNotification(version)
                    }
                }
            }
        })
        
        // Add keyboard support (Enter key)
        versionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    versionList.selectedValue?.let { version ->
                        copyToClipboard(version)
                        showCopiedNotification(version)
                    }
                }
            }
        })
    }
    
    /**
     * Extracts the major version from a version string.
     * For version "2.3.1", returns "2.3"
     * For version "1.9.20", returns "1.9" 
     */
    private fun extractMajorVersion(version: String): String {
        val parts = version.split(".")
        return if (parts.size >= 2) {
            "${parts[0]}.${parts[1]}"
        } else {
            parts.firstOrNull() ?: version
        }
    }
    
    /**
     * Groups versions by their major version and returns them sorted by major version
     */
    private fun groupVersionsByMajor(versions: List<String>): Map<String, List<String>> {
        return versions
            .groupBy { extractMajorVersion(it) }
            .toSortedMap { v1, v2 ->
                // Sort major versions in descending order (newest first)
                compareVersions(v2, v1)
            }
    }
    
    /**
     * Compares two version strings for sorting.
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal.
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".", "-")
        val parts2 = v2.split(".", "-")
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: ""
            val part2 = parts2.getOrNull(i) ?: ""
            
            // Try to compare as numbers first
            val num1 = part1.toIntOrNull()
            val num2 = part2.toIntOrNull()
            
            val comparison = when {
                num1 != null && num2 != null -> num1.compareTo(num2)
                num1 != null && num2 == null -> 1 // Numbers come after text
                num1 == null && num2 != null -> -1 // Text comes before numbers
                else -> part1.compareTo(part2, ignoreCase = true)
            }
            
            if (comparison != 0) {
                return comparison
            }
        }
        
        return 0
    }
    
    private fun copyToClipboard(version: String) {
        try {
            val selection = StringSelection(version)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(selection, null)
            logger.info("Copied Kotlin version to clipboard: $version")
        } catch (e: Exception) {
            logger.warn("Failed to copy version to clipboard", e)
            Messages.showErrorDialog(
                "Failed to copy version to clipboard: ${e.message}",
                "Copy Error"
            )
        }
    }
    
    private fun showCopiedNotification(version: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Kotlin Versions")
            .createNotification("Version '$version' has been copied to clipboard", NotificationType.INFORMATION)
            .notify(project)
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
}