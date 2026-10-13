package com.example.maophoneagent.agent.model

/** The next operation selected for an Agent task. */
sealed interface AgentDecision {

    /** Requests one tool execution. */
    data class CallTool(
        val call: AgentToolCall
    ) : AgentDecision

    /** Ends the task with a final message. */
    data class Complete(
        val message: String
    ) : AgentDecision
}