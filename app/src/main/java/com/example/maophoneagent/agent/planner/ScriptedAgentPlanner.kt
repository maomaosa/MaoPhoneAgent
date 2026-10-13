/*
 * File: ScriptedAgentPlanner.kt
 * Module: agent.planner
 *
 * Description:
 * Provides a repeatable Observe, LaunchApp, Observe sequence without an AI API.
 * Stops on tool failure so missing permissions or connections are visible during testing.
 */
package com.example.maophoneagent.agent.planner

import com.example.maophoneagent.agent.model.AgentDecision
import com.example.maophoneagent.agent.model.AgentStep
import com.example.maophoneagent.agent.model.AgentToolCall
import kotlinx.coroutines.delay

/**
 * Tests orchestration using a fixed script; task text does not change its decisions.
 *
 * @param packageName The installed package to launch on the session display.
 * @param settleDelayMs The delay after launch before requesting the final observation.
 */
class ScriptedAgentPlanner(
    private val packageName: String = "com.android.settings",
    private val settleDelayMs: Long = 1000L
) : AgentPlanner {
    init {
        require(settleDelayMs >= 0) { "Settle delay must be non-negative" }
    }

    override suspend fun nextDecision(task: String, history: List<AgentStep>): AgentDecision {
        history.lastOrNull()?.let { step ->
            check(step.result.success) { "Script stopped after ${step.result.toolName}: ${step.result.message}" }
        }
        return when (history.size) {
            0 -> AgentDecision.CallTool(AgentToolCall.Observe)
            1 -> AgentDecision.CallTool(AgentToolCall.LaunchApp(packageName))
            2 -> {
                // Gives the launched activity time to appear before the next screenshot.
                delay(settleDelayMs)
                AgentDecision.CallTool(AgentToolCall.Observe)
            }
            else -> AgentDecision.Complete("Script finished. Check the final observation to verify the app launch.")
        }
    }
}
