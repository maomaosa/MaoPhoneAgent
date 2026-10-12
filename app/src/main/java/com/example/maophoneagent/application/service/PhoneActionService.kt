/*
 * File: PhoneActionService.kt
 * Module: application.service
 *
 * Description:
 * Provides application-level operations for controlling an Android device.
 * Validates tap and swipe parameters before delegating interactions
 * to the PhoneController interface.
 *
 * The underlying controller handles device interactions, allowing
 * the control mechanism to be replaced without changing this service.
 */

package com.example.maophoneagent.application.service

import com.example.maophoneagent.domain.controller.PhoneController

/**
 * Coordinates phone interactions through the provided controller.
 * Dependency Inversion
 * @param phoneController The controller used to perform device interactions.
 */
class PhoneActionService(
    private val phoneController: PhoneController
)
{

    /**
     * Performs a tap at the specified screen coordinates after validation.
     *
     * @param x The non-negative horizontal coordinate in pixels.
     * @param y The non-negative vertical coordinate in pixels.
     * @return True if the operation succeeds; false if coordinates are invalid
     * or the controller reports failure.
     */
    suspend fun tap(x: Int, y: Int): Boolean {
        if (x < 0 || y < 0) {
            return false
        }

        return phoneController.tap(x, y)
    }

    /**
     * Performs a swipe gesture between two screen positions after validation.
     *
     * @param startX The non-negative starting horizontal coordinate in pixels.
     * @param startY The non-negative starting vertical coordinate in pixels.
     * @param endX The non-negative ending horizontal coordinate in pixels.
     * @param endY The non-negative ending vertical coordinate in pixels.
     * @param duration The swipe duration in milliseconds, from 1 to 10000.
     * Defaults to 500 milliseconds.
     * @return True if the operation succeeds; false if parameters are invalid
     * or the controller reports failure.
     */
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 500L
    ): Boolean {
        if (startX < 0 || startY < 0 ||
            endX < 0 || endY < 0) {
            return false
        }

        if (duration <= 0 || duration > 10000L) {
            return false
        }

        return phoneController.swipe(
            startX,
            startY,
            endX,
            endY,
            duration
        )
    }

    /**
     * Performs the Android Back navigation action through the controller.
     *
     * @return True if the operation succeeds; false otherwise.
     */
    suspend fun back(): Boolean {
        return phoneController.back()
    }

    /**
     * Navigates to the Android Home screen through the controller.
     *
     * @return True if the operation succeeds; false otherwise.
     */
    suspend fun home(): Boolean {
        return phoneController.home()
    }
}
