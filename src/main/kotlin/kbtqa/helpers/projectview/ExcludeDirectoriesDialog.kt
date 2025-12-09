package kbtqa.helpers.projectview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.AsyncProcessIcon
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.io.File
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Categories for grouping excludable items in the dialog.
 */
enum class ExclusionCategory(val displayName: String, val order: Int) {
    BUILD_OUTPUT("Build Output", 1),
    GRADLE_CACHE("Gradle Cache", 2),
    KOTLIN_CACHE("Kotlin Cache", 3),
    IDE_SETTINGS("IDE Settings", 4),
    VERSION_CONTROL("Version Control", 5),
    AI_ASSISTANT("AI Assistant", 6),
    CONFIGURATION_FILES("Configuration Files", 7),
    OTHER("Other", 8)
}

/**
 * Dialog that allows users to select directories and files to exclude from the zip archive.
 * Shows a list of directories and files with checkboxes - checked items will be excluded.
 * Items are grouped by category for better organization.
 */
class ExcludeDirectoriesDialog(
    project: Project?,
    private val projectDir: File,
    private val defaultDirectoryExclusions: Set<String>,
    private val defaultFileExclusions: Set<String> = emptySet()
) : DialogWrapper(project) {

    private val cardLayout = CardLayout()
    private val mainPanel = JPanel(cardLayout)
    private val contentPanel = JPanel(BorderLayout())
    private val loadingPanel = createLoadingPanel()

    private val checkboxes = mutableMapOf<String, JBCheckBox>()
    private val excludableItems = mutableListOf<ExcludableItem>()

    /**
     * Represents an item (directory or file) that can be excluded from the archive.
     * @param relativePath The relative path from project root
     * @param isDefaultExclusion Whether this item is excluded by default
     * @param isFile Whether this is a file (true) or directory (false)
     * @param category The category for grouping this item in the UI
     */
    data class ExcludableItem(
        val relativePath: String,
        val isDefaultExclusion: Boolean,
        val isFile: Boolean = false,
        val category: ExclusionCategory = ExclusionCategory.OTHER
    )

    init {
        title = "Select Items to Exclude"
        setOKButtonText("Create Archive")
        setCancelButtonText("Cancel")
        init()
        loadExcludableItemsAsync()
    }

    /**
     * Scans the project directory to find all directories and files that can be excluded.
     * This includes default exclusions, build directories in Gradle modules, and default excluded files.
     *
     * Optimization: Some items are only searched at specific locations:
     * - .idea, .junie directories: only at project root level
     * - local.properties file: only at project root level
     * - build directory: only at the same level as build.gradle or build.gradle.kts
     */
    private fun collectExcludableItems(): List<ExcludableItem> {
        val result = mutableListOf<ExcludableItem>()
        scanTopLevelOnlyItems(result)
        scanDirectory(projectDir, "", result)
        // Sort by category order first, then by relative path within each category
        result.sortWith(compareBy({ it.category.order }, { it.relativePath }))
        return result
    }

    /**
     * Scans for items that can only exist at the top project level.
     * This includes .idea, .junie directories and local.properties file.
     */
    private fun scanTopLevelOnlyItems(target: MutableList<ExcludableItem>) {
        projectDir.listFiles()?.forEach { file ->
            when {
                file.isDirectory && file.name in TOP_LEVEL_ONLY_DIRECTORIES -> {
                    target.add(ExcludableItem(
                        relativePath = file.name,
                        isDefaultExclusion = true,
                        isFile = false,
                        category = getCategoryForDirectory(file.name)
                    ))
                }
                file.isFile && file.name in TOP_LEVEL_ONLY_FILES -> {
                    target.add(ExcludableItem(
                        relativePath = file.name,
                        isDefaultExclusion = true,
                        isFile = true,
                        category = getCategoryForFile(file.name)
                    ))
                }
            }
        }
    }

    private fun scanDirectory(dir: File, relativePath: String, target: MutableList<ExcludableItem>) {
        if (!dir.isDirectory) return

        dir.listFiles()?.forEach { file ->
            val childRelativePath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"

            if (file.isDirectory) {
                // Skip top-level-only directories (already handled in scanTopLevelOnlyItems)
                if (file.name in TOP_LEVEL_ONLY_DIRECTORIES) {
                    return@forEach
                }

                // Show default exclusion directories that can appear in subdirectories
                when (file.name) {
                    in defaultDirectoryExclusions -> {
                        target.add(ExcludableItem(
                            relativePath = childRelativePath,
                            isDefaultExclusion = true,
                            isFile = false,
                            category = getCategoryForDirectory(file.name)
                        ))
                    }
                    // Show build directories in Gradle module directories
                    "build" if isGradleModuleDirectory(dir) -> {
                        target.add(ExcludableItem(
                            relativePath = childRelativePath,
                            isDefaultExclusion = true,
                            isFile = false,
                            category = ExclusionCategory.BUILD_OUTPUT
                        ))
                    }
                    else -> {
                        // Recursively scan subdirectories to find nested build folders
                        scanDirectory(file, childRelativePath, target)
                    }
                }
            } else if (file.isFile) {
                // Skip top-level-only files (already handled in scanTopLevelOnlyItems)
                if (file.name in TOP_LEVEL_ONLY_FILES) {
                    return@forEach
                }
                // Check if this file should be excluded by default
                if (file.name in defaultFileExclusions) {
                    target.add(ExcludableItem(
                        relativePath = childRelativePath,
                        isDefaultExclusion = true,
                        isFile = true,
                        category = getCategoryForFile(file.name)
                    ))
                }
            }
        }
    }

    /**
     * Determines the category for a directory based on its name.
     */
    private fun getCategoryForDirectory(name: String): ExclusionCategory {
        return when (name) {
            "build" -> ExclusionCategory.BUILD_OUTPUT
            ".gradle" -> ExclusionCategory.GRADLE_CACHE
            ".kotlin" -> ExclusionCategory.KOTLIN_CACHE
            ".idea" -> ExclusionCategory.IDE_SETTINGS
            ".git" -> ExclusionCategory.VERSION_CONTROL
            ".junie" -> ExclusionCategory.AI_ASSISTANT
            else -> ExclusionCategory.OTHER
        }
    }

    /**
     * Determines the category for a file based on its name.
     */
    private fun getCategoryForFile(name: String): ExclusionCategory {
        return when (name) {
            "local.properties" -> ExclusionCategory.CONFIGURATION_FILES
            else -> ExclusionCategory.OTHER
        }
    }

    private fun loadExcludableItemsAsync() {
        isOKActionEnabled = false
        cardLayout.show(mainPanel, LOADING_CARD)

        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching { collectExcludableItems() }
                .onSuccess { items ->
                    SwingUtilities.invokeLater {
                        excludableItems.clear()
                        excludableItems.addAll(items)
                        populateContentPanel()
                        cardLayout.show(mainPanel, CONTENT_CARD)
                        isOKActionEnabled = true
                    }
                }
                .onFailure {
                    SwingUtilities.invokeLater {
                        populateErrorPanel(it)
                        cardLayout.show(mainPanel, CONTENT_CARD)
                    }
                }
        }
    }

    /**
     * Checks if the given directory is a Gradle module directory
     * (contains build.gradle or build.gradle.kts).
     */
    private fun isGradleModuleDirectory(dir: File): Boolean {
        return dir.listFiles()?.any { it.isFile && it.name in setOf("build.gradle", "build.gradle.kts") } == true
    }

    override fun createCenterPanel(): JComponent {
        mainPanel.border = JBUI.Borders.empty(10)
        mainPanel.add(loadingPanel, LOADING_CARD)
        mainPanel.add(contentPanel, CONTENT_CARD)
        cardLayout.show(mainPanel, LOADING_CARD)
        mainPanel.preferredSize = Dimension(450, 400)
        return mainPanel
    }

    private fun populateContentPanel() {
        contentPanel.removeAll()
        contentPanel.border = JBUI.Borders.empty()

        // Header label
        val headerLabel = JBLabel("Select items to exclude from the archive:")
        headerLabel.border = JBUI.Borders.emptyBottom(10)
        contentPanel.add(headerLabel, BorderLayout.NORTH)

        if (excludableItems.isEmpty()) {
            contentPanel.add(JBLabel("No excludable items found in the project."), BorderLayout.CENTER)
        } else {
            checkboxes.clear()
            // Create panel with checkboxes grouped by category
            val checkboxPanel = JPanel()
            checkboxPanel.layout = BoxLayout(checkboxPanel, BoxLayout.Y_AXIS)
            checkboxPanel.border = JBUI.Borders.empty(5)

            // Group items by category
            val itemsByCategory = excludableItems.groupBy { it.category }
            
            // Iterate through categories in order
            ExclusionCategory.entries
                .filter { it in itemsByCategory }
                .forEach { category ->
                    val items = itemsByCategory[category] ?: return@forEach
                    
                    // Add category header
                    val categoryHeader = JBLabel(category.displayName)
                    categoryHeader.font = categoryHeader.font.deriveFont(Font.BOLD)
                    categoryHeader.border = JBUI.Borders.empty(8, 0, 4, 0)
                    checkboxPanel.add(categoryHeader)
                    
                    // Add checkboxes for items in this category
                    for (item in items) {
                        val checkbox = JBCheckBox(item.relativePath)
                        checkbox.isSelected = item.isDefaultExclusion
                        checkbox.toolTipText = when {
                            item.isFile && item.isDefaultExclusion -> "This file is excluded by default"
                            item.isFile -> "Check to exclude this file from the archive"
                            item.isDefaultExclusion -> "This directory is excluded by default (cache/build folder)"
                            else -> "Check to exclude this directory from the archive"
                        }
                        checkbox.border = JBUI.Borders.emptyLeft(16)
                        checkboxes[item.relativePath] = checkbox
                        checkboxPanel.add(checkbox)
                    }
                }

            val scrollPane = JBScrollPane(checkboxPanel)
            scrollPane.border = JBUI.Borders.empty()
            scrollPane.preferredSize = Dimension(400, 300)
            contentPanel.add(scrollPane, BorderLayout.CENTER)
        }

        // Footer with info
        val footerLabel = JBLabel("<html><i>Checked items will be excluded from the zip archive.</i></html>")
        footerLabel.border = JBUI.Borders.emptyTop(10)
        contentPanel.add(footerLabel, BorderLayout.SOUTH)

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun populateErrorPanel(error: Throwable) {
        checkboxes.clear()
        excludableItems.clear()
        contentPanel.removeAll()
        contentPanel.border = JBUI.Borders.empty()
        val message = error.message ?: "Unknown error"
        val label = JBLabel("Failed to load exclusions: ${'$'}message")
        contentPanel.add(label, BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun createLoadingPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(10)
        val loadingIcon = AsyncProcessIcon("loading_exclusions")
        val loadingLabel = JBLabel("Loading excludable items...")
        val loadingContent = JPanel()
        loadingContent.layout = BoxLayout(loadingContent, BoxLayout.Y_AXIS)
        loadingContent.border = JBUI.Borders.empty(10)
        loadingIcon.alignmentX = JComponent.CENTER_ALIGNMENT
        loadingLabel.alignmentX = JComponent.CENTER_ALIGNMENT
        loadingContent.add(loadingIcon)
        loadingContent.add(JBUI.Panels.simplePanel().apply { add(loadingLabel) })
        panel.add(loadingContent, BorderLayout.CENTER)
        return panel
    }

    /**
     * Returns the set of paths (directories and files) that should be excluded from the archive.
     * Only returns paths for items that are checked in the dialog.
     */
    fun getSelectedExclusions(): Set<String> {
        return checkboxes.filter { it.value.isSelected }.keys.toSet()
    }

    private companion object {
        const val LOADING_CARD = "loading"
        const val CONTENT_CARD = "content"
    }
}

// Directories that should only be searched at the top project level
private val TOP_LEVEL_ONLY_DIRECTORIES = setOf(".idea", ".junie")

// Files that should only be searched at the top project level
private val TOP_LEVEL_ONLY_FILES = setOf("local.properties")
