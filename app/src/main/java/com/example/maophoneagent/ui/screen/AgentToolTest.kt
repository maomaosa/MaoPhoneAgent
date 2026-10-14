/*
 * File: AgentToolTest.kt
 * Module: ui.screen
 *
 * Description:
 * Tests observations, actions, app launches, and package listing through the AI tool executor.
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

/** Uses one executor for all tools; the test UI never dispatches shell actions directly. */
@Composable
fun AgentToolTest(executor: PhoneToolExecutor, accessibilityConnected: Boolean) {
    val scope = rememberCoroutineScope()
    var displayInput by remember { mutableStateOf("1") }
    var xInput by remember { mutableStateOf("350") }
    var yInput by remember { mutableStateOf("500") }
    var startXInput by remember { mutableStateOf("350") }
    var startYInput by remember { mutableStateOf("950") }
    var endXInput by remember { mutableStateOf("350") }
    var endYInput by remember { mutableStateOf("300") }
    var durationInput by remember { mutableStateOf("500") }
    var packageInput by remember { mutableStateOf("com.android.settings") }
    var textInput by remember { mutableStateOf("猫咪") }
    var delayInput by remember { mutableStateOf("0") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Ready") }
    var result by remember { mutableStateOf<AgentToolResult?>(null) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    val displayId = displayInput.toIntOrNull()
    val x = xInput.toIntOrNull()
    val y = yInput.toIntOrNull()
    val startX = startXInput.toIntOrNull()
    val startY = startYInput.toIntOrNull()
    val endX = endXInput.toIntOrNull()
    val endY = endYInput.toIntOrNull()
    val durationMs = durationInput.toIntOrNull()
    val seconds = delayInput.toIntOrNull()
    val packageName = packageInput.trim()
    val packagePattern = remember {
        Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")
    }
    val validPackage = packagePattern.matches(packageName)
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
        Text("Observe and text input require accessibility. Gestures, navigation, app launches, and ListApps require Shizuku permission and a connected Shell UserService.")
        Text("Enter an existing virtual display ID greater than 0. Action success reports command completion; verify the app's response on that display.")
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
        // Swipe uses its own coordinates and the same session display and execution delay.
        Text("Agent Swipe", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = startXInput, onValueChange = { startXInput = it },
                label = { Text("Start X") }, modifier = Modifier.weight(1f),
                singleLine = true, enabled = !busy
            )
            OutlinedTextField(
                value = startYInput, onValueChange = { startYInput = it },
                label = { Text("Start Y") }, modifier = Modifier.weight(1f),
                singleLine = true, enabled = !busy
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = endXInput, onValueChange = { endXInput = it },
                label = { Text("End X") }, modifier = Modifier.weight(1f),
                singleLine = true, enabled = !busy
            )
            OutlinedTextField(
                value = endYInput, onValueChange = { endYInput = it },
                label = { Text("End Y") }, modifier = Modifier.weight(1f),
                singleLine = true, enabled = !busy
            )
        }
        OutlinedTextField(
            value = durationInput, onValueChange = { durationInput = it },
            label = { Text("Swipe Duration in ms (1-10000)") }, singleLine = true, enabled = !busy
        )
        Button(
            enabled = valid && startX != null && startX >= 0 && startY != null && startY >= 0 &&
                endX != null && endX >= 0 && endY != null && endY >= 0 &&
                durationMs != null && durationMs in 1..10000,
            onClick = {
                if (startX != null && startY != null && endX != null && endY != null && durationMs != null) {
                    runTool(AgentToolCall.Swipe(startX, startY, endX, endY, durationMs))
                }
            }
        ) { Text("Agent Swipe") }

        // Text input replaces the focused field on the selected display through accessibility.
        Text("Agent Text Input", style = MaterialTheme.typography.titleSmall)
        Text("Tap an editable field on the target display first. Input replaces its entire value; empty text clears it. Observe after input and submission to verify the result.")
        OutlinedTextField(
            value = textInput, onValueChange = { textInput = it },
            label = { Text("Text to Enter") }, enabled = !busy,
            isError = textInput.length > 2000,
            supportingText = { Text("Supports Chinese and spaces; maximum 2000 characters.") }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = valid && accessibilityConnected && textInput.length <= 2000,
                onClick = { runTool(AgentToolCall.InputText(textInput)) }) { Text("Agent Input Text") }
            Button(enabled = valid && accessibilityConnected,
                onClick = { runTool(AgentToolCall.SubmitInput) }) { Text("Agent Submit Input") }
        }

        // Navigation requests do not depend on the tap or swipe coordinate inputs.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = valid, onClick = { runTool(AgentToolCall.Home) }) { Text("Agent Home") }
            Button(enabled = valid, onClick = { runTool(AgentToolCall.Back) }) { Text("Agent Back") }
        }

        // The package is a tool argument; the target display remains selected by the session.
        Text("Agent App Launch", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = packageInput, onValueChange = { packageInput = it },
            label = { Text("App Package Name") }, singleLine = true, enabled = !busy,
            isError = packageInput.isNotEmpty() && !validPackage,
            supportingText = { Text("Example: com.android.settings") }
        )
        Button(enabled = valid && validPackage,
            onClick = { runTool(AgentToolCall.LaunchApp(packageName)) }) {
            Text("Agent Launch App")
        }

        // Lists packages through the Agent route; no package or coordinate input is needed.
        Text("Agent App List", style = MaterialTheme.typography.titleSmall)
        Text("Lists device-wide package names, including apps without launcher activities. A valid Agent session display is still required.")
        Button(enabled = valid, onClick = { runTool(AgentToolCall.ListApps) }) {
            Text("Agent List Apps")
        }

        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status)
        result?.let { response ->
            SelectionContainer {
                Text(response.message,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()))
            }
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
