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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.maophoneagent.application.service.PhoneObservationService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Latest observation artifacts and request progress for the test UI. */
data class ObservationState(
    val busy: Boolean = false,
    val status: String = "Ready",
    val json: String = "",
    val jsonPath: String = "",
    val screenshotPath: String = "",
    val image: Bitmap? = null
)

/**
 * Manages phone action requests and their UI status.
 *
 * @param phoneActionService The service used to perform phone operations.
 */
class PhoneViewModel(
    private val phoneActionService: PhoneActionService
) : ViewModel() {

    private val observationService = PhoneObservationService()
    private val _observation = MutableStateFlow(ObservationState())
    val observation = _observation.asStateFlow()

    /**
     * Reads UI nodes or captures a screenshot after an optional delay.
     * The delay allows switching to another app before sampling its screen.
     * Requests are serialized; UI snapshots and screenshots are separate samples.
     *
     * @param displayId The non-negative display ID, including 0 for the main screen.
     * @param delaySeconds The delay from 0 to 10 seconds.
     * @param screenshot True to capture a PNG; false to read UI nodes.
     */
    fun observeScreen(displayId: Int, delaySeconds: Int, screenshot: Boolean) {
        if (_observation.value.busy) return
        if (displayId < 0 || delaySeconds !in 0..10) {
            _observation.value = _observation.value.copy(status = "Invalid display or delay")
            return
        }
        _observation.value = _observation.value.copy(busy = true, status = "Waiting $delaySeconds seconds...")
        viewModelScope.launch {
            try {
                delay(delaySeconds * 1000L)
                _observation.value = _observation.value.copy(status = "Reading display $displayId...")
                if (screenshot) {
                    val capture = observationService.capture(displayId)
                    val image = withContext(Dispatchers.IO) {
                        checkNotNull(BitmapFactory.decodeFile(capture.filePath)) { "Cannot load screenshot preview" }
                    }
                    _observation.value = _observation.value.copy(
                        screenshotPath = capture.filePath,
                        image = image,
                        status = "Screenshot: display=$displayId, ${capture.width} x ${capture.height}"
                    )
                } else {
                    val snapshot = observationService.readUi(displayId)
                    _observation.value = _observation.value.copy(
                        json = snapshot.json,
                        jsonPath = snapshot.filePath,
                        status = "UI snapshot: display=$displayId, nodes=${snapshot.nodeCount}"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _observation.value = _observation.value.copy(status = "Observation failed: ${e.message}")
            } finally {
                _observation.value = _observation.value.copy(busy = false)
            }
        }
    }

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
