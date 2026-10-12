/*
 * File: PhoneAccessibilityService.kt
 * Module: infrastructure.accessibility
 *
 * Description:
 * Performs phone interactions through Android Accessibility Services.
 * Supports tap and swipe gestures, along with Back and Home navigation.
 *
 * Exposes the current service instance and connection state.
 * Gesture operations await completion callbacks and return failure
 * when dispatch is rejected, the gesture is cancelled, or the wait times out.
 */

package com.example.maophoneagent.infrastructure.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Provides accessibility-based gestures and system navigation actions.
 */
class PhoneAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PhoneAgent"

        // Keeps the current service reference visible across threads.
        @Volatile
        private var instance: PhoneAccessibilityService? = null

        // Tracks whether a service instance is currently connected.
        private val _connected = MutableStateFlow(false)
        /**
         * Exposes the service connection state as a read-only state flow.
         */
        val connected = _connected.asStateFlow()

        /**
         * Returns the current accessibility service instance.
         *
         * @return The connected service instance, or null if unavailable.
         */
        fun currentInstance(): PhoneAccessibilityService? {
            return instance
        }
    }

    /**
     * Registers this instance and marks the service as connected.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()

        instance = this
        _connected.value = true

        Log.d(TAG, "Accessibility Service connected")
    }

    /**
     * Receives accessibility events without processing them at this stage.
     *
     * @param event The accessibility event supplied by the system.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // UI event processing is pending implementation.
    }

    /**
     * Records a system request to interrupt accessibility feedback.
     */
    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    /**
     * Clears the shared instance and connection state if this is the current service.
     */
    override fun onDestroy() {
        if (instance === this) {
            instance = null
            _connected.value = false
        }

        Log.d(TAG, "Accessibility Service destroyed")
        super.onDestroy()
    }

    /**
     * Performs a tap at the specified screen coordinates using a 100 ms gesture.
     *
     * @param x The horizontal coordinate in pixels.
     * @param y The vertical coordinate in pixels.
     * @return True if the gesture completes; false if rejected, cancelled, or timed out.
     */
    suspend fun executeTap(x: Int, y: Int): Boolean {

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        return executeGesture(path, 100L)
    }

    /**
     * Performs a swipe gesture between two screen positions.
     *
     * @param startX The starting horizontal coordinate in pixels.
     * @param startY The starting vertical coordinate in pixels.
     * @param endX The ending horizontal coordinate in pixels.
     * @param endY The ending vertical coordinate in pixels.
     * @param duration The swipe duration in milliseconds.
     * @return True if the gesture completes; false if rejected, cancelled, or timed out.
     */
    suspend fun executeSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): Boolean {

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }

        return executeGesture(path, duration)
    }

    /**
     * Dispatches a single-stroke gesture on the main thread and awaits its result.
     * Allows an additional 5000 milliseconds beyond the gesture duration for completion.
     *
     * @param path The screen path followed by the gesture.
     * @param duration The gesture duration in milliseconds.
     * @return True if the completion callback is received; false if dispatch is rejected,
     * the gesture is cancelled, or the wait times out.
     */
    private suspend fun executeGesture(
        path: Path,
        duration: Long
    ): Boolean = withContext(Dispatchers.Main.immediate) {

        // Builds the Android gesture from the specified path and duration.
        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    duration
                )
            )
            .build()
        // Limits the time spent waiting for the gesture result.
        withTimeoutOrNull((duration + 5000L).milliseconds) {
            // Suspends the current coroutine until Android reports the gesture result.
            suspendCancellableCoroutine<Boolean> { continuation ->

                // Resumes only active continuations to handle late callbacks safely.
                val callback = object : GestureResultCallback() {

                    override fun onCompleted(
                        gestureDescription: GestureDescription?
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(true)
                        }
                    }

                    override fun onCancelled(
                        gestureDescription: GestureDescription?
                    ) {
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                }

                val dispatched = dispatchGesture(
                    gesture,
                    callback,
                    null
                )

                // Reports immediate rejection without waiting for a callback.
                if (!dispatched && continuation.isActive) {
                    continuation.resume(false)
                }
            }
        } ?: false
    }

    /**
     * Requests the Android Back navigation action.
     *
     * @return True if the system reports success; false otherwise.
     */
    fun executeBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    /**
     * Requests navigation to the Android Home screen.
     *
     * @return True if the system reports success; false otherwise.
     */
    fun executeHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
