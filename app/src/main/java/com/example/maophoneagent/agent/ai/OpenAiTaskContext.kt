/*
 * File: OpenAiTaskContext.kt
 * Module: agent.ai
 *
 * Description:
 * Collects initial observation and package-list requirements before an AI request.
 * Builds task context with a dedicated installed-app list and the latest UI JSON.
 */
package com.example.maophoneagent.agent.ai

import com.example.maophoneagent.agent.model.AgentDecision
import com.example.maophoneagent.agent.model.AgentStep
import com.example.maophoneagent.agent.model.AgentToolCall
import org.json.JSONArray
import org.json.JSONObject

object OpenAiTaskContext {
    /** Requests each prerequisite once per task; failed results are still reported to AI. */
    fun initialDecision(history: List<AgentStep>): AgentDecision.CallTool? {
        if (history.none { it.call == AgentToolCall.Observe }) {
            return AgentDecision.CallTool(AgentToolCall.Observe)
        }
        if (history.none { it.call == AgentToolCall.ListApps }) {
            return AgentDecision.CallTool(AgentToolCall.ListApps)
        }
        return null
    }

    /** Preserves the complete package list separately from length-limited step summaries. */
    fun text(task: String, history: List<AgentStep>): JSONObject {
        val appResult = history.lastOrNull { it.call == AgentToolCall.ListApps }?.result
        val packages = if (appResult?.success == true) {
            appResult.message.lineSequence().map { it.trim() }
                .filter { it.startsWith("package:") }
                .map { it.removePrefix("package:").trim() }
                .filter { it.isNotEmpty() }.distinct().sorted().toList()
        } else emptyList()
        val apps = JSONObject().put("success", appResult?.success ?: false)
            .put("packages", JSONArray(packages))
            .put("message", if (appResult?.success == true) "${packages.size} installed packages" else
                appResult?.message?.take(16000) ?: "Package list unavailable")
        val records = JSONArray(history.mapIndexed { index, step ->
            JSONObject().put("step", index + 1).put("request", step.call.toString())
                .put("success", step.result.success)
                .put("result", if (step.call == AgentToolCall.ListApps && step.result.success)
                    "See installedApps for package names" else step.result.message.take(16000))
        })
        val latest = history.lastOrNull { it.call == AgentToolCall.Observe }?.result
        return JSONObject().put("task", task).put("history", records).put("installedApps", apps)
            .put("latestUiJson", latest?.uiJson?.take(100000) ?: JSONObject.NULL)
    }
}
