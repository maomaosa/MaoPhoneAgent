/*
 * File: AgentToolCall.kt
 * Module: agent.model
 *
 * Description:
 * Defines the supported Agent tool requests and their execution results.
 * Requests contain action parameters; the target display comes from AgentSession.
 * Observation results may include UI JSON and a local screenshot path.
 */

package com.example.maophoneagent.agent.model

/**
 * Supported tool requests.
 *
 * Supports Observe and Tap. The sealed hierarchy allows the executor
 * to handle every supported request explicitly.
 */
sealed interface AgentToolCall {

    /**
     * Requests UI hierarchy information and a screenshot of the session's display.
     * The two observations are collected sequentially and may differ in capture time.
     */
    data object Observe : AgentToolCall

    /**
     * Requests a tap at screen coordinates on the session's display.
     * Coordinate validation is performed by the executor before dispatch.
     *
     * @property x The non-negative horizontal coordinate in pixels.
     * @property y The non-negative vertical coordinate in pixels.
     */
    data class Tap(
        val x: Int,
        val y: Int
    ) : AgentToolCall
}

/**
 * Structured response returned by a tool execution.
 *
 * @property toolName The tool name or validation/error identifier supplied by the executor.
 * @property success True when at least one Observe artifact is available, or when
 * the Tap shell command reports exit code 0. This does not confirm an app's response.
 * @property message The execution summary, shell output, or failure details.
 * @property uiJson The captured UI hierarchy JSON, or null when unavailable.
 * @property screenshotPath The local PNG cache path, or null when unavailable.
 * The file must be read or uploaded separately for a remote AI service.
 * @property nodeCount The collected node count, or 0 when no UI snapshot is available.
 */
data class AgentToolResult(
    val toolName: String,
    val success: Boolean,
    val message: String,

    // Tap results and failed observations leave these fields at their defaults.
    val uiJson: String? = null,
    val screenshotPath: String? = null,
    val nodeCount: Int = 0
)
