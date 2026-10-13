/*
 * File: AgentSession.kt
 * Module: agent.model
 *
 * Description:
 * Holds the application-selected target display for Agent tool execution.
 * Rejects the main display and negative IDs when a session is constructed.
 * Display availability is checked by PhoneToolExecutor before each request.
 */

package com.example.maophoneagent.agent.model

/**
 * Represents a phone Agent execution session.
 *
 * The target display is selected by the application,
 * not by the AI model.
 *
 * @property displayId The positive Android display ID used by all tools in this session.
 * @throws IllegalArgumentException If the display ID is zero or negative.
 */
data class AgentSession(
    val displayId: Int
) {
    init {
        // ID 0 is the main display; Agent tools are restricted to secondary displays.
        require(displayId > 0) {
            "Agent cannot control the main display"
        }
    }
}
