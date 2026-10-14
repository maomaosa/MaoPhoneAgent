/*
 * File: AgentRunner.kt
 * Module: agent.runtime
 *
 * Description:
 * Runs one task by alternating planner decisions and tool executions.
 * Keeps execution history, reports progress, and enforces task limits.
 */
package com.example.maophoneagent.agent.runtime

import com.example.maophoneagent.agent.model.AgentDecision
import com.example.maophoneagent.agent.model.AgentRunResult
import com.example.maophoneagent.agent.model.AgentSession
import com.example.maophoneagent.agent.model.AgentStep
import com.example.maophoneagent.agent.model.AgentToolCall
import com.example.maophoneagent.agent.model.AgentToolResult
import com.example.maophoneagent.agent.planner.AgentPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coordinates decisions without depending on a particular AI provider or Android UI.
 *
 * @param planner The provider that chooses the next decision.
 * @param executeTool The tool execution function, normally PhoneToolExecutor::execute.
 */
class AgentRunner(
    private val planner: AgentPlanner,
    private val executeTool: suspend (AgentSession, AgentToolCall) -> AgentToolResult
) {
    private val taskMutex = Mutex()

    /**
     * Runs a task until completion, failure, timeout, or the tool call limit.
     * All tool requests, including initial observation, come from the planner.
     * Tool failures are recorded and passed back to the planner rather than retried here.
     *
     * @param task A non-blank task description.
     * @param session The fixed target display for this task.
     * @param maxToolCalls The maximum number of tool executions.
     * @param timeoutMs The overall coroutine timeout in milliseconds.
     * @param onProgress A callback receiving status and a copy of completed steps.
     * @return The final outcome and completed execution history.
     * @throws CancellationException If the caller cancels the task.
     */
    suspend fun run(
        task: String,
        session: AgentSession,
        maxToolCalls: Int = 10,
        timeoutMs: Long = 60_000L,
        onProgress: (String, List<AgentStep>) -> Unit = { _, _ -> }
    ): AgentRunResult {
        require(task.isNotBlank()) { "Task must not be blank" }
        require(maxToolCalls > 0) { "Tool call limit must be positive" }
        require(timeoutMs > 0) { "Task timeout must be positive" }
        if (!taskMutex.tryLock()) return AgentRunResult(false, "Another task is running", emptyList())

        val history = mutableListOf<AgentStep>()
        try {
            return withTimeoutOrNull(timeoutMs.milliseconds) {
                var outcome: AgentRunResult? = null
                while (outcome == null) {
                    currentCoroutineContext().ensureActive()
                    onProgress("Requesting next decision...", history.toList())
                    when (val decision = planner.nextDecision(task, history.toList())) {
                        is AgentDecision.Complete -> {
                            outcome = AgentRunResult(true, decision.message, history.toList())
                        }
                        is AgentDecision.CallTool -> {
                            if (history.size >= maxToolCalls) {
                                outcome = AgentRunResult(false, "Tool call limit reached ($maxToolCalls)", history.toList())
                            } else if (AgentLoopGuard.repeatedActionCount(history, decision.call) >= 2) {
                                // Reject the third identical action when two attempts left the UI unchanged.
                                outcome = AgentRunResult(false,
                                    "Stopped repeated action: ${decision.call}. Two attempts left the observed UI unchanged. Review the current screen before retrying; task completion was not confirmed.",
                                    history.toList())
                            } else {
                                onProgress("Executing step ${history.size + 1}: ${decision.call}", history.toList())
                                val result = executeTool(session, decision.call)
                                history += AgentStep(decision.call, result)
                                onProgress("Step ${history.size}: ${result.toolName}, success=${result.success}", history.toList())
                            }
                        }
                    }
                }
                outcome
            } ?: AgentRunResult(false, "Task timed out after $timeoutMs ms", history.toList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AgentRunResult(false, "Task failed: ${e.message}", history.toList())
        } finally {
            // Cancellation stops future steps; it cannot undo an already dispatched action.
            taskMutex.unlock()
        }
    }
}
