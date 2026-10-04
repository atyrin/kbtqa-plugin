package kbtqa.helpers.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [KmpBuildScriptParser] and [KmpSourceSetPlanner].
 */
class KmpSourceSetsTest {

    private fun targetNames(script: String) = KmpBuildScriptParser.parse(script).targets.map { it.name }

    private fun planNames(script: String, includeTests: Boolean = true): List<String> {
        val info = KmpBuildScriptParser.parse(script)
        return KmpSourceSetPlanner.plan(info.targets, info.customSourceSets, includeTests).map { it.name }
    }

    @Test
    fun `no kotlin block`() {
        val info = KmpBuildScriptParser.parse("plugins { kotlin(\"multiplatform\") }\n")
        assertFalse(info.kotlinBlockFound)
        assertTrue(info.targets.isEmpty())
    }

    @Test
    fun `typical targets are detected in declaration order`() {
        val script = """
            plugins {
                kotlin("multiplatform") version "2.2.20"
            }
            kotlin {
                jvmToolchain(17)
                jvm()
                js { browser() }
                @OptIn(ExperimentalWasmDsl::class)
                wasmJs { browser() }
                iosArm64()
                iosSimulatorArm64()
                linuxX64 {
                    binaries { executable() }
                }
            }
        """.trimIndent()
        assertEquals(
            listOf("jvm", "js", "wasmJs", "iosArm64", "iosSimulatorArm64", "linuxX64"),
            targetNames(script)
        )
    }

    @Test
    fun `targets inside listOf are detected`() {
        val script = """
            kotlin {
                listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
                    it.binaries.framework { baseName = "shared" }
                }
            }
        """.trimIndent()
        assertEquals(listOf("iosX64", "iosArm64", "iosSimulatorArm64"), targetNames(script))
    }

    @Test
    fun `commented out targets are ignored`() {
        val script = """
            kotlin {
                jvm()
                // iosArm64()
                /* linuxX64()
                   /* nested */ mingwX64() */
                js() // wasmJs()
            }
        """.trimIndent()
        assertEquals(listOf("jvm", "js"), targetNames(script))
    }

    @Test
    fun `custom target names`() {
        val script = """
            kotlin {
                jvm("desktop")
                macosArm64(name = "native") { }
                js(IR) { nodejs() }
            }
        """.trimIndent()
        assertEquals(listOf("desktop", "native", "js"), targetNames(script))
    }

    @Test
    fun `nested calls and strings do not produce targets`() {
        val script = """
            kotlin {
                jvm {
                    compilations.all { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
                }
                sourceSets {
                    jvmMain.dependencies { implementation("x:js(1)") }
                }
                val text = "iosArm64() { }"
            }
        """.trimIndent()
        assertEquals(listOf("jvm"), targetNames(script))
    }

    @Test
    fun `android targets`() {
        val agpLibrary = """
            kotlin {
                android {
                    namespace = "org.example"
                    withHostTest { }
                }
            }
            android { compileSdk = 35 }
        """.trimIndent()
        val target = KmpBuildScriptParser.parse(agpLibrary).targets.single()
        assertEquals(KmpTargetPreset.ANDROID_LIBRARY, target.preset)
        assertEquals(listOf("androidHostTest"), target.testSourceSets)

        val legacy = KmpBuildScriptParser.parse("kotlin { androidTarget() }").targets.single()
        assertEquals(KmpTargetPreset.ANDROID_TARGET, legacy.preset)
        assertEquals(listOf("androidUnitTest", "androidInstrumentedTest"), legacy.testSourceSets)
    }

    @Test
    fun `top-level android block outside kotlin is not a target`() {
        assertEquals(listOf("jvm"), targetNames("android { }\nkotlin { jvm() }"))
    }

    @Test
    fun `repeated declaration is merged`() {
        val info = KmpBuildScriptParser.parse("kotlin {\n jvm()\n jvm { withJava() }\n }")
        assertEquals(1, info.targets.size)
        assertTrue(info.targets.single().configuration.contains("withJava"))
    }

    @Test
    fun `custom source sets are detected`() {
        val script = """
            kotlin {
                jvm()
                sourceSets {
                    val commonMain by getting
                    val jvmAndJsMain by creating { dependsOn(commonMain) }
                    create("integrationTest")
                }
            }
        """.trimIndent()
        assertEquals(listOf("jvmAndJsMain", "integrationTest"), KmpBuildScriptParser.parse(script).customSourceSets)
    }

    @Test
    fun `plan follows default hierarchy template`() {
        val script = """
            kotlin {
                jvm()
                js()
                iosArm64()
                iosSimulatorArm64()
                macosArm64()
                linuxX64()
            }
        """.trimIndent()
        assertEquals(
            listOf(
                "commonMain", "jvmMain",
                "webMain", "jsMain",
                "nativeMain", "appleMain", "iosMain", "iosArm64Main", "iosSimulatorArm64Main",
                "macosMain", "macosArm64Main",
                "linuxMain", "linuxX64Main"
            ),
            planNames(script, includeTests = false)
        )
    }

    @Test
    fun `plan includes tests and depths`() {
        val plan = KmpSourceSetPlanner.plan(listOf(KmpTarget(KmpTargetPreset.IOS_ARM64)))
        assertEquals(
            listOf(
                "commonMain" to 0, "nativeMain" to 1, "appleMain" to 2, "iosMain" to 3, "iosArm64Main" to 4,
                "commonTest" to 0, "nativeTest" to 1, "appleTest" to 2, "iosTest" to 3, "iosArm64Test" to 4
            ),
            plan.map { it.name to it.depth }
        )
    }

    @Test
    fun `plan with no targets is empty`() {
        assertTrue(KmpSourceSetPlanner.plan(emptyList()).isEmpty())
    }

    @Test
    fun `duplicate target names are kept once`() {
        val plan = KmpSourceSetPlanner.plan(
            listOf(KmpTarget(KmpTargetPreset.ANDROID_TARGET), KmpTarget(KmpTargetPreset.ANDROID_LIBRARY)),
            includeTests = false
        )
        assertEquals(listOf("commonMain", "androidMain"), plan.map { it.name })
    }

    @Test
    fun `generated file path and content`() {
        val sourceSet = PlannedSourceSet("iosArm64Main", depth = 4, isTest = false)
        assertEquals("src/iosArm64Main/kotlin/IosArm64Main.kt", sourceSet.relativeFilePath(""))
        assertEquals("src/iosArm64Main/kotlin/org/example/IosArm64Main.kt", sourceSet.relativeFilePath("org.example"))
        assertEquals("class IosArm64Main\n", sourceSet.fileContent(""))
        assertEquals("package org.example\n\nclass IosArm64Main\n", sourceSet.fileContent("org.example"))
    }
}
