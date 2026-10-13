/*
 * File: PhoneObservationService.kt
 * Module: application.service
 *
 * Description:
 * Provides on-demand screen information and screenshots from the connected
 * accessibility service. Keeps observation requests independent of test UI code.
 */
package com.example.maophoneagent.application.service

import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService
import com.example.maophoneagent.infrastructure.accessibility.ScreenObservationReader
import com.example.maophoneagent.infrastructure.accessibility.ScreenCapture
import com.example.maophoneagent.infrastructure.accessibility.UiSnapshot

class PhoneObservationService {
    private fun reader(): ScreenObservationReader {
        val service = checkNotNull(PhoneAccessibilityService.currentInstance()) {
            "Accessibility service is not connected"
        }
        return ScreenObservationReader(service)
    }

    /** Reads accessible window nodes for the requested display and exports JSON. */
    suspend fun readUi(displayId: Int): UiSnapshot = reader().readUi(displayId)

    /** Captures the requested display and exports a PNG. */
    suspend fun capture(displayId: Int): ScreenCapture = reader().capture(displayId)
}
