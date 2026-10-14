/*
 * File: VirtualDisplayOverlayService.kt
 * Module: infrastructure.display
 *
 * Description:
 * Displays the Shell-owned virtual screen in a draggable, non-focusable overlay.
 * A foreground service keeps preview polling independent of the test page lifecycle.
 */
package com.example.maophoneagent.infrastructure.display

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.maophoneagent.infrastructure.shizuku.ShizukuShellClient
import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/** An optional read-only preview; it does not run Agent tasks or forward screen touches. */
class VirtualDisplayOverlayService : Service() {
    companion object {
        private const val CHANNEL = "virtual_display_preview"
        private const val NOTIFICATION_ID = 42
        private const val STOP = "com.example.maophoneagent.STOP_PREVIEW"
        private val _running = MutableStateFlow(false)
        val running = _running.asStateFlow()
        private val _frameReady = MutableStateFlow(false)
        val frameReady = _frameReady.asStateFlow()
        private val _error = MutableStateFlow<String?>(null)
        val error = _error.asStateFlow()

        /** Call from a visible activity after the user enables the accessibility service. */
        fun start(context: Context, displayId: Int) {
            require(displayId > 0)
            check(PhoneAccessibilityService.currentInstance() != null) { "Enable MaoPhoneAgent accessibility first" }
            _error.value = null
            // Reserve ownership before service startup so Activity destruction cannot remove it.
            _running.value = true
            try {
                context.startForegroundService(Intent(context, VirtualDisplayOverlayService::class.java)
                    .putExtra("displayId", displayId))
            } catch (e: Exception) {
                _running.value = false
                throw e
            }
        }

        fun stop(context: Context) {
            _error.value = null
            context.stopService(Intent(context, VirtualDisplayOverlayService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var client: ShizukuShellClient
    private lateinit var windows: WindowManager
    private var root: LinearLayout? = null
    private lateinit var image: ImageView
    private lateinit var label: TextView
    private lateinit var title: TextView
    private lateinit var layout: WindowManager.LayoutParams
    private var poll: Job? = null
    private var overlayOwner: PhoneAccessibilityService? = null

    override fun onCreate() {
        super.onCreate()
        client = ShizukuShellClient(applicationContext) { }
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Virtual display preview", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val id = intent?.getIntExtra("displayId", -1) ?: -1
        if (id <= 0 || PhoneAccessibilityService.currentInstance() == null) {
            _error.value = "A valid display and connected accessibility service are required."
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val close = PendingIntent.getService(this, 0,
                Intent(this, javaClass).setAction(STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("MaoPhoneAgent preview")
                .setContentText("Display $id · read-only floating preview")
                .setOngoing(true).setOnlyAlertOnce(true)
                .addAction(Notification.Action.Builder(null, "Close preview", close).build()).build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            if (root == null) createWindow()
            title.text = "Agent · $id"
            _running.value = true
            poll?.cancel()
            _frameReady.value = false
            poll = scope.launch {
                try {
                    client.connect()
                    withTimeout(15_000) { client.connected.first { it } }
                    var previous = byteArrayOf()
                    while (isActive) {
                        check(PhoneAccessibilityService.currentInstance() === overlayOwner) { "Accessibility disconnected; reconnect it before reopening the preview" }
                        val info = JSONObject(client.getVirtualDisplayInfo())
                        if (info.optBoolean("success") && info.isNull("displayId")) {
                            // An intentional screen release is a normal preview shutdown.
                            stopSelf()
                            return@launch
                        }
                        check(info.optBoolean("success") && info.optInt("displayId", -1) == id) {
                            "The selected internal display is no longer available"
                        }
                        val bytes = client.getVirtualDisplayPreview()
                        if (bytes.isEmpty()) {
                            image.setImageDrawable(null)
                            previous = byteArrayOf()
                            _frameReady.value = false
                            label.text = info.optString("previewError").takeUnless { it == "null" || it.isBlank() }
                                ?: "No app content yet · open an app"
                        } else if (!bytes.contentEquals(previous)) {
                            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                            checkNotNull(bitmap) { "Cannot decode the preview" }
                            image.setImageBitmap(bitmap)
                            previous = bytes
                            _frameReady.value = true
                            label.text = "Read-only · drag the title"
                        }
                        delay(100)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _error.value = e.message ?: "Floating preview failed"
                    stopSelf()
                }
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Cannot show floating preview"
            stopSelf()
        }
        return START_NOT_STICKY
    }

    /** Only the title can drag; image touches are consumed without sending input to either app. */
    private fun createWindow() {
        // Accessibility supplies the window token for TYPE_ACCESSIBILITY_OVERLAY.
        // A normal TYPE_APPLICATION_OVERLAY is hidden when Settings requests overlay protection.
        overlayOwner = checkNotNull(PhoneAccessibilityService.currentInstance())
        windows = overlayOwner!!.getSystemService(WindowManager::class.java)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(Color.rgb(30, 30, 30)); cornerRadius = dp(10).toFloat() }
            setPadding(dp(6), dp(6), dp(6), dp(6))
            elevation = dp(8).toFloat()
        }
        root = panel
        val bar = LinearLayout(this)
        title = text("Agent").apply { contentDescription = "Drag floating preview" }
        bar.addView(title, LinearLayout.LayoutParams(0, dp(36), 1f))
        val collapse = text("−").apply {
            contentDescription = "Collapse or expand floating preview"
            setOnClickListener {
                val hidden = image.visibility == View.VISIBLE
                image.visibility = if (hidden) View.GONE else View.VISIBLE
                label.visibility = image.visibility
                text = if (hidden) "+" else "−"
            }
        }
        bar.addView(collapse, LinearLayout.LayoutParams(dp(30), dp(36)))
        bar.addView(text("×").apply {
            contentDescription = "Close floating preview"
            setOnClickListener { stopSelf() }
        }, LinearLayout.LayoutParams(dp(30), dp(36)))
        panel.addView(bar)
        image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
            contentDescription = "Floating virtual display preview"
            setOnTouchListener { _, _ -> true }
        }
        panel.addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(280)))
        label = text("Connecting…").apply { textSize = 10f }
        panel.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32)))
        val bounds = windows.currentWindowMetrics.bounds
        layout = WindowManager.LayoutParams(dp(180).coerceAtMost(bounds.width()),
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            // Keep the test page's left-side buttons accessible by default.
            x = (bounds.width() - width - dp(12)).coerceAtLeast(0)
            y = (bounds.height() - dp(460)).coerceAtLeast(0)
        }
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        title.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layout.x; startY = layout.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val area = windows.currentWindowMetrics.bounds
                    layout.x = (startX + event.rawX - touchX).toInt().coerceIn(0, (area.width() - panel.width).coerceAtLeast(0))
                    layout.y = (startY + event.rawY - touchY).toInt().coerceIn(0, (area.height() - panel.height).coerceAtLeast(0))
                    windows.updateViewLayout(panel, layout)
                    true
                }
                MotionEvent.ACTION_UP -> { title.performClick(); true }
                else -> false
            }
        }
        windows.addView(panel, layout)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun text(value: String) = TextView(this).apply {
        text = value
        textSize = 12f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
    }

    override fun onDestroy() {
        scope.cancel()
        root?.let {
            // Accessibility disconnection may already have removed its window token.
            try { if (it.isAttachedToWindow) windows.removeViewImmediate(it) }
            catch (_: IllegalArgumentException) { }
        }
        root = null
        // Closing a preview must not remove the screen owned by the test page.
        client.close(removeService = false)
        _running.value = false
        _frameReady.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
