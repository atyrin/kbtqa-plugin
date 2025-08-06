package kbtqa.gradle

import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.JBSplitter
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Main panel for the Gradle Debug Tool Window.
 * Displays a tree of Gradle projects and subprojects with available commands.
 */
class GradleDebugPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tree: JTree
    private val treeModel: DefaultTreeModel
    private val gradleCommandService = project.service<GradleCommandService>()
    private val outputConsole: GradleOutputConsole

    init {
        // Create the tree model with root node
        val rootNode = DefaultMutableTreeNode("Gradle Projects")
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)

        // Create output console
        outputConsole = GradleOutputConsole(project)

        // Create splitter with tree on left and console on right
        val splitter = JBSplitter(false, 0.3f)
        val treeScrollPane = JBScrollPane(tree)
        splitter.firstComponent = treeScrollPane
        splitter.secondComponent = outputConsole.component

        add(splitter, BorderLayout.CENTER)

        // Add mouse listener for double-click actions
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    handleTreeDoubleClick()
                }
            }
        })

        // Initialize the tree with project data
        initializeTree()
    }

    private fun initializeTree() {
        val rootNode = treeModel.root as DefaultMutableTreeNode
        
        // Add root project
        val rootProjectNode = DefaultMutableTreeNode("Root Project")
        rootNode.add(rootProjectNode)
        
        // Add commands for root project
        addCommandNodes(rootProjectNode)
        
        // Discover and add subprojects
        discoverSubprojects(rootNode)
        
        // Expand the root node
        tree.expandRow(0)
        
        // Refresh the tree
        treeModel.reload()
    }

    private fun discoverSubprojects(rootNode: DefaultMutableTreeNode) {
        // Discover actual subprojects by executing gradle projects command and parsing output
        val actualSubprojects = gradleCommandService.discoverActualSubprojects()
        
        if (actualSubprojects.isNotEmpty()) {
            val subprojectsNode = DefaultMutableTreeNode("Subprojects")
            rootNode.add(subprojectsNode)
            
            // Add discovered subprojects
            actualSubprojects.forEach { subprojectName ->
                val subprojectNode = DefaultMutableTreeNode("Project: $subprojectName")
                subprojectsNode.add(subprojectNode)
                addCommandNodes(subprojectNode)
            }
        } else {
            // Fallback: add a placeholder indicating no subprojects found or discovery failed
            val subprojectsNode = DefaultMutableTreeNode("Subprojects (none found or discovery failed)")
            rootNode.add(subprojectsNode)
        }
    }
    
    private fun addCommandNodes(projectNode: DefaultMutableTreeNode) {
        // Add available Gradle commands
        projectNode.add(DefaultMutableTreeNode("List Configurations"))
        projectNode.add(DefaultMutableTreeNode("List Dependencies"))
        projectNode.add(DefaultMutableTreeNode("Dependency Insights"))
        projectNode.add(DefaultMutableTreeNode("Outgoing Variants"))
    }

    private fun handleTreeDoubleClick() {
        val selectionPath = tree.selectionPath ?: return
        val selectedNode = selectionPath.lastPathComponent as? DefaultMutableTreeNode ?: return
        val nodeText = selectedNode.userObject.toString()

        // Determine the project path based on the parent node
        val projectPath = getProjectPath(selectedNode)

        when (nodeText) {
            "List Configurations" -> {
                val processHandler = gradleCommandService.listConfigurations(projectPath)
                outputConsole.executeAndDisplay(processHandler, "gradle $projectPath:configurations")
            }
            "List Dependencies" -> {
                val processHandler = gradleCommandService.listDependencies(projectPath)
                outputConsole.executeAndDisplay(processHandler, "gradle $projectPath:dependencies")
            }
            "Dependency Insights" -> {
                // For now, show insights for a common dependency - could be enhanced with user input
                val processHandler = gradleCommandService.dependencyInsight(projectPath, "kotlin-stdlib")
                outputConsole.executeAndDisplay(processHandler, "gradle $projectPath:dependencyInsight --dependency kotlin-stdlib")
            }
            "Outgoing Variants" -> {
                val processHandler = gradleCommandService.outgoingVariants(projectPath)
                outputConsole.executeAndDisplay(processHandler, "gradle $projectPath:outgoingVariants")
            }
        }
    }

    private fun getProjectPath(node: DefaultMutableTreeNode): String {
        // Walk up the tree to find the project node
        var currentNode = node.parent as? DefaultMutableTreeNode
        while (currentNode != null) {
            val nodeText = currentNode.userObject.toString()
            if (nodeText == "Root Project") {
                return "" // Root project has empty path
            }
            if (nodeText.startsWith("Project: ")) {
                return nodeText.removePrefix("Project: ")
            }
            currentNode = currentNode.parent as? DefaultMutableTreeNode
        }
        return "" // Default to root project
    }
}