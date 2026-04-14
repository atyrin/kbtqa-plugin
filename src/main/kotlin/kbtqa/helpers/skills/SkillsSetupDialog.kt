package kbtqa.helpers.skills

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CollectionListModel
import com.intellij.ui.JBSplitter
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import javax.swing.*

/**
 * Main dialog for the Skills Setup Wizard.
 * Left panel: list of configured skill repositories with +/- buttons.
 * Center panel: checkboxes for available skills from the selected repository.
 * Bottom panel: target directory field and Install button.
 */
class SkillsSetupDialog(
    private val project: Project?
) : DialogWrapper(project) {

    private val logger = thisLogger()
    private val settings = SkillsSettingsState.getInstance()
    private val repositoryService = ApplicationManager.getApplication().getService(SkillsRepositoryService::class.java)

    private lateinit var repoList: JBList<SkillRepository>
    private lateinit var repoListModel: CollectionListModel<SkillRepository>
    private lateinit var skillsPanel: JPanel
    private lateinit var targetDirField: JComboBox<String>

    private val skillCheckBoxes = mutableListOf<JCheckBox>()
    private var currentRepo: SkillRepository? = null
    private var loadGeneration = 0L
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        title = "Skills Setup Wizard"
        setOKButtonText("Install")
        setCancelButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        val splitter = JBSplitter(false, 0.25f)
        splitter.firstComponent = createRepoPanel()
        splitter.secondComponent = createSkillsPanel()

        mainPanel.add(splitter, BorderLayout.CENTER)
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH)
        mainPanel.preferredSize = Dimension(700, 450)

        // Auto-select first repo
        SwingUtilities.invokeLater {
            if (repoListModel.size > 0) {
                repoList.selectedIndex = 0
            }
        }

        return mainPanel
    }

    private fun createRepoPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(0, 0, 0, 5)

        panel.add(TitledSeparator("Repositories"), BorderLayout.NORTH)

        repoListModel = CollectionListModel(settings.repositories.toList())
        repoList = JBList(repoListModel)
        repoList.selectionMode = ListSelectionModel.SINGLE_SELECTION

        repoList.addListSelectionListener { e ->
            if (!e.valueIsAdjusting) {
                repoList.selectedValue?.let { onRepoSelected(it) }
            }
        }

        val scrollPane = JBScrollPane(repoList)
        scrollPane.border = JBUI.Borders.empty()
        panel.add(scrollPane, BorderLayout.CENTER)

        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 0))
        val addButton = JButton("+").apply {
            toolTipText = "Add repository"
            addActionListener { onAddRepo() }
        }
        val removeButton = JButton("−").apply {
            toolTipText = "Remove repository"
            addActionListener { onRemoveRepo() }
        }
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createSkillsPanel(): JComponent {
        skillsPanel = JPanel(BorderLayout())
        skillsPanel.border = JBUI.Borders.empty(0, 5, 0, 0)

        val header = TitledSeparator("Available Skills")
        skillsPanel.add(header, BorderLayout.NORTH)
        skillsPanel.add(JBLabel("Select a repository to browse skills."), BorderLayout.CENTER)

        return skillsPanel
    }

    private fun createBottomPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyTop(10)

        val label = JLabel("Target directory: ")
        targetDirField = JComboBox(arrayOf(".junie/skills", ".claude/skills", ".codex/skills"))
        targetDirField.isEditable = true
        targetDirField.selectedItem = ".junie/skills"

        val dirPanel = JPanel(BorderLayout())
        dirPanel.add(label, BorderLayout.WEST)
        dirPanel.add(targetDirField, BorderLayout.CENTER)

        panel.add(dirPanel, BorderLayout.CENTER)
        return panel
    }

    private fun onRepoSelected(repo: SkillRepository) {
        currentRepo = repo
        skillCheckBoxes.clear()
        val generation = ++loadGeneration

        // Show loading
        skillsPanel.removeAll()
        skillsPanel.add(TitledSeparator("Available Skills"), BorderLayout.NORTH)
        skillsPanel.add(JBLabel("Loading skills from ${repo.url}..."), BorderLayout.CENTER)
        skillsPanel.revalidate()
        skillsPanel.repaint()

        coroutineScope.launch {
            try {
                val skills = withContext(Dispatchers.IO) {
                    repositoryService.fetchSkills(repo)
                }
                if (generation == loadGeneration) {
                    showSkills(skills)
                }
            } catch (e: Exception) {
                if (generation == loadGeneration) {
                    showSkillsError(e.message ?: "Unknown error")
                }
            }
        }
    }

    private fun showSkills(skills: List<SkillInfo>) {
        skillsPanel.removeAll()
        skillsPanel.add(TitledSeparator("Available Skills (${skills.size})"), BorderLayout.NORTH)

        if (skills.isEmpty()) {
            skillsPanel.add(JBLabel("No skills found in this repository."), BorderLayout.CENTER)
        } else {
            val checkBoxPanel = JPanel()
            checkBoxPanel.layout = BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS)
            checkBoxPanel.border = JBUI.Borders.empty(5)

            for (skill in skills) {
                val cb = JCheckBox(skill.name)
                cb.actionCommand = skill.name
                skillCheckBoxes.add(cb)
                checkBoxPanel.add(cb)
            }

            skillsPanel.add(JBScrollPane(checkBoxPanel), BorderLayout.CENTER)
        }

        skillsPanel.revalidate()
        skillsPanel.repaint()
    }

    private fun showSkillsError(message: String) {
        skillsPanel.removeAll()
        skillsPanel.add(TitledSeparator("Available Skills"), BorderLayout.NORTH)
        skillsPanel.add(JBLabel("<html><font color='red'>Error: $message</font></html>"), BorderLayout.CENTER)
        skillsPanel.revalidate()
        skillsPanel.repaint()
    }

    private fun onAddRepo() {
        val dialog = AddSkillRepositoryDialog()
        if (dialog.showAndGet()) {
            val repo = dialog.getRepository()
            settings.repositories.add(repo)
            repoListModel.add(repo)
        }
    }

    private fun onRemoveRepo() {
        val index = repoList.selectedIndex
        if (index >= 0) {
            val removedRepo = repoListModel.getElementAt(index)
            settings.repositories.removeAt(index)
            repoListModel.remove(index)

            // Invalidate any in-flight load for the removed repo
            if (currentRepo == removedRepo || repoListModel.size == 0) {
                ++loadGeneration
                currentRepo = null
                skillCheckBoxes.clear()
                skillsPanel.removeAll()
                skillsPanel.add(TitledSeparator("Available Skills"), BorderLayout.NORTH)
                skillsPanel.add(JBLabel("Select a repository to browse skills."), BorderLayout.CENTER)
                skillsPanel.revalidate()
                skillsPanel.repaint()
            }
        }
    }

    override fun doOKAction() {
        val repo = currentRepo
        if (repo == null) {
            Messages.showWarningDialog(project, "Please select a repository first.", "No Repository Selected")
            return
        }

        val selectedSkills = skillCheckBoxes.filter { it.isSelected }.map { it.actionCommand }
        if (selectedSkills.isEmpty()) {
            Messages.showWarningDialog(project, "Please select at least one skill to install.", "No Skills Selected")
            return
        }

        val targetPath = (targetDirField.selectedItem as? String)?.trim() ?: ""
        if (targetPath.isBlank()) {
            Messages.showWarningDialog(project, "Please specify a target directory.", "No Target Directory")
            return
        }

        val projectBasePath = project?.basePath
        if (projectBasePath == null) {
            Messages.showErrorDialog(project, "No project is open.", "Error")
            return
        }

        val targetDir = File(projectBasePath, targetPath)
        val installer = SkillsInstallerService()

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Installing Skills", false) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    installer.installSkills(repo, selectedSkills, targetDir, indicator)
                    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetDir)?.refresh(true, true)

                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "Successfully installed ${selectedSkills.size} skill(s) to $targetPath",
                            "Skills Installed"
                        )
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to install skills", e)
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Failed to install skills: ${e.message}", "Installation Error")
                    }
                }
            }
        })

        super.doOKAction()
    }

    override fun dispose() {
        coroutineScope.cancel()
        super.dispose()
    }
}
