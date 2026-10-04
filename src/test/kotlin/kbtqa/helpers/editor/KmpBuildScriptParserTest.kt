package kbtqa.helpers.editor

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtFile

/**
 * Tests for [KmpBuildScriptParser] on the Kotlin PSI of a `build.gradle.kts` script.
 */
class KmpBuildScriptParserTest : BasePlatformTestCase() {

    private fun parse(script: String): KmpBuildScriptInfo =
        KmpBuildScriptParser.parse(myFixture.configureByText("build.gradle.kts", script) as KtFile)

    private fun targetNames(script: String) = parse(script).targets.map { it.name }

    fun testNoKotlinBlock() {
        val info = parse("plugins { kotlin(\"multiplatform\") }\n")
        assertFalse(info.kotlinBlockFound)
        assertTrue(info.targets.isEmpty())
    }

    fun testTypicalTargetsAreDetectedInDeclarationOrder() {
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

    fun testTargetsInsideListOfAreDetected() {
        val script = """
            kotlin {
                listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach {
                    it.binaries.framework { baseName = "shared" }
                }
            }
        """.trimIndent()
        assertEquals(listOf("iosX64", "iosArm64", "iosSimulatorArm64"), targetNames(script))
    }

    fun testCommentedOutTargetsAreIgnored() {
        val script = """
            kotlin {
                jvm()
                // iosArm64()
                /* linuxX64()
                   mingwX64() */
                js() // wasmJs()
            }
        """.trimIndent()
        assertEquals(listOf("jvm", "js"), targetNames(script))
    }

    fun testCustomTargetNames() {
        val script = """
            kotlin {
                jvm("desktop")
                macosArm64(name = "native") { }
                js(IR) { nodejs() }
                linuxX64("linux${'$'}suffix")
            }
        """.trimIndent()
        assertEquals(listOf("desktop", "native", "js", "linuxX64"), targetNames(script))
    }

    fun testNestedCallsAndStringsDoNotProduceTargets() {
        val script = """
            kotlin {
                jvm {
                    compilations.all { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
                }
                sourceSets {
                    jvmMain.dependencies { implementation("x:js(1)") }
                }
                val text = "iosArm64() { }"
                targets.forEach { it.js() }
            }
        """.trimIndent()
        assertEquals(listOf("jvm"), targetNames(script))
    }

    fun testCallOnReceiverIsNotATarget() {
        assertEquals(listOf("js"), targetNames("kotlin {\n project.jvm()\n this.js()\n}"))
    }

    fun testAndroidTargets() {
        val agpLibrary = """
            kotlin {
                android {
                    namespace = "org.example"
                    withHostTest { }
                    // withDeviceTest { }
                }
            }
            android { compileSdk = 35 }
        """.trimIndent()
        val target = parse(agpLibrary).targets.single()
        assertEquals(KmpTargetPreset.ANDROID_LIBRARY, target.preset)
        assertEquals(listOf("androidHostTest"), target.testSourceSets)

        val legacy = parse("kotlin { androidTarget() }").targets.single()
        assertEquals(KmpTargetPreset.ANDROID_TARGET, legacy.preset)
        assertEquals(listOf("androidUnitTest", "androidInstrumentedTest"), legacy.testSourceSets)
    }

    fun testTopLevelAndroidBlockOutsideKotlinIsNotATarget() {
        assertEquals(listOf("jvm"), targetNames("android { }\nkotlin { jvm() }"))
    }

    fun testOutermostKotlinBlockIsRead() {
        assertEquals(listOf("jvm"), targetNames("subprojects {\n kotlin { iosArm64() }\n}\nkotlin { jvm() }"))
    }

    fun testRepeatedDeclarationIsMerged() {
        val info = parse("kotlin {\n jvm()\n jvm { withJava() }\n}")
        assertEquals(1, info.targets.size)
        assertTrue("withJava" in info.targets.single().configurationCalls)
    }

    fun testCustomSourceSetsAreDetected() {
        val script = """
            kotlin {
                jvm()
                sourceSets {
                    val commonMain by getting
                    val jvmAndJsMain by creating { dependsOn(commonMain) }
                    val nativeShared by registering
                    create("integrationTest")
                }
            }
        """.trimIndent()
        assertEquals(listOf("jvmAndJsMain", "nativeShared", "integrationTest"), parse(script).customSourceSets)
    }
}
