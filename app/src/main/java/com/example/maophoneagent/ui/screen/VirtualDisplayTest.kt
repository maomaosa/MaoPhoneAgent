/*
 * File: VirtualDisplayTest.kt
 * Module: ui.screen
 *
 * Description:
 * Tests creation, querying, and release of a virtual display owned by the Shell service.
 * Displays the returned ID and checks whether the app can enumerate that display.
 */
package com.example.maophoneagent.ui.screen

import android.hardware.display.DisplayManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.maophoneagent.infrastructure.shizuku.ShizukuShellClient
import com.example.maophoneagent.infrastructure.display.VirtualDisplayOverlayService
import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Previews the owned display; opening Settings is a separate explicit test action. */
@Composable
fun VirtualDisplayTest(client: ShizukuShellClient) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val connected by client.connected.collectAsStateWithLifecycle()
    val manager = LocalContext.current.getSystemService(DisplayManager::class.java)
    var busy by remember { mutableStateOf(false) }
    var displayId by remember { mutableStateOf<Int?>(null) }
    var status by remember { mutableStateOf("Connect Shell UserService before creating a display.") }
    var showPreview by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var previewStatus by remember { mutableStateOf("Create or query a display to start the preview.") }
    val accessibilityConnected by PhoneAccessibilityService.connected.collectAsStateWithLifecycle()
    val floating by VirtualDisplayOverlayService.running.collectAsStateWithLifecycle()
    val overlayError by VirtualDisplayOverlayService.error.collectAsStateWithLifecycle()
    var askedNotificationPermission by remember { mutableStateOf(false) }

    fun openFloatingPreview() {
        val id = displayId ?: return
        try {
            VirtualDisplayOverlayService.start(context, id)
        } catch (e: Exception) {
            status = "Floating preview failed: ${e.message}"
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        // Denial still permits the foreground service; Android controls notification visibility.
        openFloatingPreview()
    }

    fun runOperation(operation: suspend () -> String) {
        if (busy || !connected) return
        busy = true
        scope.launch {
            try {
                val response = JSONObject(operation())
                if (response.getBoolean("success")) {
                    displayId = if (response.isNull("displayId")) null else response.getInt("displayId")
                    status = response.toString(2)
                    displayId?.let { id ->
                        status += "\nVisible to app DisplayManager: ${manager.getDisplay(id) != null}"
                    }
                } else {
                    status = response.optString("message", "Virtual display request failed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "${e.javaClass.simpleName}: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun runAction(operation: suspend (Int) -> String) {
        val id = displayId ?: return
        if (!connected || busy) return
        busy = true
        scope.launch {
            try {
                status = operation(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Display action failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    LaunchedEffect(connected) {
        if (!connected) {
            displayId = null
            status = "Shell UserService is disconnected. Reconnect and query its display status."
        }
    }

    // Polls a small in-memory JPEG; no preview touches are forwarded to the virtual display.
    LaunchedEffect(connected, displayId, showPreview) {
        preview = null
        if (!connected || displayId == null || !showPreview) {
            previewStatus = "Preview is inactive."
            return@LaunchedEffect
        }
        var previous = byteArrayOf()
        while (isActive) {
            try {
                val bytes = client.getVirtualDisplayPreview()
                if (bytes.isEmpty()) {
                    preview = null
                    previous = byteArrayOf()
                    previewStatus = "No app content yet. Open Settings or another app on this display."
                } else if (!bytes.contentEquals(previous)) {
                    preview = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                    previous = bytes
                    previewStatus = if (preview != null) "Live read-only preview · up to 10 updates per second."
                        else "Preview decoding failed."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                previewStatus = "Preview failed: ${e.message}"
            }
            delay(100)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text("Internal Virtual Display Test", style = MaterialTheme.typography.titleMedium)
        Text("Creates one 720 x 1280 display at 240 dpi inside the phone. No scrcpy connection is required.")
        Text("Display ID: ${displayId?.toString() ?: "None"}", style = MaterialTheme.typography.titleSmall)
        Button(enabled = connected && !busy,
            onClick = { runOperation { client.createVirtualDisplay(720, 1280, 240) } }) {
            Text("Create Internal Display")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = connected && !busy,
                onClick = { runOperation { client.getVirtualDisplayInfo() } }) { Text("Query Display ID") }
            OutlinedButton(enabled = connected && !busy,
                onClick = {
                    VirtualDisplayOverlayService.stop(context)
                    runOperation { client.releaseVirtualDisplay() }
                }) { Text("Release Display") }
        }
        Button(enabled = connected && displayId != null && !busy,
            onClick = { runAction { client.launchAppForAgent(it, "com.android.settings") } }) {
            Text("Open Settings on This Display")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = connected && displayId != null && !busy,
                onClick = { runAction { client.swipeForAgent(it, 350, 1000, 350, 300, 500) } }) { Text("Swipe Up") }
            OutlinedButton(enabled = connected && displayId != null && !busy,
                onClick = { runAction { client.homeForAgent(it) } }) { Text("Display Home") }
        }
        if (!accessibilityConnected) Text("Enable MaoPhoneAgent accessibility to show the floating preview.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = connected && displayId != null && accessibilityConnected && !busy, onClick = {
                if (!askedNotificationPermission && Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    askedNotificationPermission = true
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else openFloatingPreview()
            }) { Text("Open Floating Preview") }
            OutlinedButton(enabled = floating,
                onClick = { VirtualDisplayOverlayService.stop(context) }) { Text("Close Preview") }
        }
        overlayError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("Drag the floating title, use − to collapse, or × to close. Closing the preview does not release the internal display.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = showPreview, onCheckedChange = { showPreview = it })
            Text("Show Display Preview")
        }
        preview?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = "Internal virtual display preview",
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp))
        }
        Text(previewStatus, style = MaterialTheme.typography.bodySmall)
        Text("The preview displays the phone's internal screen inside this test page. Focus, keyboard isolation, and Agent observation still require verification. The display is removed when its UserService exits.")
        if (busy) LinearProgressIndicator()
        SelectionContainer { Text(status, style = MaterialTheme.typography.bodySmall) }
    }
}
