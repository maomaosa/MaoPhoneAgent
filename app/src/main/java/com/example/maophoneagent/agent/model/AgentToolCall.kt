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
 * Supports observations, gestures, navigation, app launches, and package listing.
 * The sealed hierarchy allows the executor to handle every supported request explicitly.
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

    /**
     * Requests a swipe between screen coordinates on the session's display.
     *
     * @property startX The non-negative starting horizontal coordinate in pixels.
     * @property startY The non-negative starting vertical coordinate in pixels.
     * @property endX The non-negative ending horizontal coordinate in pixels.
     * @property endY The non-negative ending vertical coordinate in pixels.
     * @property durationMs The duration from 1 to 10000 milliseconds. Defaults to 500.
     */
    data class Swipe(
        val startX: Int,
        val startY: Int,
        val endX: Int,
        val endY: Int,
        val durationMs: Int = 500
    ) : AgentToolCall

    /** Requests Home navigation on the session's display through a shell key event. */
    data object Home : AgentToolCall

    /** Requests Back navigation on the session's display through a shell key event. */
    data object Back : AgentToolCall

    /**
     * Requests an installed app on the session's display.
     * The executor validates the package name before dispatch.
     *
     * @property packageName The installed package containing the activity to launch.
     */
    data class LaunchApp(val packageName: String) : AgentToolCall

    /**
     * Requests installed package names through the shell UserService.
     * Includes packages without launcher activities. The list is device-wide,
     * although the executor still validates the current session display.
     */
    data object ListApps : AgentToolCall
}

/**
 * Structured response returned by a tool execution.
 *
 * @property toolName The tool name or validation/error identifier supplied by the executor.
 * @property success True when at least one Observe artifact is available, or when
 * an action's shell command reports exit code 0. This does not confirm an app's response.
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

    // Action results and failed observations leave these fields at their defaults.
    val uiJson: String? = null,
    val screenshotPath: String? = null,
    val nodeCount: Int = 0
)
