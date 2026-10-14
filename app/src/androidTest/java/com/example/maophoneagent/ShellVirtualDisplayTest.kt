package com.example.maophoneagent

import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.example.maophoneagent.infrastructure.shizuku.ShizukuShellClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import rikka.shizuku.Shizuku

/** Requires a running, authorized Shizuku; verifies real creation without launching another app. */
class ShellVirtualDisplayTest {
    @Test fun settingsRendersIntoTheServicePreview() = runBlocking {
        assumeTrue(Shizuku.pingBinder())
        assumeTrue(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val client = ShizukuShellClient(context) { }
        try {
            client.connect()
            withTimeout(15_000) { client.connected.first { it } }
            assertTrue(JSONObject(client.releaseVirtualDisplay()).getBoolean("success"))
            val created = JSONObject(client.createVirtualDisplay(720, 1280, 240))
            assertTrue(created.toString(), created.getBoolean("success"))
            val output = client.launchAppForAgent(created.getInt("displayId"), "com.android.settings")
            assertTrue(output, output.contains("Exit code: 0"))
            withTimeout(15_000) {
                while (true) {
                    val bytes = client.getVirtualDisplayPreview()
                    val bitmap = if (bytes.isNotEmpty()) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                    if (bitmap != null) {
                        try {
                            assertTrue(bytes.size <= 256 * 1024)
                            assertEquals(360, bitmap.width)
                            assertEquals(640, bitmap.height)
                            val colors = mutableSetOf<Int>()
                            for (y in 0 until bitmap.height step 40) {
                                for (x in 0 until bitmap.width step 40) colors += bitmap.getPixel(x, y)
                            }
                            if (colors.size > 1) break
                        } finally { bitmap.recycle() }
                    }
                    kotlinx.coroutines.delay(200)
                }
            }
        } finally {
            if (client.connected.value) client.releaseVirtualDisplay()
            client.close()
        }
    }

    @Test fun createsReusesAndReleasesARealDisplay() = runBlocking {
        assumeTrue("Start Shizuku before this device test", Shizuku.pingBinder())
        assumeTrue("Authorize MaoPhoneAgent in Shizuku before this device test",
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(DisplayManager::class.java)
        val client = ShizukuShellClient(context) { }
        try {
            client.connect()
            withTimeout(15_000) { client.connected.first { it } }
            assertTrue(JSONObject(client.releaseVirtualDisplay()).getBoolean("success"))
            val created = JSONObject(client.createVirtualDisplay(720, 1280, 240))
            assertTrue(created.toString(), created.getBoolean("success"))
            val id = created.getInt("displayId")
            assertTrue(id > 0)
            assertNotNull(manager.getDisplay(id))
            assertEquals(720, created.getInt("width"))
            assertEquals(id, JSONObject(client.getVirtualDisplayInfo()).getInt("displayId"))
            assertEquals(id, JSONObject(client.createVirtualDisplay(720, 1280, 240)).getInt("displayId"))
            val invalid = JSONObject(client.createVirtualDisplay(0, 1280, 240))
            assertFalse(invalid.getBoolean("success"))
            assertEquals(id, JSONObject(client.getVirtualDisplayInfo()).getInt("displayId"))
            val released = JSONObject(client.releaseVirtualDisplay())
            assertTrue(released.getBoolean("success"))
            assertTrue(released.isNull("displayId"))
            assertTrue(JSONObject(client.getVirtualDisplayInfo()).isNull("displayId"))
            withTimeout(5000) {
                while (manager.getDisplay(id) != null) kotlinx.coroutines.delay(50)
            }
        } finally {
            if (client.connected.value) client.releaseVirtualDisplay()
            client.close()
        }
    }
}
