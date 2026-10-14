package com.example.maophoneagent

import com.example.maophoneagent.agent.ai.OpenAiTaskContext
import com.example.maophoneagent.agent.model.*
import org.junit.Assert.*
import org.junit.Test

class OpenAiTaskContextTest {
    private val observation = AgentStep(AgentToolCall.Observe,
        AgentToolResult("observe", true, "Observed", uiJson = "{\"windows\":[]}"))
    private fun apps(success: Boolean, message: String) =
        AgentStep(AgentToolCall.ListApps, AgentToolResult("list_apps", success, message))

    @Test fun collectsObservationAndAppsBeforeRequestingAi() {
        assertEquals(AgentDecision.CallTool(AgentToolCall.Observe), OpenAiTaskContext.initialDecision(emptyList()))
        assertEquals(AgentDecision.CallTool(AgentToolCall.ListApps), OpenAiTaskContext.initialDecision(listOf(observation)))
        val history = listOf(observation, apps(true, "package:com.android.settings"))
        assertNull(OpenAiTaskContext.initialDecision(history))
        assertNull(OpenAiTaskContext.initialDecision(history + AgentStep(AgentToolCall.Home,
            AgentToolResult("home", true, "OK"))))
    }

    @Test fun actionsRequireFollowUpObservationEvenWhenTheToolFails() {
        listOf(AgentToolCall.Tap(1, 2), AgentToolCall.InputText("cats"), AgentToolCall.SubmitInput).forEach { call ->
            listOf(true, false).forEach { success ->
                val history = listOf(observation, AgentStep(call, AgentToolResult("action", success, "Result")))
                assertEquals(AgentDecision.CallTool(AgentToolCall.Observe), OpenAiTaskContext.followUpObservation(history))
            }
        }
        assertNull(OpenAiTaskContext.followUpObservation(listOf(observation)))
        assertNull(OpenAiTaskContext.followUpObservation(listOf(apps(true, "package:com.app"))))
    }

    @Test fun progressContainsOnlyTheUnchangedActionCount() {
        val action = AgentStep(AgentToolCall.SubmitInput, AgentToolResult("submit_input", true, "OK"))
        val history = listOf(observation, action, observation, action, observation)
        val progress = OpenAiTaskContext.text("Search cats", history).getJSONObject("executionProgress")
        assertEquals(1, progress.length())
        assertEquals(2, progress.getInt("unchangedAttemptsOfLastAction"))
        val stale = OpenAiTaskContext.text("Search cats", history + action).getJSONObject("executionProgress")
        assertEquals(0, stale.getInt("unchangedAttemptsOfLastAction"))
    }

    @Test fun payloadContainsUiAndCompletePackageListBeyondSummaryLimit() {
        val names = (1..1200).map { "com.example.installedapp$it" }
        val context = OpenAiTaskContext.text("Open Settings", listOf(observation,
            apps(true, "Installed packages\nExit code: 0\n" + names.joinToString("\n") { "package:$it" })))
        assertEquals(observation.result.uiJson, context.getString("latestUiJson"))
        val values = context.getJSONObject("installedApps").getJSONArray("packages")
        assertEquals(names.size, values.length())
        assertEquals(names.toSet(), (0 until values.length()).map { values.getString(it) }.toSet())
    }

    @Test fun listFailureIsReportedWithoutAutomaticBootstrapRetries() {
        val history = listOf(observation, apps(false, "Shell UserService is not connected"))
        assertNull(OpenAiTaskContext.initialDecision(history))
        val value = OpenAiTaskContext.text("Test", history).getJSONObject("installedApps")
        assertFalse(value.getBoolean("success"))
        assertEquals(0, value.getJSONArray("packages").length())
        assertTrue(value.getString("message").contains("not connected"))
    }

    @Test fun refreshedListReplacesInitialPackageContext() {
        val history = listOf(observation, apps(true, "package:com.old.app"),
            apps(true, "package:com.new.app\npackage:com.new.app"))
        val values = OpenAiTaskContext.text("Test", history).getJSONObject("installedApps").getJSONArray("packages")
        assertEquals(1, values.length())
        assertEquals("com.new.app", values.getString(0))
    }

    @Test fun settingsAliasesAreProvidedOnlyForAnInstalledSettingsPackage() {
        val present = OpenAiTaskContext.text("打开设置", listOf(observation,
            apps(true, "package:com.android.settings"))).getJSONObject("installedApps")
            .getJSONArray("knownApplications")
        assertEquals(1, present.length())
        assertEquals("com.android.settings", present.getJSONObject(0).getString("packageName"))
        assertTrue(present.getJSONObject(0).getJSONArray("aliases").toString().contains("设置"))
        val absent = OpenAiTaskContext.text("打开设置", listOf(observation,
            apps(true, "package:com.other.app"))).getJSONObject("installedApps")
            .getJSONArray("knownApplications")
        assertEquals(0, absent.length())
    }
}
