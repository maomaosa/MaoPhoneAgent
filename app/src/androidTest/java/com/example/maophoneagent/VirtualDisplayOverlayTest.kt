package com.example.maophoneagent

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.Settings
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService
import com.example.maophoneagent.infrastructure.display.VirtualDisplayOverlayService
import com.example.maophoneagent.infrastructure.shizuku.ShizukuShellClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import rikka.shizuku.Shizuku
import java.io.File

/** Verifies actual window visibility, including Settings' protection against ordinary overlays. */
class VirtualDisplayOverlayTest {
    @Suppress("DEPRECATION")
    private fun visible(automation: UiAutomation, id: Int): Boolean {
        val windows = automation.windows
        try {
            return windows.any { window ->
                if (window.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) false
                else {
                    val root = window.root
                    try {
                        val matches = root?.findAccessibilityNodeInfosByText("Agent · $id").orEmpty()
                        try { matches.any { it.isVisibleToUser } }
                        finally { matches.forEach { it.recycle() } }
                    } finally { root?.recycle() }
                }
            }
        } finally { windows.forEach { it.recycle() } }
    }

    private suspend fun awaitVisible(automation: UiAutomation, id: Int) = withTimeout(10_000) {
        while (!visible(automation, id)) delay(100)
    }

    @Test fun settingsAfterOverlayStaysVisibleAndReleaseDoesNotMoveSettingsToMain() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        // Default UiAutomation suppresses other accessibility services, invalidating this test.
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        assumeTrue(Shizuku.pingBinder())
        assumeTrue(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val component = ComponentName(context, PhoneAccessibilityService::class.java)
        assumeTrue("Enable MaoPhoneAgent accessibility before this device test",
            enabled.split(':').any { ComponentName.unflattenFromString(it) == component })
        if (!PhoneAccessibilityService.connected.value) {
            // Instrumentation replaces the target process; rebind only this already-enabled service.
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            try {
                val others = enabled.split(':').filterNot { ComponentName.unflattenFromString(it) == component }.joinToString(":")
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others)
                delay(200)
            } finally {
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled)
                automation.dropShellPermissionIdentity()
            }
        }
        check(withTimeoutOrNull(15_000) { PhoneAccessibilityService.connected.first { it } } == true) {
            "The already-enabled accessibility service did not reconnect"
        }
        var connectionStatus = "Not started"
        val client = ShizukuShellClient(context) { connectionStatus = it }
        var activity: ActivityScenario<MainActivity>? = null
        try {
            client.connect()
            check(withTimeoutOrNull(15_000) { client.connected.first { it } } == true) {
                "Shizuku UserService connection timed out: $connectionStatus"
            }
            assertTrue(JSONObject(client.releaseVirtualDisplay()).getBoolean("success"))
            val created = JSONObject(client.createVirtualDisplay(720, 1280, 240))
            assertTrue(created.toString(), created.getBoolean("success"))
            assertTrue(created.getBoolean("destroyContentOnRemoval"))
            val id = created.getInt("displayId")
            val scenario = ActivityScenario.launch(MainActivity::class.java)
            activity = scenario
            scenario.onActivity { VirtualDisplayOverlayService.start(it, id) }
            awaitVisible(automation, id)

            // Reproduce the reported order: create, show overlay, then launch Settings.
            val output = client.launchAppForAgent(id, "com.android.settings")
            assertTrue(output, output.contains("Exit code: 0"))
            withTimeout(15_000) { VirtualDisplayOverlayService.frameReady.first { it } }
            delay(700)
            awaitVisible(automation, id)
            scenario.onActivity { assertTrue("Settings or the overlay stole main-screen focus", it.window.decorView.hasWindowFocus()) }

            val before = client.getVirtualDisplayPreview()
            client.swipeForAgent(id, 350, 1000, 350, 300, 500)
            withTimeout(8000) {
                while (client.getVirtualDisplayPreview().contentEquals(before)) delay(100)
            }
            awaitVisible(automation, id)
            automation.takeScreenshot()?.let { screenshot ->
                try {
                    File(context.externalCacheDir, "virtual-display-overlay-regression.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                } finally { screenshot.recycle() }
            }

            VirtualDisplayOverlayService.stop(context)
            withTimeout(5000) { VirtualDisplayOverlayService.running.first { !it } }
            assertEquals(id, JSONObject(client.getVirtualDisplayInfo()).getInt("displayId"))
            scenario.onActivity { VirtualDisplayOverlayService.start(it, id) }
            awaitVisible(automation, id)
            scenario.close()
            activity = null
            delay(500)
            awaitVisible(automation, id)

            // Remove the Settings display while a separate main-screen Activity is foreground.
            val foreground = ActivityScenario.launch(MainActivity::class.java)
            activity = foreground
            assertTrue(JSONObject(client.releaseVirtualDisplay()).getBoolean("success"))
            withTimeout(5000) { VirtualDisplayOverlayService.running.first { !it } }
            delay(500)
            foreground.onActivity { assertTrue("Removing the display moved Settings onto the main screen", it.window.decorView.hasWindowFocus()) }
            assertTrue(JSONObject(client.getVirtualDisplayInfo()).isNull("displayId"))
            assertNull(VirtualDisplayOverlayService.error.value)
        } finally {
            VirtualDisplayOverlayService.stop(context)
            withTimeoutOrNull(5000) { VirtualDisplayOverlayService.running.first { !it } }
            activity?.close()
            if (client.connected.value) client.releaseVirtualDisplay()
            client.close()
        }
    }
}
