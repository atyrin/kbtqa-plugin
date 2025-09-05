package kbtqa.helpers.versions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.intellij.ui.CollectionListModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TitledSeparator
import com.intellij.ui.SearchTextField
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import java.awt.datatransfer.StringSelection
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.*

/**
 * Dialog that displays available versions for different tools.
 * Has a left panel with tools list and right panel with versions grid.
 */
class ToolVersionsDialog(
    val project: Project?,
    private val tools: List<ToolVersionsManager.Tool>
) : DialogWrapper(project) {

    private val logger = thisLogger()

    private lateinit var toolsList: JBList<ToolVersionsManager.Tool>
    private lateinit var versionsPanel: JPanel

    private var currentTool: ToolVersionsManager.Tool? = null
    private var searchField: SearchTextField? = null
    private var currentFilter: String = ""
    
    // Track loaded tools and loading states
    private val loadedTools = mutableMapOf<String, ToolVersionsManager.Tool>()
    private val loadingStates = mutableMapOf<String, Boolean>()
    private val toolsManager = ToolVersionsManager.getInstance()

    init {
        title = "Tool Versions"
        setResizable(true)
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        
        if (tools.isEmpty()) {
            mainPanel.add(JBLabel("Failed to load tool versions. Please check your internet connection."), BorderLayout.CENTER)
            return mainPanel
        }
        
        // Initialize versionsPanel first to avoid lateinit access issues
        versionsPanel = JPanel(BorderLayout())
        versionsPanel.border = JBUI.Borders.empty(0, 5, 0, 0)
        
        // Create modern splitter with tools on the left and versions on the right
        val splitter = JBSplitter(false, 0.2f)
        splitter.firstComponent = createToolsPanel()
        splitter.secondComponent = createVersionsPanel()

        mainPanel.add(splitter, BorderLayout.CENTER)
        
        // Add instructions at the bottom
        val instructionsLabel = JBLabel("<html><i>Select a tool on the left. Use the search field to filter versions. Double-click or press Enter to copy a version.</i></html>")
        instructionsLabel.border = JBUI.Borders.empty(10, 0, 0, 0)
        mainPanel.add(instructionsLabel, BorderLayout.SOUTH)
        
        mainPanel.preferredSize = Dimension(800, 500)

        // Auto-select Kotlin and load versions by default (fallback to first tool)
        javax.swing.SwingUtilities.invokeLater {
            try {
                val kotlinIndex = tools.indexOfFirst { it.name.equals("Kotlin", ignoreCase = true) }
                val indexToSelect = when {
                    kotlinIndex >= 0 -> kotlinIndex
                    tools.isNotEmpty() -> 0
                    else -> -1
                }
                if (indexToSelect >= 0) {
                    toolsList.selectedIndex = indexToSelect
                    toolsList.ensureIndexIsVisible(indexToSelect)
                    // Selection listener will trigger version loading
                }
            } catch (_: Exception) {
                // Ignore any errors during auto-selection to avoid breaking dialog
            }
        }

        return mainPanel
    }
    
    /**
     * Creates the left panel with tools list.
     */
    private fun createToolsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 0, 0, 5)
        
        // Header
        val header = TitledSeparator("Tools")
        panel.add(header, BorderLayout.NORTH)
        
        // Create tools list
        toolsList = JBList(CollectionListModel(tools))
        toolsList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        toolsList.cellRenderer = object : ColoredListCellRenderer<ToolVersionsManager.Tool>() {
            override fun customizeCellRenderer(
                list: JList<out ToolVersionsManager.Tool>,
                value: ToolVersionsManager.Tool?,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                if (value != null) {
                    append(value.name)
                    append("  ")
                    when {
                        loadingStates[value.name] == true -> {
                            append("(Loading...)", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)
                        }
                        loadedTools.containsKey(value.name) -> {
                            val loadedTool = loadedTools[value.name]!!
                            append("(${getTotalVersionsCount(loadedTool)} versions)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                        else -> {
                            append("(Click to load)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                    }
                }
            }
        }
        
        // Add selection listener
        toolsList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                val selectedTool = toolsList.selectedValue
                if (selectedTool != null) {
                    updateVersionsDisplayAsync(selectedTool)
                }
            }
        }
        
        val scrollPane = JBScrollPane(toolsList)
        scrollPane.border = JBUI.Borders.empty()
        scrollPane.viewportBorder = JBUI.Borders.empty()
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * Creates the right panel for displaying versions.
     */
    private fun createVersionsPanel(): JComponent {
        // versionsPanel is now pre-initialized in createCenterPanel()
        
        // Show instruction to select a tool instead of automatically loading first tool
        if (tools.isNotEmpty()) {
            val instructionLabel = JBLabel("<html><center>Select a tool from the list to view available versions.<br/>Versions will be loaded on-demand.</center></html>")
            instructionLabel.horizontalAlignment = SwingConstants.CENTER
            versionsPanel.add(instructionLabel, BorderLayout.CENTER)
        } else {
            versionsPanel.add(JBLabel("No tools available."), BorderLayout.CENTER)
        }
        
        return versionsPanel
    }
    
    /**
     * Updates the versions display for the selected tool, fetching versions if needed.
     */
    private fun updateVersionsDisplayAsync(tool: ToolVersionsManager.Tool) {
        // Check if we already have this tool with loaded versions
        val loadedTool = loadedTools[tool.name]
        if (loadedTool != null) {
            // Already loaded, display immediately
            updateVersionsDisplay(loadedTool)
            return
        }
        
        // Check if already loading
        if (loadingStates[tool.name] == true) {
            return
        }
        
        // Display loading state
        versionsPanel.removeAll()
        versionsPanel.add(JBLabel("Loading versions for ${tool.name}..."), BorderLayout.CENTER)
        versionsPanel.revalidate()
        versionsPanel.repaint()
        
        // Update loading state and refresh tool list
        loadingStates[tool.name] = true
        toolsList.repaint()
        
        // Fetch versions in background
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading ${tool.name} Versions", true) {
            private var updatedTool: ToolVersionsManager.Tool? = null
            private var error: Throwable? = null
            
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Fetching ${tool.name} versions..."
                indicator.isIndeterminate = true
                
                try {
                    runBlocking {
                        withContext(Dispatchers.IO) {
                            updatedTool = toolsManager.fetchVersionsForTool(tool)
                        }
                    }
                } catch (e: Exception) {
                    error = e
                }
            }
            
            override fun onSuccess() {
                ApplicationManager.getApplication().invokeLater {
                    loadingStates[tool.name] = false
                    
                    if (error != null) {
                        logger.warn("Failed to load versions for ${tool.name}", error)
                        versionsPanel.removeAll()
                        versionsPanel.add(JBLabel("Failed to load versions for ${tool.name}"), BorderLayout.CENTER)
                        versionsPanel.revalidate()
                        versionsPanel.repaint()
                    } else if (updatedTool != null) {
                        // Store loaded tool and update display
                        loadedTools[tool.name] = updatedTool!!
                        updateVersionsDisplay(updatedTool!!)
                    }
                    
                    // Refresh tool list to update status
                    toolsList.repaint()
                }
            }
            
            override fun onCancel() {
                ApplicationManager.getApplication().invokeLater {
                    loadingStates[tool.name] = false
                    toolsList.repaint()
                }
            }
        })
    }

    /**
     * Updates the versions display for the selected tool.
     */
    private fun updateVersionsDisplay(tool: ToolVersionsManager.Tool) {
        currentTool = tool
        versionsPanel.removeAll()

        // Top bar with title and search
        val topBar = JPanel(BorderLayout())
        topBar.border = JBUI.Borders.empty(0, 0, 10, 0)
        val titleSeparator = TitledSeparator("${tool.name} Versions")
        topBar.add(titleSeparator, BorderLayout.CENTER)

        val sf = SearchTextField()
        sf.text = currentFilter
        sf.toolTipText = "Filter versions"
        sf.addDocumentListener(object : DocumentListener {
            private fun onChange() {
                val newText = sf.text.trim()
                if (newText != currentFilter) {
                    // Save caret position before rebuilding UI
                    val caretPos = try { sf.textEditor.caretPosition } catch (e: Exception) { newText.length }
                    currentFilter = newText
                    currentTool?.let { updateVersionsDisplay(it) }
                    // Restore focus and caret to the new search field after UI rebuild
                    javax.swing.SwingUtilities.invokeLater {
                        searchField?.let { newSf ->
                            newSf.requestFocusInWindow()
                            // Ensure the text matches currentFilter (no-op if same)
                            if (newSf.text != currentFilter) newSf.text = currentFilter
                            val endPos = newSf.text.length
                            try { newSf.textEditor.caretPosition = endPos } catch (_: Exception) {}
                        }
                    }
                }
            }
            override fun insertUpdate(e: DocumentEvent) = onChange()
            override fun removeUpdate(e: DocumentEvent) = onChange()
            override fun changedUpdate(e: DocumentEvent) = onChange()
        })
        topBar.add(sf, BorderLayout.EAST)
        searchField = sf

        versionsPanel.add(topBar, BorderLayout.NORTH)

        if (tool.channels.isEmpty()) {
            versionsPanel.add(JBLabel("No versions available for ${tool.name}."), BorderLayout.CENTER)
        } else {
            val versionsDisplay = createVersionsDisplay(tool)
            versionsPanel.add(versionsDisplay, BorderLayout.CENTER)
        }

        versionsPanel.revalidate()
        versionsPanel.repaint()
    }
    
    /**
     * Creates the versions display component for a tool.
     */
    private fun createVersionsDisplay(tool: ToolVersionsManager.Tool): JComponent {
        if (tool.channels.size == 1) {
            // Single channel - reuse channel panel (with description and proper layout)
            return createChannelPanel(tool.channels.first(), tool)
        } else {
            // Multiple channels - show as tabbed pane
            val tabbedPane = JBTabbedPane()
            
            for (channel in tool.channels) {
                val tabPanel = createChannelPanel(channel, tool)
                tabbedPane.addTab(channel.name, tabPanel)
            }
            
            return tabbedPane
        }
    }
    
    /**
     * Creates a panel for a specific version channel.
     */
    private fun createChannelPanel(channel: VersionsService.VersionChannel, tool: ToolVersionsManager.Tool): JComponent {
        val panel = JPanel(BorderLayout())

        // Apply filter
        val filter = currentFilter
        val filtered = if (filter.isBlank()) channel.versions else channel.versions.filter { it.contains(filter, ignoreCase = true) }

        // Add channel description with count and optional copy repo button
        val countText = if (filter.isBlank()) filtered.size else "$filter: ${filtered.size}"
        val descriptionLabel = JBLabel("<html><i>${channel.description} (${countText})</i></html>")
        descriptionLabel.border = JBUI.Borders.emptyBottom(10)

        val headerPanel = JPanel(BorderLayout())
        headerPanel.border = JBUI.Borders.emptyBottom(10)
        headerPanel.add(descriptionLabel, BorderLayout.WEST)

        // Add Copy repository button for Kotlin Dev/Experimental and Dokka Dev/Test channels
        if (tool.name == "Kotlin" && (channel.name.equals("Dev", ignoreCase = true) || channel.name.equals("Experimental", ignoreCase = true))) {
            val copyButton = JButton("Copy repository")
            copyButton.addActionListener {
                val repoUrl = if (channel.name.equals("Dev", ignoreCase = true)) {
                    "https://redirector.kotlinlang.org/maven/dev"
                } else {
                    "https://redirector.kotlinlang.org/maven/experimental"
                }
                copyVersionToClipboard(repoUrl, itemType = "Repository")
            }
            headerPanel.add(copyButton, BorderLayout.EAST)
        } else if (tool.name == "Dokka" && (channel.name.equals("Dev", ignoreCase = true) || channel.name.equals("Test", ignoreCase = true))) {
            val copyButton = JButton("Copy repository")
            copyButton.addActionListener {
                val repoUrl = if (channel.name.equals("Dev", ignoreCase = true)) {
                    "https://redirector.kotlinlang.org/maven/dokka-dev"
                } else {
                    "https://redirector.kotlinlang.org/maven/dokka-test"
                }
                copyVersionToClipboard(repoUrl, itemType = "Repository")
            }
            headerPanel.add(copyButton, BorderLayout.EAST)
        }

        panel.add(headerPanel, BorderLayout.NORTH)

        if (filtered.isEmpty()) {
            panel.add(JBLabel("No versions match the current filter."), BorderLayout.CENTER)
            return panel
        }

        // Use column layout for appropriate channels, single list for others
        val centerPanel = if (shouldUseColumnsLayout(channel, tool)) {
            createColumnsPanel(filtered)
        } else {
            createSingleListPanel(filtered)
        }

        panel.add(centerPanel, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * Determines if a channel should use columns layout based on tool and channel characteristics.
     */
    private fun shouldUseColumnsLayout(channel: VersionsService.VersionChannel, tool: ToolVersionsManager.Tool): Boolean {
        // Do not apply grouping to Android, Kotlin stable channels, KSP, and Dokka (all channels)
        if (tool.name == "Kotlin" && channel.name == "Stable") {
            return false
        }
        if (tool.name == "Android" && channel.name == "All") {
            return false
        }
        if (tool.name == "KSP") {
            return false
        }
        if (tool.name == "Dokka") {
            return false
        }
        
        // Use columns for channels with many versions that can be grouped by major version
        return channel.versions.size > 10
    }
    
    /**
     * Creates a single list panel (used for channels with fewer versions)
     */
    private fun createSingleListPanel(versions: List<String>): JComponent {
        val versionList = createVersionList(versions)
        return JBScrollPane(versionList).apply {
            border = JBUI.Borders.empty()
            viewportBorder = JBUI.Borders.empty()
        }
    }

    /**
     * Creates a columns panel grouped by major version (used for channels with many versions)
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

            val scrollPane = JBScrollPane(versionJBList).apply {
                border = JBUI.Borders.empty()
                viewportBorder = JBUI.Borders.empty()
            }
            columnPanel.add(scrollPane, BorderLayout.CENTER)

            columnsPanel.add(columnPanel)
        }

        return columnsPanel
    }
    
    /**
     * Creates a version list with proper interaction listeners.
     */
    private fun createVersionList(versions: List<String>): JBList<String> {
        val versionList = JBList(CollectionListModel(versions))
        versionList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        versionList.cellRenderer = SimpleListCellRenderer.create<String> { label, value, _ ->
            label.text = value
            label.border = JBUI.Borders.empty(2, 6)
        }
        addListInteractionListeners(versionList)
        return versionList
    }

    
    /**
     * Adds mouse and keyboard listeners for version interaction.
     */
    private fun addListInteractionListeners(versionList: JBList<String>) {
        // Double-click to copy
        versionList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selectedVersion = versionList.selectedValue
                    selectedVersion?.let { version ->
                        copyVersionToClipboard(version)
                    }
                }
            }
        })
        
        // Enter key to copy
        versionList.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    val selectedVersion = versionList.selectedValue
                    selectedVersion?.let { version ->
                        copyVersionToClipboard(version)
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
     * Gets the total number of versions for a tool across all channels.
     */
    private fun getTotalVersionsCount(tool: ToolVersionsManager.Tool): Int {
        return tool.channels.sumOf { it.versions.size }
    }
    
    /**
     * Copies text to clipboard and shows notification.
     */
    private fun copyVersionToClipboard(text: String, itemType: String = "Version") {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val stringSelection = StringSelection(text)
            clipboard.setContents(stringSelection, null)
            
            logger.info("$itemType copied to clipboard: $text")
            
            // Show notification
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Tool Versions")
                .createNotification("$itemType '$text' has been copied to clipboard", NotificationType.INFORMATION)
                .notify(project)
        } catch (e: Exception) {
            logger.warn("Failed to copy $itemType to clipboard", e)
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
}