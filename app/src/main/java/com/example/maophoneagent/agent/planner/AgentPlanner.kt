/*
 * File: AgentPlanner.kt
 * Module: agent.planner
 *
 * Description:
 * Separates the next-decision provider from task execution.
 * A future AI implementation can replace the scripted planner without changing the Runner.
 */
package com.example.maophoneagent.agent.planner

import com.example.maophoneagent.agent.model.AgentDecision
import com.example.maophoneagent.agent.model.AgentStep

interface AgentPlanner {
    /**
     * Chooses one tool request or task completion using the task and execution history.
     *
     * @param task The user's task description.
     * @param history Completed tool requests and results in execution order.
     * @return The next decision; this method does not execute phone operations.
     */
    suspend fun nextDecision(task: String, history: List<AgentStep>): AgentDecision
}
