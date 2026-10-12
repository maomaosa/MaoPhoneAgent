/*
 * File: MockPhoneController.kt
 * Module: infrastructure.controller
 *
 * Description:
 * Provides a mock implementation of the PhoneController interface.
 * Provides placeholders for basic interactions, including tapping,
 * swiping, and system navigation.
 *
 * Currently uses debug logging to verify operation calls.
 * Actual device control will be implemented in a later stage.
 *
 * All operations return success, allowing higher-level business
 * logic to be developed and tested without a real device
 * or an active control mechanism.
 */

package com.example.maophoneagent.infrastructure.controller

import android.util.Log
import com.example.maophoneagent.domain.controller.PhoneController

class MockPhoneController : PhoneController {

    /**
     * Represents a tap at the specified screen coordinates.
     *
     * @param x The horizontal coordinate in pixels.
     * @param y The vertical coordinate in pixels.
     * @return Always true to simulate a successful operation.
     */
    override suspend fun tap(x: Int, y: Int): Boolean {
        Log.d("PhoneAgent", "Tap at ($x, $y)")
        return true
    }

    /**
     * Represents a swipe gesture between two screen positions.
     *
     * @param startX The starting horizontal coordinate in pixels.
     * @param startY The starting vertical coordinate in pixels.
     * @param endX The ending horizontal coordinate in pixels.
     * @param endY The ending vertical coordinate in pixels.
     * @param duration The swipe duration in milliseconds.
     * @return Always true to simulate a successful operation.
     */
    override suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): Boolean {
        Log.d(
            "PhoneAgent",
            "Swipe from ($startX, $startY) to ($endX, $endY), duration=$duration"
        )
        return true
    }

    /**
     * Represents the Android Back navigation action.
     *
     * @return Always true to simulate a successful operation.
     */
    override suspend fun back(): Boolean {
        Log.d("PhoneAgent", "Back")
        return true
    }

    /**
     * Represents navigation to the Android Home screen.
     *
     * @return Always true to simulate a successful operation.
     */
    override suspend fun home(): Boolean {
        Log.d("PhoneAgent", "Home")
        return true
    }
}
