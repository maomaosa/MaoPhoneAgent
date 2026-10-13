/*
 * File: AgentStep.kt
 * Module: agent.model
 *
 * Description:
 * Records tool requests and their results for planning, progress display, and task history.
 */
package com.example.maophoneagent.agent.model

/** One completed tool execution, including failures returned by the executor. */
data class AgentStep(val call: AgentToolCall, val result: AgentToolResult)

/**
 * Final task outcome. Cancellation propagates instead of returning a completed outcome.
 *
 * @property completed True when the planner chooses Complete.
 * @property message The completion summary or reason execution stopped.
 * @property steps An immutable snapshot of completed tool executions.
 */
data class AgentRunResult(
    val completed: Boolean,
    val message: String,
    val steps: List<AgentStep>
)
