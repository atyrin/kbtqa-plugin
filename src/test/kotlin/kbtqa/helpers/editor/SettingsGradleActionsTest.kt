package kbtqa.helpers.editor

/**
 * Tests for the QA Helper actions that rewrite `settings.gradle.kts` and `build.gradle.kts` through Kotlin PSI:
 * [ConfigureRepositoriesAction], [ConfigureBuildScanAction], [ConfigureBuildCacheAction]
 * and [OverwriteVersionCatalogAction].
 */
class SettingsGradleActionsTest : GradleScriptActionTestCase() {

    private val kotlinRepositories = listOf(
        "mavenCentral()",
        "maven(\"https://redirector.kotlinlang.org/maven/dev\")",
        "maven(\"https://redirector.kotlinlang.org/maven/bootstrap\")",
        "maven(\"https://redirector.kotlinlang.org/maven/experimental\")",
        "google()"
    )

    // region Configure Repositories

    fun testRepositoriesAreCreatedInEmptySettings() {
        val result = runAction(ConfigureRepositoriesAction(), "settings.gradle.kts", "")

        assertEquals(listOf("pluginManagement", "dependencyResolutionManagement"), topLevelCalls(result))
        assertEquals(listOf("gradlePluginPortal()") + kotlinRepositories, statements(result, "pluginManagement", "repositories"))
        assertEquals(kotlinRepositories, statements(result, "dependencyResolutionManagement", "repositories"))
    }

    fun testRepositoriesBlocksAreAddedAroundExistingSettings() {
        val result = runAction(
            ConfigureRepositoriesAction(), "settings.gradle.kts", """
            rootProject.name = "sample"
            include(":app")
            """.trimIndent()
        )

        assertEquals(listOf("pluginManagement", "dependencyResolutionManagement", "include"), topLevelCalls(result))
        assertTrue(result.contains("rootProject.name = \"sample\""))
    }

    fun testMissingRepositoriesAreMergedIntoExistingBlocks() {
        val result = runAction(
            ConfigureRepositoriesAction(), "settings.gradle.kts", """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    maven("https://redirector.kotlinlang.org/maven/dev/")
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            }
            """.trimIndent()
        )

        val pluginRepositories = statements(result, "pluginManagement", "repositories")
        assertEquals(1, pluginRepositories.count { it == "gradlePluginPortal()" })
        assertEquals(
            "Trailing slash must not cause a duplicate",
            1, pluginRepositories.count { it.contains("maven/dev") }
        )
        assertEquals(6, pluginRepositories.size)

        assertEquals(
            setOf("repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)", "repositories"),
            statements(result, "dependencyResolutionManagement").map { it.substringBefore(" {") }.toSet()
        )
        assertEquals(kotlinRepositories, statements(result, "dependencyResolutionManagement", "repositories"))
    }

    fun testConfigureRepositoriesInSettingsIsIdempotent() {
        runAction(ConfigureRepositoriesAction(), "settings.gradle.kts", "rootProject.name = \"sample\"\n")
        assertIdempotent(ConfigureRepositoriesAction())
    }

    fun testRepositoriesBlockIsAddedToBuildScript() {
        val result = runAction(
            ConfigureRepositoriesAction(), "build.gradle.kts", """
            plugins {
                kotlin("jvm") version "2.2.20"
            }
            """.trimIndent()
        )

        assertEquals(listOf("plugins", "repositories"), topLevelCalls(result))
        assertEquals(kotlinRepositories, statements(result, "repositories"))
    }

    fun testRepositoriesAreMergedIntoBuildScriptBlock() {
        val result = runAction(
            ConfigureRepositoriesAction(), "build.gradle.kts", """
            repositories {
                mavenLocal()
                google()
                maven(url = "https://example.org/maven")
            }
            """.trimIndent()
        )

        val repositories = statements(result, "repositories")
        assertEquals(1, repositories.count { it == "google()" })
        assertTrue(repositories.containsAll(listOf("mavenLocal()", "maven(url = \"https://example.org/maven\")")))
        assertTrue(repositories.containsAll(kotlinRepositories))
        assertEquals(7, repositories.size)
    }

    fun testConfigureRepositoriesInBuildScriptIsIdempotent() {
        runAction(ConfigureRepositoriesAction(), "build.gradle.kts", "")
        assertIdempotent(ConfigureRepositoriesAction())
    }

    // endregion

    // region Configure Build Scan

