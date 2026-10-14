/*
 * File: ShellVirtualDisplay.kt
 * Module: infrastructure.shizuku
 *
 * Description:
 * Owns one experimental virtual display and its rendering surface in the Shell process.
 * Drains image buffers and retains a bounded JPEG preview in memory.
 */
package com.example.maophoneagent.infrastructure.shizuku

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Build
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import org.json.JSONObject

/** Creating a display does not establish foreground, focus, or IME isolation. */
class ShellVirtualDisplay(private val context: Context) {
    companion object {
        // Hidden Android flags, matching DisplayManager and scrcpy's virtual display setup.
        private const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
        private const val SHOW_SYSTEM_DECORATIONS = 1 shl 9
        private const val TRUSTED = 1 shl 10
        private const val OWN_FOCUS = 1 shl 14
    }
    private var display: VirtualDisplay? = null
    @Volatile private var reader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var width = 0
    private var height = 0
    private var densityDpi = 0
    @Volatile private var preview = byteArrayOf()
    @Volatile private var previewError: String? = null
    private var flags = 0

    /** Returns the existing display, or creates one with a continuously drained Surface. */
    @Synchronized
    fun create(width: Int, height: Int, densityDpi: Int): String {
        require(width in 64..2048 && height in 64..2048) { "Display dimensions must be 64-2048 pixels" }
        require(densityDpi in 72..640) { "Density must be 72-640 dpi" }
        if (display != null) return info("Virtual display already exists; release it before changing dimensions.")

        // Attribute the system call to the actual Shell identity, not the ordinary app package.
        val shellContext = context.createPackageContext("com.android.shell", 0)
        val constructor = DisplayManager::class.java.getDeclaredConstructor(Context::class.java)
        constructor.isAccessible = true
        val manager = constructor.newInstance(shellContext)
        val worker = HandlerThread("MaoVirtualDisplay").apply { start() }
        var images: ImageReader? = null
        var created: VirtualDisplay? = null
        try {
            val handler = Handler(worker.looper)
            images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            images.setOnImageAvailableListener({ source ->
                // Close every acquired frame so rendering cannot stall on a full buffer queue.
                try {
                    source.acquireLatestImage()?.use { image ->
                        if (reader === source) {
                            val bytes = encodePreview(image)
                            // Ignore an old worker's output if this screen was released or replaced.
                            synchronized(this) {
                                if (reader === source) {
                                    preview = bytes
                                    previewError = null
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Closing the reader can invalidate a queued callback; report only live errors.
                    synchronized(this) {
                        if (reader === source) previewError = "${e.javaClass.simpleName}: ${e.message}"
                    }
                }
            }, handler)
            flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DESTROY_CONTENT_ON_REMOVAL
            if (Build.VERSION.SDK_INT >= 33) {
                // A trusted display can show its own system decorations and secondary launcher.
                flags = flags or TRUSTED or SHOW_SYSTEM_DECORATIONS
            }
            if (Build.VERSION.SDK_INT >= 34) {
                flags = flags or OWN_FOCUS
                // Available on some Android builds; own focus alone can still steal top focus.
                val noSteal = runCatching {
                    DisplayManager::class.java.getField("VIRTUAL_DISPLAY_FLAG_STEAL_TOP_FOCUS_DISABLED").getInt(null)
                }.getOrDefault(0)
                flags = flags or noSteal
            }
            // Retain ownership before creation can deliver its first (possibly only) frame.
            reader = images
            created = checkNotNull(manager.createVirtualDisplay(
                "MaoPhoneAgent-Test", width, height, densityDpi, images.surface, flags, null, handler
            )) { "Android returned no virtual display" }
            check(created.display.displayId > 0) { "Android returned an invalid secondary display ID" }
            this.width = width
            this.height = height
            this.densityDpi = densityDpi
            reader = images
            thread = worker
            display = created
            return info("Virtual display created in the Shell UserService.")
        } catch (e: Exception) {
            reader = null
            preview = byteArrayOf()
            created?.release()
            images?.close()
            worker.quitSafely()
            throw e
        }
    }

    /** Returns a structured status; null displayId means this service owns no display. */
    @Synchronized
    fun info(message: String = "Virtual display status."): String = JSONObject()
        .put("success", true)
        .put("displayId", display?.display?.displayId ?: JSONObject.NULL)
        .put("name", "MaoPhoneAgent-Test")
        .put("width", if (display != null) width else 0)
        .put("height", if (display != null) height else 0)
        .put("densityDpi", if (display != null) densityDpi else 0)
        .put("previewAvailable", preview.isNotEmpty())
        .put("previewError", previewError ?: JSONObject.NULL)
        .put("destroyContentOnRemoval", display != null && flags and DESTROY_CONTENT_ON_REMOVAL != 0)
        .put("message", message).toString()

    /** Returns the last rendered JPEG, or an empty array while no frame is available. */
    fun preview(): ByteArray = preview

    /** Removes row padding and limits preview resolution and payload before crossing Binder. */
    private fun encodePreview(image: android.media.Image): ByteArray {
        val plane = image.planes[0]
        check(plane.pixelStride == 4) { "Unexpected RGBA pixel stride: ${plane.pixelStride}" }
        val pixels = ByteBuffer.allocate(image.width * image.height * 4)
        val source = plane.buffer.duplicate()
        for (row in 0 until image.height) {
            source.limit(source.capacity())
            source.position(row * plane.rowStride)
            source.limit(row * plane.rowStride + image.width * 4)
            pixels.put(source)
        }
        pixels.flip()
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        var thumbnail: Bitmap? = null
        try {
            bitmap.copyPixelsFromBuffer(pixels)
            val scale = minOf(1f, 360f / image.width, 640f / image.height)
            val scaled = Bitmap.createScaledBitmap(bitmap,
                (image.width * scale).toInt().coerceAtLeast(1),
                (image.height * scale).toInt().coerceAtLeast(1), true)
            thumbnail = scaled
            val output = ByteArrayOutputStream()
            check(scaled.compress(Bitmap.CompressFormat.JPEG, 75, output)) { "JPEG preview encoding failed" }
            return output.toByteArray().also {
                check(it.size <= 256 * 1024) { "Preview exceeds the Binder payload limit" }
            }
        } finally {
            if (thumbnail !== bitmap) thumbnail?.recycle()
            bitmap.recycle()
        }
    }

    /** Releases only this service's display; screens created by scrcpy are unaffected. */
    @Synchronized
    fun release(): String {
        try {
            display?.release()
        } finally {
            display = null
            // Clear ownership before closing so pending frame callbacks cannot publish stale data.
            val oldReader = reader
            reader = null
            preview = byteArrayOf()
            previewError = null
            val worker = thread
            thread = null
            // Close on the frame worker after any in-flight pixel read finishes.
            val cleanup = Runnable {
                oldReader?.setOnImageAvailableListener(null, null)
                oldReader?.close()
                worker?.quitSafely()
            }
            if (worker == null || !Handler(worker.looper).post(cleanup)) cleanup.run()
        }
        return info("Virtual display released.")
    }
}
