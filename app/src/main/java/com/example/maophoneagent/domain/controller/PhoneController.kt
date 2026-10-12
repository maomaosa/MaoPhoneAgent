/*
 * File: PhoneController.kt
 * Module: domain.controller
 *
 * Description:
 * Defines the core interface for controlling an Android device.
 * Provides a unified abstraction for basic interactions,
 * including tapping, swiping, and system navigation.
 *
 * Implementations may use different control mechanisms,
 * such as ADB or Android Accessibility Services.
 *
 * This interface is independent of any specific control
 * implementation, allowing the underlying mechanism to be
 * replaced without affecting higher-level business logic.
 */

package com.example.maophoneagent.domain.controller

interface PhoneController {

    /**
     * Performs a tap at the specified screen coordinates.
     *
     * @param x The horizontal coordinate in pixels.
     * @param y The vertical coordinate in pixels.
     * @return True if the operation succeeds; false otherwise.
     */
    suspend fun tap(x: Int, y: Int): Boolean

    /**
     * Performs a swipe gesture between two screen positions.
     *
     * @param startX The starting horizontal coordinate in pixels.
     * @param startY The starting vertical coordinate in pixels.
     * @param endX The ending horizontal coordinate in pixels.
     * @param endY The ending vertical coordinate in pixels.
     * @param duration The swipe duration in milliseconds.
     * @return True if the operation succeeds; false otherwise.
     */
    suspend fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long
    ): Boolean

    /**
     * Performs the Android Back navigation action.
     *
     * @return True if the operation succeeds; false otherwise.
     */
    suspend fun back(): Boolean

    /**
     * Navigates to the Android Home screen.
     *
     * @return True if the operation succeeds; false otherwise.
     */
    suspend fun home(): Boolean

}