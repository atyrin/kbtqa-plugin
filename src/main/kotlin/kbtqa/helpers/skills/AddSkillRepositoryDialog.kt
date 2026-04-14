package kbtqa.helpers.skills

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Dialog for adding a new skill repository configuration.
 */
class AddSkillRepositoryDialog : DialogWrapper(true) {

    private val nameField = JTextField(30)
    private val urlField = JTextField(30)
    private val skillsPathField = JTextField(30).apply { text = "skills" }

    init {
        title = "Add Skill Repository"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(5)
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(4)
        }

        fun addRow(row: Int, label: String, field: JTextField) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            panel.add(JLabel(label), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            panel.add(field, gbc)
        }

        addRow(0, "Display Name:", nameField)
        addRow(1, "Git URL:", urlField)
        addRow(2, "Skills Path:", skillsPathField)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (nameField.text.isBlank()) return ValidationInfo("Display Name is required", nameField)
        if (urlField.text.isBlank()) return ValidationInfo("Git URL is required", urlField)
        if (skillsPathField.text.isBlank()) return ValidationInfo("Skills Path is required", skillsPathField)

        val normalizedPath = java.nio.file.Paths.get(skillsPathField.text.trim()).normalize().toString()
        if (normalizedPath.startsWith("..") || java.nio.file.Paths.get(normalizedPath).isAbsolute) {
            return ValidationInfo("Skills Path must be a relative path inside the repository (e.g., 'skills')", skillsPathField)
        }

        return null
    }

    fun getRepository(): SkillRepository = SkillRepository(
        name = nameField.text.trim(),
        url = urlField.text.trim(),
        skillsPath = skillsPathField.text.trim()
    )
}
