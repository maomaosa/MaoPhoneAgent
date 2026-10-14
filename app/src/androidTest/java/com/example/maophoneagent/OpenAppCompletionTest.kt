package com.example.maophoneagent

import com.example.maophoneagent.agent.ai.OpenAiTaskContext
import com.example.maophoneagent.agent.ai.OpenAppCompletion
import com.example.maophoneagent.agent.model.*
import org.junit.Assert.*
import org.junit.Test

class OpenAppCompletionTest {
    private val apps = AgentStep(AgentToolCall.ListApps,
        AgentToolResult("list_apps", true, "package:com.android.settings\npackage:com.example.app"))
    private val launch = AgentStep(AgentToolCall.LaunchApp("com.android.settings"),
        AgentToolResult("launch_app", true, "Exit code: 0"))
    private fun observe(packageName: String, active: Boolean = true, visible: Boolean = true) =
        AgentStep(AgentToolCall.Observe, AgentToolResult("observe", true, "Observed",
            uiJson = """{"windows":[{"type":1,"active":$active,"focused":false,"root":{"packageName":"$packageName","visible":$visible}}]}"""))
    private fun decide(task: String, history: List<AgentStep>) = OpenAppCompletion.nextDecision(
        task, history, OpenAiTaskContext.text(task, history).getJSONObject("installedApps"))

    @Test fun completesSimpleEnglishAndChineseRequestsWhenAppIsActive() {
        val history = listOf(apps, launch, observe("com.android.settings"))
        listOf("Open Settings", "Open Settings on the target display", "Just open Settings",
            "只打开settings", "打开设置", "请打开系统设置就好").forEach { task ->
            assertTrue(task, decide(task, history) is AgentDecision.Complete)
        }
        assertTrue(decide("Open com.example.app", listOf(apps, observe("com.example.app"))) is AgentDecision.Complete)
    }

    @Test fun successfulLaunchRequiresFreshObservationBeforeCompletion() {
        val history = listOf(apps, observe("com.android.settings"), launch)
        assertEquals(AgentDecision.CallTool(AgentToolCall.Observe), decide("Open Settings", history))
    }

    @Test fun doesNotCompleteCompoundTasks() {
        val history = listOf(apps, launch, observe("com.android.settings"))
        listOf("Open Settings and enable Wi-Fi", "打开设置并进入Wi-Fi页面", "打开设置后关闭蓝牙").forEach {
            assertNull(it, decide(it, history))
        }
    }

    @Test fun doesNotCompleteForLauncherBackgroundOrHiddenTargetWindows() {
        assertNull(decide("Open Settings", listOf(apps, launch, observe("com.android.launcher"))))
        assertNull(decide("Open Settings", listOf(apps, launch, observe("com.android.settings", active = false))))
        assertNull(decide("Open Settings", listOf(apps, launch, observe("com.android.settings", visible = false))))
    }

    @Test fun doesNotCompleteForFailedOrScreenshotOnlyObservations() {
        val missingUi = AgentStep(AgentToolCall.Observe, AgentToolResult("observe", true, "Screenshot only"))
        assertNull(decide("Open Settings", listOf(apps, launch, missingUi)))
        val failed = observe("com.android.settings").let { it.copy(result = it.result.copy(success = false)) }
        assertNull(decide("Open Settings", listOf(apps, launch, failed)))
    }

    @Test fun unavailablePackagesAndUnknownNamesAreLeftToAi() {
        val history = listOf(apps, observe("com.android.settings"))
        assertNull(decide("Open UnknownApp", history))
        val failedList = apps.copy(result = apps.result.copy(success = false))
        assertNull(decide("Open Settings", listOf(failedList, observe("com.android.settings"))))
    }
}
