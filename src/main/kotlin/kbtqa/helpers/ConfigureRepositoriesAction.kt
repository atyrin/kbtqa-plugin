package kbtqa.helpers

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project

/**
 * Action that adds a context menu option for Gradle files
 * to insert repository configurations.
 */
class ConfigureRepositoriesAction : AnAction("Configure Repositories", "Insert repository configurations", null), DumbAware {

    companion object {
        private const val COMMON_REPOSITORIES = """mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
        google()"""

        private const val PLUGIN_REPOSITORIES = """gradlePluginPortal()
        mavenCentral()
        maven("https://packages.jetbrains.team/maven/p/kt/dev")
        google()"""

        private val SETTINGS_GRADLE_REPOSITORIES = """
pluginManagement {
    repositories {
        $PLUGIN_REPOSITORIES
    }
}

dependencyResolutionManagement {
    repositories {
        $COMMON_REPOSITORIES
    }
}

""".trimIndent()

        private val BUILD_GRADLE_REPOSITORIES = """
repositories {
    $COMMON_REPOSITORIES
}

""".trimIndent()
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        // Always show the action, but enable it only for settings.gradle.kts and build.gradle.kts files
        e.presentation.isVisible = true
        e.presentation.isEnabled = file != null && 
                (file.name == "settings.gradle.kts" || file.name == "build.gradle.kts")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        
        // Determine which repositories block to insert based on the file name
        val repositoriesBlock = when (file.name) {
            "settings.gradle.kts" -> SETTINGS_GRADLE_REPOSITORIES
            "build.gradle.kts" -> BUILD_GRADLE_REPOSITORIES
            else -> return // This shouldn't happen due to the update method, but just in case
        }
        
        insertRepositories(project, editor, repositoriesBlock)
    }

    private fun insertRepositories(project: Project, editor: Editor, repositoriesBlock: String) {
        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document)
        
        WriteCommandAction.runWriteCommandAction(project) {
            if (file?.name == "settings.gradle.kts") {
                val text = document.text
                
                // Check for existing pluginManagement block
                val pluginManagementExists = text.contains("pluginManagement\\s*\\{".toRegex())
                // Check for existing dependencyResolutionManagement block
                val dependencyManagementExists = text.contains("dependencyResolutionManagement\\s*\\{".toRegex())
                
                if (pluginManagementExists || dependencyManagementExists) {
                    // If either block exists, we need to update them individually
                    
                    // Handle pluginManagement block
                    if (pluginManagementExists) {
                        val pluginManagementRegex = "pluginManagement\\s*\\{[^}]*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
                        val pluginMatch = pluginManagementRegex.find(text)
                        
                        if (pluginMatch != null) {
                            // Check if repositories block exists within pluginManagement
                            val pluginBlock = pluginMatch.value
                            val hasRepositories = pluginBlock.contains("repositories\\s*\\{".toRegex())
                            
                            if (hasRepositories) {
                                // Replace existing repositories block
                                val updatedPluginBlock = pluginBlock.replace(
                                    "repositories\\s*\\{[^}]*\\}".toRegex(RegexOption.DOT_MATCHES_ALL),
                                    """repositories {
                                        |        $PLUGIN_REPOSITORIES
                                        |    }""".trimMargin()
                                )
                                document.replaceString(pluginMatch.range.first, pluginMatch.range.last + 1, updatedPluginBlock)
                            } else {
                                // Add repositories block inside pluginManagement
                                val insertPos = pluginMatch.range.first + "pluginManagement {".length
                                document.insertString(insertPos, """
                                    |
                                    |    repositories {
                                    |        $PLUGIN_REPOSITORIES
                                    |    }""".trimMargin()
                                )
                            }
                        }
                    } else {
                        // Add pluginManagement block at the beginning
                        document.insertString(0, """pluginManagement {
                            |
                            |    repositories {
                            |        $PLUGIN_REPOSITORIES
                            |    }
                            |}
                            |
                            |""".trimMargin()
                        )
                    }
                    
                    // Handle dependencyResolutionManagement block
                    // We need to get the updated text after potential pluginManagement changes
                    val updatedText = document.text
                    if (dependencyManagementExists) {
                        val dependencyManagementRegex = "dependencyResolutionManagement\\s*\\{[^}]*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
                        val dependencyMatch = dependencyManagementRegex.find(updatedText)
                        
                        if (dependencyMatch != null) {
                            // Check if repositories block exists within dependencyResolutionManagement
                            val dependencyBlock = dependencyMatch.value
                            val hasRepositories = dependencyBlock.contains("repositories\\s*\\{".toRegex())
                            
                            if (hasRepositories) {
                                // Replace existing repositories block
                                val updatedDependencyBlock = dependencyBlock.replace(
                                    "repositories\\s*\\{[^}]*\\}".toRegex(RegexOption.DOT_MATCHES_ALL),
                                    """repositories {
                                        |        $COMMON_REPOSITORIES
                                        |    }""".trimMargin()
                                )
                                document.replaceString(dependencyMatch.range.first, dependencyMatch.range.last + 1, updatedDependencyBlock)
                            } else {
                                // Add repositories block inside dependencyResolutionManagement
                                val insertPos = dependencyMatch.range.first + "dependencyResolutionManagement {".length
                                document.insertString(insertPos, """
                                    |
                                    |    repositories {
                                    |        $COMMON_REPOSITORIES
                                    |    }""".trimMargin()
                                )
                            }
                        }
                    } else {
                        // Add dependencyResolutionManagement block after pluginManagement or at the beginning
                        val pluginManagementEnd = updatedText.indexOf("pluginManagement {")
                        val insertPos = if (pluginManagementEnd >= 0) {
                            val endBrace = updatedText.indexOf("}", pluginManagementEnd)
                            if (endBrace >= 0) endBrace + 1 else 0
                        } else {
                            0
                        }
                        
                        document.insertString(insertPos, """
                            |
                            |dependencyResolutionManagement {
                            |    repositories {
                            |        $COMMON_REPOSITORIES
                            |    }
                            |}
                            |
                            |""".trimMargin()
                        )
                    }
                } else {
                    // If neither block exists, insert both blocks at the beginning
                    document.insertString(0, repositoriesBlock)
                }
            } else {
                // For build.gradle.kts
                val text = document.text
                val repositoriesExists = text.contains("repositories\\s*\\{".toRegex())
                
                if (repositoriesExists) {
                    // Replace existing repositories block
                    val repositoriesRegex = "repositories\\s*\\{[^}]*\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
                    val match = repositoriesRegex.find(text)
                    
                    if (match != null) {
                        document.replaceString(match.range.first, match.range.last + 1, """repositories {
                            |    $COMMON_REPOSITORIES
                            |}""".trimMargin()
                        )
                    }
                } else {
                    // Insert at cursor position
                    document.insertString(editor.caretModel.offset, repositoriesBlock)
                }
            }
        }
    }
}