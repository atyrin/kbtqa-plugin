package kbtqa.helpers.editor

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBSplitter
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

/**
 * Dialog for choosing the KMP targets whose source sets should be created.
 * Targets detected in the build script are pre-selected; all other presets are offered unchecked.
 * The right-hand side previews the resulting source set tree and marks classes that already exist.
 */
class CreateKmpSourceSetsDialog(
    project: Project,
    private val moduleDir: VirtualFile,
    private val scriptInfo: KmpBuildScriptInfo
) : DialogWrapper(project) {

    private val targetCheckBoxes = linkedMapOf<KmpTarget, JBCheckBox>()
    private val includeTestsCheckBox = JBCheckBox("Include test source sets", true)
    private val includeCustomCheckBox = JBCheckBox(
        "Include custom source sets: ${scriptInfo.customSourceSets.joinToString()}",
        true
    )
    private val packageField = JBTextField(20)
    private val previewArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, UIUtil.getLabelFont().size)
    }
    private val summaryLabel = JBLabel()

    init {
        title = "Create KMP Source Sets"
        setOKButtonText("Create")
        init()
        updatePreview()
    }

    /** Targets checked in the dialog. */
    val selectedTargets: List<KmpTarget>
        get() = targetCheckBoxes.filterValues { it.isSelected }.keys.toList()

    /** Package of the generated classes; empty for the default package. */
    val packageName: String
        get() = packageField.text.trim()

    /** Source sets to create with the current selection. */
    val plannedSourceSets: List<PlannedSourceSet>
        get() = KmpSourceSetPlanner.plan(
            targets = selectedTargets,
            customSourceSets = if (includeCustomCheckBox.isSelected) scriptInfo.customSourceSets else emptyList(),
            includeTests = includeTestsCheckBox.isSelected
        )

    override fun createCenterPanel(): JComponent {
        val headerText = when {
            !scriptInfo.kotlinBlockFound ->
                "No kotlin {} block found in the build script. Select targets manually."
            scriptInfo.targets.isEmpty() ->
                "No targets found in the kotlin {} block. Select targets manually."
            else ->
                "Detected ${scriptInfo.targets.size} target(s) in the build script: " +
                    scriptInfo.targets.joinToString { it.name }
        }

        val splitter = JBSplitter(false, 0.4f).apply {
            firstComponent = JBScrollPane(createTargetsPanel()).apply { border = JBUI.Borders.empty() }
            secondComponent = JPanel(BorderLayout()).apply {
                add(TitledSeparator("Source sets in ${moduleDir.name}"), BorderLayout.NORTH)
                add(JBScrollPane(previewArea), BorderLayout.CENTER)
                add(summaryLabel, BorderLayout.SOUTH)
            }
        }

        return JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            add(JBLabel(headerText), BorderLayout.NORTH)
            add(splitter, BorderLayout.CENTER)
            add(createOptionsPanel(), BorderLayout.SOUTH)
            preferredSize = Dimension(JBUI.scale(760), JBUI.scale(560))
        }
    }

    private fun createTargetsPanel(): JComponent {
        val panel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val detectedPresets = scriptInfo.targets.map { it.preset }.toSet()
        val candidates = KmpTargetPreset.entries.flatMap { preset ->
            if (preset in detectedPresets) scriptInfo.targets.filter { it.preset == preset }
            else listOf(KmpTarget(preset))
        }

        candidates.groupBy { it.preset.group }.forEach { (group, targets) ->
            panel.add(TitledSeparator(group.displayName).apply { alignmentX = JComponent.LEFT_ALIGNMENT })
            targets.forEach { target ->
                val label = if (target.name == target.preset.defaultName) target.preset.dslDeclaration
                else "${target.name} — ${target.preset.dslDeclaration}"
                val checkBox = JBCheckBox(label, target in scriptInfo.targets).apply {
                    alignmentX = JComponent.LEFT_ALIGNMENT
                    addActionListener { updatePreview() }
                }
                targetCheckBoxes[target] = checkBox
                panel.add(checkBox)
            }
        }
        return JPanel(BorderLayout()).apply { add(panel, BorderLayout.NORTH) }
    }

    private fun createOptionsPanel(): JComponent {
        includeTestsCheckBox.addActionListener { updatePreview() }
        includeCustomCheckBox.addActionListener { updatePreview() }
        includeCustomCheckBox.isVisible = scriptInfo.customSourceSets.isNotEmpty()
        packageField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) = updatePreview()
        })

        return JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(includeTestsCheckBox) })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { add(includeCustomCheckBox) })
            add(JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                add(JBLabel("Package (empty for default): "))
                add(packageField)
            })
        }
    }

    private fun updatePreview() {
        val planned = plannedSourceSets
        val packageName = packageName.takeIf { PACKAGE_REGEX.matches(it) } ?: ""
        var existing = 0
        previewArea.text = planned.joinToString("\n") { sourceSet ->
            val exists = moduleDir.findFileByRelativePath(sourceSet.relativeFilePath(packageName)) != null
            if (exists) existing++
            "  ".repeat(sourceSet.depth) + sourceSet.name + if (exists) "   (exists, skipped)" else ""
        }
        previewArea.caretPosition = 0
        summaryLabel.text = "${planned.size - existing} class(es) to create" +
            if (existing > 0) ", $existing already exist" else ""
    }

    override fun doValidate(): ValidationInfo? {
        if (!PACKAGE_REGEX.matches(packageName)) {
            return ValidationInfo("Not a valid package name", packageField)
        }
        if (plannedSourceSets.isEmpty()) {
            return ValidationInfo("Select at least one target")
        }
        return null
    }

    private companion object {
        val PACKAGE_REGEX = Regex("""^$|^[A-Za-z_]\w*(\.[A-Za-z_]\w*)*$""")
    }
}
