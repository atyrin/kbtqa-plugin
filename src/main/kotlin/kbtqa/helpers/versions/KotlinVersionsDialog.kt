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
    
    companion object {
        private val REPOSITORY_URLS = mapOf(
            "Dev" to "https://redirector.kotlinlang.org/maven/dev",
            "Experimental" to "https://redirector.kotlinlang.org/maven/experimental"
        )
        
        private val MULTI_COLUMN_CHANNELS = setOf("Dev", "Experimental")
    }
    
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
        
        // Create the top panel with description and repository copy button for Dev/Experimental
        val topPanel = JPanel(BorderLayout())
        
        // Add channel description
        val descriptionLabel = JBLabel("<html><b>${channel.description}</b></html>")
        descriptionLabel.border = JBUI.Borders.emptyBottom(10)
        topPanel.add(descriptionLabel, BorderLayout.WEST)
        
        // Add repository copy button for Dev and Experimental channels
        REPOSITORY_URLS[channel.name]?.let { repositoryUrl ->
            val copyRepoButton = JButton("Copy Repository URL")
            copyRepoButton.addActionListener {
                copyVersionToClipboard(repositoryUrl, "Repository URL")
            }
            
            // Create a panel with proper margins for the button to maintain button appearance
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 0))
            buttonPanel.add(copyRepoButton)
            topPanel.add(buttonPanel, BorderLayout.EAST)
        }
        
        panel.add(topPanel, BorderLayout.NORTH)
        
        if (channel.versions.isEmpty()) {
            panel.add(JBLabel("No versions available for this channel."), BorderLayout.CENTER)
            return panel
        }
        
        // Use column layout for Dev and Experimental tabs, single list for Stable
        val centerPanel = if (isMultiColumnChannel(channel.name)) {
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
        val versionList = createVersionList(versions)
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
            headerLabel.border = JBUI.Borders.emptyBottom(5)
            columnPanel.add(headerLabel, BorderLayout.NORTH)

            // Create list for this major version group
            val versionJBList = createVersionList(versionList)

            val scrollPane = JBScrollPane(versionJBList)
            columnPanel.add(scrollPane, BorderLayout.CENTER)

            columnsPanel.add(columnPanel)
        }

        return columnsPanel
    }

    private fun createVersionList(versions: List<String>): JBList<String> {
        val versionList = JBList(CollectionListModel(versions))
        versionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        versionList.cellRenderer = SimpleListCellRenderer.create<String> { label, value, _ ->
            label.text = value
        }
        addListInteractionListeners(versionList)
        return versionList
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
                        copyVersionToClipboard(version)
                    }
                }
            }
        })
        
        // Add keyboard support (Enter key)
        versionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    versionList.selectedValue?.let { version ->
                        copyVersionToClipboard(version)
                    }
                }
            }
        })
    }
    
    /**
     * Checks if a channel should use multi-column layout
     */
    private fun isMultiColumnChannel(channelName: String): Boolean {
        return channelName in MULTI_COLUMN_CHANNELS
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
                // Simple major version comparison - compare as version strings
                val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
                val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
                
                // Compare major version (first part), then minor (second part)
                val majorComparison = (parts2.getOrElse(0) { 0 }).compareTo(parts1.getOrElse(0) { 0 })
                if (majorComparison != 0) {
                    majorComparison
                } else {
                    (parts2.getOrElse(1) { 0 }).compareTo(parts1.getOrElse(1) { 0 })
                }
            }
    }
    
    /**
     * Copies text to clipboard and shows a notification.
     * @param text The text to copy
     * @param itemType The type of item being copied (e.g., "Version", "Repository URL")
     */
    private fun copyVersionToClipboard(text: String, itemType: String = "Version") {
        try {
            val selection = StringSelection(text)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(selection, null)
            logger.info("Copied $itemType to clipboard: $text")
            
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Kotlin Versions")
                .createNotification("$itemType '$text' has been copied to clipboard", NotificationType.INFORMATION)
                .notify(project)
        } catch (e: Exception) {
            logger.warn("Failed to copy $itemType to clipboard", e)
            Messages.showErrorDialog(
                "Failed to copy $itemType to clipboard: ${e.message}",
                "Copy Error"
            )
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
}