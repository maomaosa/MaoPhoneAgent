/*
 * File: PhoneToolExecutor.kt
 * Module: agent.tool
 *
 * Description:
 * Executes supported Agent tools against an application-selected secondary display.
 * Reads observations through PhoneObservationService and dispatches actions through Shizuku.
 * Serializes requests within this executor instance and returns structured results.
 */

package com.example.maophoneagent.agent.tool

import android.hardware.display.DisplayManager
import com.example.maophoneagent.agent.model.AgentSession
import com.example.maophoneagent.agent.model.AgentToolCall
import com.example.maophoneagent.agent.model.AgentToolResult
import com.example.maophoneagent.application.service.PhoneObservationService
import com.example.maophoneagent.infrastructure.shizuku.ShizukuShellClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Unified execution entry point for Agent phone tools.
 *
 * @param observationService The service used to obtain UI JSON and screenshots.
 * @param shellClient The connected Shizuku client used for display-specific actions.
 * @param displayManager The Android display manager used to check target availability.
 */
class PhoneToolExecutor(
    private val observationService: PhoneObservationService,
    private val shellClient: ShizukuShellClient,
    private val displayManager: DisplayManager
) {

    // Serializes calls through this instance; direct UI or other executor calls are independent.
    private val executionMutex = Mutex()

    /**
     * Validates the session display and executes the requested tool.
     * Expected operation failures are returned as results; coroutine cancellation propagates.
     *
     * @param session The application-selected target display context.
     * @param call The observation, gesture, or navigation request to execute.
     * @return The tool result, or a validation failure if the display is unavailable.
     * @throws CancellationException If the calling coroutine is cancelled.
     */
    suspend fun execute(
        session: AgentSession,
        call: AgentToolCall
    ): AgentToolResult = executionMutex.withLock {

        val displayId = session.displayId

        // Prevent actions on missing or invalid displays.
        if (displayId <= 0 ||
            displayManager.getDisplay(displayId) == null
        ) {
            return@withLock AgentToolResult(
                toolName = "validation",
                success = false,
                message = "Invalid virtual display: $displayId"
            )
        }

        try {
            when (call) {

                AgentToolCall.Observe -> {
                    executeObserve(displayId)
                }

                is AgentToolCall.Tap -> {
                    executeTap(displayId, call)
                }

                is AgentToolCall.Swipe -> {
                    executeSwipe(displayId, call)
                }

                AgentToolCall.Home -> {
                    shellResult("home", shellClient.homeForAgent(displayId))
                }

                AgentToolCall.Back -> {
                    shellResult("back", shellClient.backForAgent(displayId))
                }
            }

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {
            AgentToolResult(
                toolName = call.javaClass.simpleName,
                success = false,
                message =
                    "${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    /**
     * Reads the UI hierarchy, then captures a screenshot of the same display.
     * Preserves available artifacts if one operation fails, including a screenshot timeout.
     *
     * @param displayId The validated positive Android display ID.
     * @return An Observe result marked successful when either artifact is available.
     * Failure details for missing artifacts are included in the message.
     */
    private suspend fun executeObserve(
        displayId: Int
    ): AgentToolResult {

        val errors = mutableListOf<String>()

        val ui = try {
            observationService.readUi(displayId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("UI: ${e.message}")
            null
        }

        val screenshot = try {
            observationService.capture(displayId)
        } catch (e: TimeoutCancellationException) {
            // A capture timeout should preserve UI data; caller cancellation still propagates.
            currentCoroutineContext().ensureActive()
            errors.add("Screenshot: capture timed out")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errors.add("Screenshot: ${e.message}")
            null
        }

        // Partial observation is useful; the message identifies any missing artifact.
        val success = ui != null || screenshot != null

        return AgentToolResult(
            toolName = "observe",
            success = success,

            message = buildString {
                append("Display=$displayId")
                append("\nNodes=${ui?.nodeCount ?: 0}")
                append("\nScreenshot=${screenshot != null}")

                if (errors.isNotEmpty()) {
                    append("\nErrors: ${errors.joinToString()}")
                }
            },

            uiJson = ui?.json,
            screenshotPath = screenshot?.filePath,
            nodeCount = ui?.nodeCount ?: 0
        )
    }

    /**
     * Validates tap coordinates and awaits the Shizuku shell command result.
     * Requires a running Shizuku service and a connected Shell UserService.
     *
     * @param displayId The validated positive Android display ID.
     * @param call The tap coordinates supplied by the tool request.
     * @return A Tap result containing shell output, or a coordinate validation failure.
     */
    private suspend fun executeTap(
        displayId: Int,
        call: AgentToolCall.Tap
    ): AgentToolResult {

        if (call.x < 0 || call.y < 0) {
            return AgentToolResult(
                toolName = "tap",
                success = false,
                message = "Invalid coordinates"
            )
        }

        val result = shellClient.tapForAgent(
            displayId,
            call.x,
            call.y
        )

        return shellResult("tap", result)
    }

    /**
     * Validates swipe parameters and awaits the Shizuku shell command result.
     *
     * @param displayId The validated positive Android display ID.
     * @param call The start/end coordinates and duration supplied by the tool request.
     * @return A Swipe result containing shell output or a parameter validation failure.
     */
    private suspend fun executeSwipe(displayId: Int, call: AgentToolCall.Swipe): AgentToolResult {
        if (listOf(call.startX, call.startY, call.endX, call.endY).any { it < 0 } ||
            call.durationMs !in 1..10000
        ) {
            return AgentToolResult("swipe", false, "Invalid swipe coordinates or duration")
        }
        val result = shellClient.swipeForAgent(
            displayId, call.startX, call.startY, call.endX, call.endY, call.durationMs
        )
        return shellResult("swipe", result)
    }

    /** Converts the existing shell text response into a structured action result. */
    private fun shellResult(toolName: String, result: String): AgentToolResult {
        // Parses the current text response; exit code 0 indicates command completion,
        // while the target app's visible response must be verified separately.
        val success = Regex(
            """(?m)^Exit code:\s*0\s*$"""
        ).containsMatchIn(result)

        return AgentToolResult(
            toolName = toolName,
            success = success,
            message = result
        )
    }
}
