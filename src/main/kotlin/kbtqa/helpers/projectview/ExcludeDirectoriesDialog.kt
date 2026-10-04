package kbtqa.helpers.projectview

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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

/**
 * Categories for grouping excludable items in the dialog.
 * The declaration order defines the display order in the dialog.
 */
enum class ExclusionCategory(val displayName: String) {
    BUILD_OUTPUT("Build Output"),
    GRADLE_CACHE("Gradle Cache"),
    KOTLIN_CACHE("Kotlin Cache"),
    IDE_SETTINGS("IDE Settings"),
    VERSION_CONTROL("Version Control"),
    AI_ASSISTANT("AI Assistant"),
    GIT_IGNORED("Git Ignored"),
    CONFIGURATION_FILES("Configuration Files"),
    OTHER("Other")
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
    private val defaultFileExclusions: Set<String> = emptySet(),
    private val ignoreFilter: IgnoreFilter = NoopIgnoreFilter
) : DialogWrapper(project) {

    private val cardLayout = CardLayout()
    private val mainPanel = JPanel(cardLayout)
    private val contentPanel = JPanel(BorderLayout())
    private val loadingPanel = createLoadingPanel()

    private val checkboxes = mutableMapOf<String, JBCheckBox>()
    private val excludableItems = mutableListOf<ExcludableItem>()

    /**
     * Represents an item (directory or file) that can be excluded from the archive.
     * @param relativePath The relative path from project root, or the pattern key for group items
     * @param isDefaultExclusion Whether this item is excluded by default
     * @param isFile Whether this is a file (true) or directory (false)
     * @param category The category for grouping this item in the UI
     * @param displayLabel Optional label shown instead of [relativePath] (e.g. "*.log — 14 files")
     * @param coveredPaths Concrete relative paths covered by a pattern-group item; empty for plain items
     */
    data class ExcludableItem(
        val relativePath: String,
        val isDefaultExclusion: Boolean,
        val isFile: Boolean = false,
        val category: ExclusionCategory = ExclusionCategory.OTHER,
        val displayLabel: String? = null,
        val coveredPaths: List<String> = emptyList()
    )

    init {
        title = "Select Items to Exclude"
        setOKButtonText("Create Archive")
        setCancelButtonText("Cancel")
        init()
        loadExcludableItemsAsync()
    }

    private fun loadExcludableItemsAsync() {
        isOKActionEnabled = false
        cardLayout.show(mainPanel, LOADING_CARD)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching {
                ExcludableItemsCollector(projectDir, defaultDirectoryExclusions, defaultFileExclusions, ignoreFilter)
                    .collect()
            }
            ApplicationManager.getApplication().invokeLater(
                {
                    result
                        .onSuccess { items ->
                            excludableItems.clear()
                            excludableItems.addAll(items)
                            populateContentPanel()
                            isOKActionEnabled = true
                        }
                        .onFailure { populateErrorPanel(it) }
                    cardLayout.show(mainPanel, CONTENT_CARD)
                },
                ModalityState.any()
            ) { isDisposed }
        }
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

        val headerLabel = JBLabel("Select items to exclude from the archive:")
        headerLabel.border = JBUI.Borders.emptyBottom(10)
        contentPanel.add(headerLabel, BorderLayout.NORTH)

        if (excludableItems.isEmpty()) {
            contentPanel.add(JBLabel("No excludable items found in the project."), BorderLayout.CENTER)
        } else {
            val scrollPane = JBScrollPane(createCheckboxPanel())
            scrollPane.border = JBUI.Borders.empty()
            scrollPane.preferredSize = Dimension(400, 300)
            contentPanel.add(scrollPane, BorderLayout.CENTER)
        }

        val footerLabel = JBLabel("<html><i>Checked items will be excluded from the zip archive.</i></html>")
        footerLabel.border = JBUI.Borders.emptyTop(10)
        contentPanel.add(footerLabel, BorderLayout.SOUTH)

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    /**
     * Creates the panel with checkboxes for all excludable items, grouped by category.
     */
    private fun createCheckboxPanel(): JPanel {
        checkboxes.clear()
        val checkboxPanel = JPanel()
        checkboxPanel.layout = BoxLayout(checkboxPanel, BoxLayout.Y_AXIS)
        checkboxPanel.border = JBUI.Borders.empty(5)

        val itemsByCategory = excludableItems.groupBy { it.category }
        ExclusionCategory.entries.forEach { category ->
            val items = itemsByCategory[category] ?: return@forEach

            val categoryHeader = JBLabel(category.displayName)
            categoryHeader.font = categoryHeader.font.deriveFont(Font.BOLD)
            categoryHeader.border = JBUI.Borders.empty(8, 0, 4, 0)
            checkboxPanel.add(categoryHeader)

            for (item in items) {
                val checkbox = createItemCheckbox(item)
                checkboxes[item.relativePath] = checkbox
                checkboxPanel.add(checkbox)
            }
        }
        return checkboxPanel
    }

    /**
     * Creates a checkbox for a single excludable item with an explanatory tooltip.
     */
    private fun createItemCheckbox(item: ExcludableItem): JBCheckBox {
        val checkbox = JBCheckBox(item.displayLabel ?: item.relativePath)
        checkbox.isSelected = item.isDefaultExclusion
        checkbox.toolTipText = when {
            item.category == ExclusionCategory.GIT_IGNORED -> getGitIgnoredTooltip(item)
            item.isFile && item.isDefaultExclusion -> "This file is excluded by default"
            item.isFile -> "Check to exclude this file from the archive"
            item.isDefaultExclusion -> "This directory is excluded by default (cache/build folder)"
            else -> "Check to exclude this directory from the archive"
        }
        checkbox.border = JBUI.Borders.emptyLeft(16)
        return checkbox
    }

    private fun populateErrorPanel(error: Throwable) {
        checkboxes.clear()
        excludableItems.clear()
        contentPanel.removeAll()
        contentPanel.border = JBUI.Borders.empty()
        val message = error.message ?: "Unknown error"
        contentPanel.add(JBLabel("Failed to load exclusions: $message"), BorderLayout.CENTER)
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
     * Builds the tooltip for a Git Ignored item, listing sample matched paths for pattern groups.
     */
    private fun getGitIgnoredTooltip(item: ExcludableItem): String {
        if (item.coveredPaths.isEmpty()) {
            return "This directory is ignored by .gitignore. Unchecking includes its whole subtree in the archive."
        }
        val sample = item.coveredPaths.take(TOOLTIP_SAMPLE_SIZE)
        val more = item.coveredPaths.size - sample.size
        return buildString {
            append("<html>Files ignored by .gitignore. Unchecking includes all files in this group.<br><br>")
            sample.forEach { append(it).append("<br>") }
            if (more > 0) append("…and ").append(more).append(" more")
            append("</html>")
        }
    }

    /**
     * Returns the set of paths (directories and files) that should be excluded from the archive.
     * Only returns paths for items that are checked in the dialog.
     * Pattern-group items are expanded into the concrete paths they cover.
     */
    fun getSelectedExclusions(): Set<String> = buildSet {
        for (item in excludableItems) {
            if (checkboxes[item.relativePath]?.isSelected != true) continue
            if (item.coveredPaths.isNotEmpty()) addAll(item.coveredPaths) else add(item.relativePath)
        }
    }

    private companion object {
        const val LOADING_CARD = "loading"
        const val CONTENT_CARD = "content"
        const val TOOLTIP_SAMPLE_SIZE = 10
    }
}
