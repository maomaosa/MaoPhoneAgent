/*
 * File: AgentRunnerTest.kt
 * Module: ui.screen
 *
 * Description:
 * Runs a fixed multi-step task and displays planning progress and tool history.
 * The caller owns the runner; this component owns the cancellable test job.
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
import com.example.maophoneagent.agent.model.AgentStep
import com.example.maophoneagent.agent.runtime.AgentRunner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tests task orchestration independently of the single-tool controls. */
@Composable
fun AgentRunnerTest(runner: AgentRunner, accessibilityConnected: Boolean) {
    val scope = rememberCoroutineScope()
    var displayInput by remember { mutableStateOf("1") }
    var taskInput by remember { mutableStateOf("Open Settings on the virtual display and observe the screen") }
    var limitInput by remember { mutableStateOf("10") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var steps by remember { mutableStateOf<List<AgentStep>>(emptyList()) }
    var image by remember { mutableStateOf<Bitmap?>(null) }
    val displayId = displayInput.toIntOrNull()
    val limit = limitInput.toIntOrNull()
    val enabled = !busy && accessibilityConnected && displayId != null && displayId > 0 &&
        limit != null && limit in 1..100 && taskInput.isNotBlank()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text("Agent Runner Test", style = MaterialTheme.typography.titleMedium)
        Text("Fixed script: Observe → Launch Settings → Observe → Complete. Task text is recorded as input but does not change this script. No AI API is called.")
        Text("Enable accessibility and connect Shell UserService first. Use an existing display ID greater than 0, and avoid manual tool calls while the task runs.")
        OutlinedTextField(taskInput, { taskInput = it }, label = { Text("Task") }, enabled = !busy)
        OutlinedTextField(displayInput, { displayInput = it }, label = { Text("Runner Display ID") },
            enabled = !busy, singleLine = true)
        OutlinedTextField(limitInput, { limitInput = it }, label = { Text("Maximum Tool Calls (1-100)") },
            enabled = !busy, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = {
                val id = displayId ?: return@Button
                val maxCalls = limit ?: return@Button
                busy = true
                steps = emptyList()
                image = null
                status = "Starting..."
                job = scope.launch {
                    try {
                        val outcome = runner.run(taskInput.trim(), AgentSession(id), maxCalls) { message, history ->
                            status = message
                            steps = history
                        }
                        steps = outcome.steps
                        status = "Completed=${outcome.completed}\n${outcome.message}"
                        outcome.steps.lastOrNull()?.result?.screenshotPath?.let { path ->
                            image = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
                        }
                    } catch (e: CancellationException) {
                        status = "Task cancelled. Already executed actions are not undone."
                        throw e
                    } catch (e: Exception) {
                        status = "Test failed: ${e.message}"
                    } finally {
                        busy = false
                        job = null
                    }
                }
            }) { Text("Run Agent Task") }
            OutlinedButton(enabled = busy, onClick = {
                status = "Cancelling..."
                job?.cancel()
            }) { Text("Cancel") }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(status)
        Text("Execution History: ${steps.size} tool calls")
        // Shows each result without expanding large UI JSON into the history area.
        steps.forEachIndexed { index, step ->
            Text("${index + 1}. ${step.call}", style = MaterialTheme.typography.titleSmall)
            SelectionContainer {
                Text("Success=${step.result.success}\n${step.result.message}",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        image?.let { bitmap ->
            Text("Final Observation Screenshot")
            Image(bitmap.asImageBitmap(), "Final task observation",
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp))
        }
    }
}
