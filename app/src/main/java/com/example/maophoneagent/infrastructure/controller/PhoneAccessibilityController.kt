/*
 * File: PhoneAccessibilityController.kt
 * Module: infrastructure.controller
 *
 * Description:
 * Implements PhoneController using Android Accessibility Services.
 * Delegates tapping, swiping, and system navigation to the current
 * PhoneAccessibilityService instance.
 *
 * Each operation returns false when the service is unavailable.
 * Otherwise, the operation result is provided by the service.
 */

package com.example.maophoneagent.infrastructure.controller

import android.util.Log
import com.example.maophoneagent.domain.controller.PhoneController
import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService

/**
 * Connects the phone control interface to the accessibility service.
 */
class AccessibilityPhoneController : PhoneController {

    private val tag = "PhoneAgent"

    /**
     * Performs a tap at the specified screen coordinates through the service.
     *
     * @param x The horizontal coordinate in pixels.
     * @param y The vertical coordinate in pixels.
     * @return True if the gesture completes; false if the service is unavailable
     * or the gesture is rejected, cancelled, or timed out.
     */
    override suspend fun tap(x: Int, y: Int): Boolean {

        val service = PhoneAccessibilityService.currentInstance() ?: return false

        Log.d(tag, "Executing real Tap: ($x, $y)")

        return service.executeTap(x, y)
    }

    /**
     * Performs a swipe gesture between two screen positions through the service.
     *
     * @param startX The starting horizontal coordinate in pixels.
     * @param startY The starting vertical coordinate in pixels.
     * @param endX The ending horizontal coordinate in pixels.
     * @param endY The ending vertical coordinate in pixels.
     * @param duration The swipe duration in milliseconds.
     * @return True if the gesture completes; false if the service is unavailable
     * or the gesture is rejected, cancelled, or timed out.
     */
    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): Boolean {

        val service = PhoneAccessibilityService.currentInstance() ?: return false

        Log.d(tag, "Executing real Swipe")

        return service.executeSwipe(
            startX,
            startY,
            endX,
            endY,
            duration
        )
    }

    /**
     * Requests the Android Back navigation action through the service.
     *
     * @return True if the system reports success; false if the service is unavailable
     * or the system reports failure.
     */
    override suspend fun back(): Boolean {

        val service = PhoneAccessibilityService.currentInstance() ?: return false

        return service.executeBack()
    }

    /**
     * Requests navigation to the Android Home screen through the service.
     *
     * @return True if the system reports success; false if the service is unavailable
     * or the system reports failure.
     */
    override suspend fun home(): Boolean {

        val service = PhoneAccessibilityService.currentInstance() ?: return false

        return service.executeHome()
    }
}