    fun testBuildScanCreatesPluginsBlockInEmptySettings() {
        val result = runAction(ConfigureBuildScanAction(), "settings.gradle.kts", "")

        assertEquals(listOf("plugins", "develocity"), topLevelCalls(result))
        assertEquals(listOf("id(\"com.gradle.develocity\") version (\"4.3.3\")"), statements(result, "plugins"))
        assertTrue(statements(result, "develocity", "buildScan").contains("termsOfUseAgree.set(\"yes\")"))
    }

    fun testBuildScanPluginsBlockGoesAfterPluginManagement() {
        val result = runAction(
            ConfigureBuildScanAction(), "settings.gradle.kts", """
            pluginManagement {
                repositories { gradlePluginPortal() }
            }
            """.trimIndent()
        )

        assertEquals(listOf("pluginManagement", "plugins", "develocity"), topLevelCalls(result))
    }

    fun testBuildScanPluginIsAddedToExistingPluginsBlock() {
        val result = runAction(
            ConfigureBuildScanAction(), "settings.gradle.kts", """
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
            }
            """.trimIndent()
        )

        assertEquals(listOf("plugins", "develocity"), topLevelCalls(result))
        assertEquals(
            setOf(
                "id(\"org.gradle.toolchains.foojay-resolver-convention\") version \"1.0.0\"",
                "id(\"com.gradle.develocity\") version (\"4.3.3\")"
            ),
            statements(result, "plugins").toSet()
        )
    }

    fun testExistingDevelocityConfigurationIsKept() {
        val script = """
            plugins {
                id("com.gradle.develocity") version "3.19"
            }
            develocity {
                server.set("https://example.org")
            }
        """.trimIndent()

        val result = runAction(ConfigureBuildScanAction(), "settings.gradle.kts", script)

        assertEquals(script, result)
    }

    fun testConfigureBuildScanIsIdempotent() {
        runAction(ConfigureBuildScanAction(), "settings.gradle.kts", "")
        assertIdempotent(ConfigureBuildScanAction())
    }

    // endregion

    // region Configure Build Cache

    fun testBuildCacheBlockIsAppended() {
        val result = runAction(ConfigureBuildCacheAction(), "settings.gradle.kts", "rootProject.name = \"sample\"\n")

        assertEquals(listOf("buildCache"), topLevelCalls(result))
        assertTrue(statements(result, "buildCache", "local").contains("directory = File(rootDir, \"build-cache\")"))
    }

    fun testExistingBuildCacheBlockIsKept() {
        val script = "buildCache {\n    remote<HttpBuildCache> { }\n}"
        assertEquals(script, runAction(ConfigureBuildCacheAction(), "settings.gradle.kts", script))
    }

    // endregion

    // region Overwrite Version Catalog

    fun testVersionCatalogBlockIsCreated() {
        val result = runAction(OverwriteVersionCatalogAction(), "settings.gradle.kts", "include(\":app\")\n")

        assertEquals(listOf("include", "dependencyResolutionManagement"), topLevelCalls(result))
        assertEquals(
            listOf("version(\"kotlin\", \"new-version\")"),
            statements(result, "dependencyResolutionManagement", "versionCatalogs", "create")
        )
    }

    fun testVersionCatalogsAreAddedToExistingDependencyResolutionManagement() {
        val result = runAction(
            OverwriteVersionCatalogAction(), "settings.gradle.kts", """
            dependencyResolutionManagement {
                repositories { mavenCentral() }
            }
            """.trimIndent()
        )

        assertEquals(listOf("dependencyResolutionManagement"), topLevelCalls(result))
        assertEquals(
            setOf("repositories", "versionCatalogs"),
            statements(result, "dependencyResolutionManagement").map { it.substringBefore(" {") }.toSet()
        )
        assertEquals(
            listOf("version(\"kotlin\", \"new-version\")"),
            statements(result, "dependencyResolutionManagement", "versionCatalogs", "create")
        )
    }

    fun testLibsCatalogIsAddedNextToOtherCatalogs() {
        val result = runAction(
            OverwriteVersionCatalogAction(), "settings.gradle.kts", """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("tools") { from(files("gradle/tools.versions.toml")) }
                }
            }
            """.trimIndent()
        )

        val catalogs = statements(result, "dependencyResolutionManagement", "versionCatalogs")
        assertEquals(setOf("create(\"tools\")", "create(\"libs\")"), catalogs.map { it.substringBefore(" {") }.toSet())
    }

    fun testExistingLibsCatalogIsKept() {
        val script = """
            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") { version("kotlin", "2.0.0") }
                }
            }
        """.trimIndent()

        assertEquals(script, runAction(OverwriteVersionCatalogAction(), "settings.gradle.kts", script))
    }

    // endregion
}
