/*
 * File: ScreenObservationReader.kt
 * Module: infrastructure.accessibility
 *
 * Description:
 * Reads display-specific accessibility windows and captures screenshots.
 * Exports bounded UI snapshots as JSON and screenshots as PNG in app-private cache.
 * These artifacts can be consumed by a future AI integration without UI dependencies.
 */

package com.example.maophoneagent.infrastructure.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

/** A structured snapshot and its exported file, taken independently of a screenshot. */
data class UiSnapshot(val json: String, val filePath: String, val nodeCount: Int)

/** A PNG screenshot and metadata for preview or later AI input. */
data class ScreenCapture(val filePath: String, val width: Int, val height: Int)

/**
 * Observes screens exposed to the connected accessibility service.
 *
 * @param service The connected service used for window queries and screenshot requests.
 */
class ScreenObservationReader(private val service: AccessibilityService) {
    companion object {
        private const val MAX_NODES = 1500
        private const val MAX_DEPTH = 40
        private const val MAX_TEXT_LENGTH = 2000
    }

    private fun requireDisplay(displayId: Int) {
        require(displayId >= 0) { "Display ID must be non-negative" }
        val manager = service.getSystemService(DisplayManager::class.java)
        require(manager.getDisplay(displayId) != null) { "Display $displayId is unavailable" }
    }

    /**
     * Reads windows on the selected display, with bounded node count and depth.
     * Password values are omitted. Missing roots are reported rather than replaced
     * by another display's active window. Node bounds use screen pixel coordinates.
     *
     * @param displayId The Android display ID; 0 selects the main display.
     * @return JSON, its cache file path, and the number of collected nodes.
     */
    @Suppress("DEPRECATION")
    suspend fun readUi(displayId: Int): UiSnapshot = withContext(Dispatchers.IO) {
        requireDisplay(displayId)
        var nodeCount = 0
        var truncated = false

        fun readNode(node: AccessibilityNodeInfo, depth: Int): JSONObject {
            nodeCount++
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            val result = JSONObject()
                .put("index", nodeCount)
                .put("packageName", node.packageName?.toString().orEmpty())
                .put("className", node.className?.toString().orEmpty())
                .put("viewId", node.viewIdResourceName.orEmpty())
                .put("text", if (node.isPassword) "" else node.text?.toString().orEmpty().take(MAX_TEXT_LENGTH))
                .put("contentDescription", if (node.isPassword) "" else node.contentDescription?.toString().orEmpty().take(MAX_TEXT_LENGTH))
                .put("password", node.isPassword)
                .put("bounds", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                .put("clickable", node.isClickable)
                .put("scrollable", node.isScrollable)
                .put("editable", node.isEditable)
                .put("enabled", node.isEnabled)
                .put("visible", node.isVisibleToUser)
                .put("focused", node.isFocused)
                .put("checked", node.isChecked)
                .put("selected", node.isSelected)
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                if (depth >= MAX_DEPTH || nodeCount >= MAX_NODES) {
                    truncated = true
                    break
                }
                val child = node.getChild(i) ?: continue
                try {
                    children.put(readNode(child, depth + 1))
                } finally {
                    child.recycle()
                }
            }
            return result.put("children", children)
        }

        val windows = service.windowsOnAllDisplays.get(displayId).orEmpty()
        check(windows.isNotEmpty()) { "No accessible windows on display $displayId" }
        val entries = JSONArray()
        try {
            for (window in windows) {
                val entry = JSONObject()
                    .put("windowId", window.id)
                    .put("title", window.title?.toString().orEmpty())
                    .put("type", window.type)
                    .put("active", window.isActive)
                    .put("focused", window.isFocused)
                val root = window.root
                if (root != null) {
                    try {
                        if (nodeCount < MAX_NODES) {
                            entry.put("root", readNode(root, 0))
                        } else {
                            truncated = true
                            entry.put("root", JSONObject.NULL)
                        }
                    } finally {
                        root.recycle()
                    }
                } else {
                    entry.put("root", JSONObject.NULL)
                }
                entries.put(entry)
            }
        } finally {
            windows.forEach { it.recycle() }
        }
        check(nodeCount > 0) { "Windows exist, but no readable nodes on display $displayId" }
        val json = JSONObject()
            .put("schemaVersion", 1)
            .put("displayId", displayId)
            .put("capturedAtEpochMs", System.currentTimeMillis())
            .put("nodeCount", nodeCount)
            .put("truncated", truncated)
            .put("windows", entries)
            .toString(2)
        // Replaces the previous snapshot for this display to bound cache growth.
        val file = File(service.cacheDir, "ui_snapshot_$displayId.json")
        file.writeText(json)
        UiSnapshot(json, file.absolutePath, nodeCount)
    }

    /**
     * Captures the selected display and saves a PNG in app-private cache.
     * Times out after 10 seconds and reports Android screenshot failures explicitly.
     * Secure content cannot be captured through this API.
     *
     * @param displayId The Android display ID; 0 selects the main display.
     * @return The exported PNG path and image dimensions.
     */
    suspend fun capture(displayId: Int): ScreenCapture {
        requireDisplay(displayId)
        val bitmap = withTimeout(10_000L.milliseconds) {
            suspendCancellableCoroutine<Bitmap> { continuation ->
                service.takeScreenshot(displayId, service.mainExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                            val buffer = result.hardwareBuffer
                            try {
                                if (!continuation.isActive) return
                                val hardware = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                    ?: error("Cannot decode screenshot buffer")
                                val copy = try {
                                    hardware.copy(Bitmap.Config.ARGB_8888, false)
                                        ?: error("Cannot copy screenshot")
                                } finally {
                                    hardware.recycle()
                                }
                                // Releases a successfully produced bitmap if the caller is cancelled.
                                continuation.resume(copy) { _, value, _ -> value.recycle() }
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            } finally {
                                buffer.close()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            val reason = when (errorCode) {
                                AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "Requests are too frequent; retry later"
                                AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "Invalid or inaccessible display"
                                AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "Screenshot accessibility access is unavailable"
                                6 -> "The display contains secure content"
                                else -> "Android screenshot error"
                            }
                            if (continuation.isActive) {
                                continuation.resumeWithException(IllegalStateException("$reason (code=$errorCode)"))
                            }
                        }
                    }
                )
            }
        }
        try {
            return withContext(Dispatchers.IO) {
                val file = File(service.cacheDir, "screenshot_$displayId.png")
                val bytes = ByteArrayOutputStream().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
                    output.toByteArray()
                }
                file.writeBytes(bytes)
                ScreenCapture(file.absolutePath, bitmap.width, bitmap.height)
            }
        } finally {
            bitmap.recycle()
        }
    }
}
