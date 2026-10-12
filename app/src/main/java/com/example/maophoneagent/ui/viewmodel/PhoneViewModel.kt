/*
 * File: PhoneViewModel.kt
 * Module: ui.viewmodel
 *
 * Description:
 * Coordinates phone interactions requested by the UI.
 * Delegates operations to PhoneActionService using viewModelScope
 * and exposes the current operation status as a read-only state flow.
 *
 * Tap and swipe operations report progress, results, and errors.
 * Back and Home operations report their results.
 */

package com.example.maophoneagent.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.maophoneagent.application.service.PhoneActionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Manages phone action requests and their UI status.
 *
 * @param phoneActionService The service used to perform phone operations.
 */
class PhoneViewModel(
    private val phoneActionService: PhoneActionService
) : ViewModel() {

    // Stores the current operation status, initially set to Ready.
    private val _status = MutableStateFlow("Ready")

    /**
     * Exposes the current operation status as a read-only state flow for the UI.
     */
    val status = _status.asStateFlow()

    /**
     * Requests a tap at the specified screen coordinates.
     * Updates the status with progress, the result, or an error message.
     *
     * @param x The horizontal coordinate in pixels.
     * @param y The vertical coordinate in pixels.
     */
    fun tap(x: Int, y: Int) {
        viewModelScope.launch {
            _status.value = "Executing Tap..."

            try {
                val success = phoneActionService.tap(x, y)

                _status.value = if (success) {
                    "Tap accepted: ($x, $y)"
                } else {
                    "Tap failed"
                }
            } catch (e: Exception) {
                _status.value = "Tap error: ${e.message}"
            }
        }
    }

    /**
     * Requests a swipe gesture between two screen positions.
     * Updates the status with progress, the result, or an error message.
     *
     * @param startX The starting horizontal coordinate in pixels.
     * @param startY The starting vertical coordinate in pixels.
     * @param endX The ending horizontal coordinate in pixels.
     * @param endY The ending vertical coordinate in pixels.
     * @param duration The swipe duration in milliseconds. Defaults to 500.
     */
    fun swipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Long = 500L
    ) {
        viewModelScope.launch {
            _status.value = "Executing Swipe..."

            try {
                val success = phoneActionService.swipe(
                    startX, startY, endX, endY, duration
                )

                _status.value = if (success) {
                    "Swipe accepted"
                } else {
                    "Swipe failed"
                }
            } catch (e: Exception) {
                _status.value = "Swipe error: ${e.message}"
            }
        }
    }

    /**
     * Requests the Android Back navigation action.
     * Updates the status with the operation result.
     */
    fun back() {
        viewModelScope.launch {
            _status.value = if (phoneActionService.back()) {
                "Back accepted"
            } else {
                "Back failed"
            }
        }
    }

    /**
     * Requests navigation to the Android Home screen.
     * Updates the status with the operation result.
     */
    fun home() {
        viewModelScope.launch {
            _status.value = if (phoneActionService.home()) {
                "Home accepted"
            } else {
                "Home failed"
            }
        }
    }
}
