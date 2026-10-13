/*
 * File: AgentToolTest.kt
 * Module: ui.screen
 *
 * Description:
 * Tests Observe and Tap through the same executor used by future AI tool calls.
 * Displays structured tool results, selectable UI JSON, and screenshot previews.
 */
package com.example.maophoneagent.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.example.maophoneagent.agent.model.AgentSession
import com.example.maophoneagent.agent.model.AgentToolCall
import com.example.maophoneagent.agent.model.AgentToolResult
import com.example.maophoneagent.agent.tool.PhoneToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Uses one executor for both tools; the test UI never dispatches shell actions directly. */
@Composable
fun AgentToolTest(executor: PhoneToolExecutor, accessibilityConnected: Boolean) {
    val scope = rememberCoroutineScope()
    var displayInput by remember { mutableStateOf("1") }
    var xInput by remember { mutableStateOf("350") }
    var yInput by remember { mutableStateOf("500") }
    var delayInput by remember { mutableStateOf("0") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var result by remember { mutableStateOf<AgentToolResult?>(null) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    val displayId = displayInput.toIntOrNull()
    val x = xInput.toIntOrNull()
    val y = yInput.toIntOrNull()
    val seconds = delayInput.toIntOrNull()
    val valid = !busy && displayId != null && displayId > 0 && seconds != null && seconds in 0..10

    fun runTool(call: AgentToolCall) {
        val id = displayId ?: return
        val wait = seconds ?: return
        if (!valid) return
        busy = true
        result = null
        image = null
        scope.launch {
            try {
                status = "Waiting $wait seconds..."
                delay(wait * 1000L)
                status = "Executing Agent tool on display=$id..."
                val response = executor.execute(AgentSession(id), call)
                result = response
                status = "Tool=${response.toolName}, Success=${response.success}"
                response.screenshotPath?.let { path ->
                    image = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
                    if (image == null) status += "\nScreenshot preview could not be loaded"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                status = "Agent tool test failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text("Agent Tool Test", style = MaterialTheme.typography.titleMedium)
        Text("Observe requires accessibility. Tap requires Shizuku permission and a connected Shell UserService.")
        Text("Enter an existing virtual display ID greater than 0. Tap success reports command completion; verify the app's response on that display.")
        OutlinedTextField(
            value = displayInput, onValueChange = { displayInput = it },
            label = { Text("Agent Display ID") }, singleLine = true, enabled = !busy
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = xInput, onValueChange = { xInput = it },
                label = { Text("Tap X") }, modifier = Modifier.weight(1f),
                singleLine = true, enabled = !busy
            )
            OutlinedTextField(
                value = yInput, onValueChange = { yInput = it },
                label = { Text("Tap Y") }, modifier = Modifier.weight(1f),
                singleLine = true, enabled = !busy
            )
        }
        OutlinedTextField(
            value = delayInput, onValueChange = { delayInput = it },
            label = { Text("Delay in Seconds (0-10)") }, singleLine = true, enabled = !busy
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = valid && accessibilityConnected,
                onClick = { runTool(AgentToolCall.Observe) }) { Text("Agent Observe") }
            Button(enabled = valid && x != null && x >= 0 && y != null && y >= 0,
                onClick = {
                    if (x != null && y != null) runTool(AgentToolCall.Tap(x, y))
                }) { Text("Agent Tap") }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status)
        result?.let { response ->
            SelectionContainer { Text(response.message) }
            response.uiJson?.let { json ->
                Text("UI JSON: ${response.nodeCount} nodes")
                SelectionContainer {
                    Text(json, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState()))
                }
            }
            response.screenshotPath?.let { path -> Text(path, style = MaterialTheme.typography.bodySmall) }
        }
        image?.let { bitmap ->
            Image(bitmap.asImageBitmap(), contentDescription = "Agent observation screenshot",
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp))
        }
    }
}
