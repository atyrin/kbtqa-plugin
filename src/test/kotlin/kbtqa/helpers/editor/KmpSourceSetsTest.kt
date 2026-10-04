package kbtqa.helpers.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [KmpSourceSetPlanner] and the KMP target/source set model.
 */
class KmpSourceSetsTest {

    @Test
    fun `plan follows default hierarchy template`() {
        val targets = listOf(
            KmpTargetPreset.JVM, KmpTargetPreset.JS, KmpTargetPreset.IOS_ARM64,
            KmpTargetPreset.IOS_SIMULATOR_ARM64, KmpTargetPreset.MACOS_ARM64, KmpTargetPreset.LINUX_X64
        ).map { KmpTarget(it) }
        assertEquals(
            listOf(
                "commonMain", "jvmMain",
                "webMain", "jsMain",
                "nativeMain", "appleMain", "iosMain", "iosArm64Main", "iosSimulatorArm64Main",
                "macosMain", "macosArm64Main",
                "linuxMain", "linuxX64Main"
            ),
            KmpSourceSetPlanner.plan(targets, includeTests = false).map { it.name }
        )
    }

    @Test
    fun `android test source sets`() {
        assertEquals(
            listOf("androidUnitTest", "androidInstrumentedTest"),
            KmpTarget(KmpTargetPreset.ANDROID_TARGET).testSourceSets
        )
        assertEquals(emptyList<String>(), KmpTarget(KmpTargetPreset.ANDROID_LIBRARY).testSourceSets)
        assertEquals(
            listOf("androidHostTest", "androidDeviceTest"),
            KmpTarget(KmpTargetPreset.ANDROID_LIBRARY, configurationCalls = setOf("withHostTest", "withDeviceTest"))
                .testSourceSets
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

    @Test
    fun `targets directly under common get their own sections`() {
        assertEquals(
            listOf("JVM & Android", "Web", "WASI", "iOS", "macOS", "tvOS", "watchOS", "Linux", "Windows (MinGW)", "Android Native"),
            KmpTargetPreset.entries.map { it.section }.distinct()
        )
        assertEquals(KmpGroup.COMMON, KmpTargetPreset.WASM_WASI.group)
    }
}
