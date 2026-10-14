/*
 * File: AgentLoopGuard.kt
 * Module: agent.runtime
 *
 * Description:
 * Detects repeated actions against unchanged, freshly observed UI snapshots.
 * Stops stalled execution without claiming that the requested task succeeded.
 */
package com.example.maophoneagent.agent.runtime

import com.example.maophoneagent.agent.model.AgentStep
import com.example.maophoneagent.agent.model.AgentToolCall

object AgentLoopGuard {
    /** Observe and ListApps do not change the screen. */
    fun isAction(call: AgentToolCall): Boolean =
        call != AgentToolCall.Observe && call != AgentToolCall.ListApps

    /** Removes capture time so otherwise identical snapshots can be compared. */
    fun uiState(step: AgentStep?): String? = step?.result?.takeIf {
        step.call == AgentToolCall.Observe && it.success
    }?.uiJson?.takeIf { it.isNotBlank() }?.replace(
        Regex(""""capturedAtEpochMs"\s*:\s*\d+"""), "\"capturedAtEpochMs\":0"
    )

    /**
     * Counts consecutive identical actions whose before/after UI is unchanged.
     * Requires a fresh readable observation; screenshot-only results are not compared.
     * Swipes are excluded because repeated scrolling may be necessary for a task.
     */
    fun repeatedActionCount(history: List<AgentStep>, nextCall: AgentToolCall): Int {
        if (!isAction(nextCall) || nextCall is AgentToolCall.Swipe) return 0
        val observationIndex = history.indexOfLast { it.call == AgentToolCall.Observe }
        val actionIndex = history.indexOfLast { isAction(it.call) }
        if (observationIndex <= actionIndex) return 0
        val current = uiState(history.getOrNull(observationIndex)) ?: return 0
        var repeats = 0
        for (index in history.indices.reversed()) {
            if (!isAction(history[index].call)) continue
            if (history[index].call != nextCall) break
            val before = history.take(index).lastOrNull { it.call == AgentToolCall.Observe }
            if (uiState(before) != current) break
            repeats++
        }
        return repeats
    }
}
