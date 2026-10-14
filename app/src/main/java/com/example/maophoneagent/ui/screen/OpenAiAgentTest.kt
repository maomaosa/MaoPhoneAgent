/*
 * File: OpenAiAgentTest.kt
 * Module: ui.screen
 *
 * Description:
 * Provides development-only OpenAI decision preview and multi-step task tests.
 * API keys remain in page memory and are never saved to preferences or source files.
 */
package com.example.maophoneagent.ui.screen

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.maophoneagent.agent.ai.OpenAiClient
import com.example.maophoneagent.agent.model.*
import com.example.maophoneagent.agent.planner.OpenAiAgentPlanner
import com.example.maophoneagent.agent.runtime.AgentRunner
import com.example.maophoneagent.agent.tool.PhoneToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Direct key entry is available only in debuggable builds for personal API testing. */
@Composable
fun OpenAiAgentTest(executor: PhoneToolExecutor, accessibilityConnected: Boolean) {
    val context = LocalContext.current
    if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
    val scope = rememberCoroutineScope()
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("gpt-4.1-mini") }
    var task by remember { mutableStateOf("Open Settings on the target display") }
    var displayInput by remember { mutableStateOf("1") }
    var limitInput by remember { mutableStateOf("6") }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("Ready") }
    var steps by remember { mutableStateOf<List<AgentStep>>(emptyList()) }
    val displayId = displayInput.toIntOrNull()
    val limit = limitInput.toIntOrNull()
    val valid = !busy && accessibilityConnected && apiKey.isNotBlank() && model.isNotBlank() &&
        task.isNotBlank() && displayId != null && displayId > 0 && limit != null && limit in 1..20

    fun start(previewOnly: Boolean) {
        if (!valid) return
        val id = displayId ?: return
        val maxCalls = limit ?: return
        val key = apiKey.trim()
        val selectedModel = model.trim()
        val description = task.trim()
        busy = true
        steps = emptyList()
        status = "Starting..."
        job = scope.launch {
            try {
                val planner = OpenAiAgentPlanner(OpenAiClient(key), selectedModel)
                val session = AgentSession(id)
                if (previewOnly) {
                    status = "Observing target display..."
                    val observation = executor.execute(session, AgentToolCall.Observe)
                    steps = listOf(AgentStep(AgentToolCall.Observe, observation))
                    check(observation.success) { observation.message }
                    status = "Reading installed packages..."
                    val apps = executor.execute(session, AgentToolCall.ListApps)
                    steps = steps + AgentStep(AgentToolCall.ListApps, apps)
                    status = "Requesting one OpenAI decision..."
                    val decision = planner.nextDecision(description, steps)
                    status = "Decision preview (not executed):\n$decision"
                } else {
                    val runner = AgentRunner(planner, executor::execute)
                    val result = runner.run(description, session, maxCalls, timeoutMs = 180_000L) { message, history ->
                        status = message
                        steps = history
                    }
                    steps = result.steps
                    status = "Completed=${result.completed}\n${result.message}"
                }
            } catch (e: CancellationException) {
                status = "Cancelled. Previously dispatched actions are not undone."
                throw e
            } catch (e: Exception) {
                status = "OpenAI test failed: ${e.message?.replace(key, "[redacted]")}"
            } finally {
                busy = false
                job = null
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text("OpenAI Agent Test", style = MaterialTheme.typography.titleMedium)
        Text("Preview reads the target screen and installed packages, sends both to OpenAI, then shows one decision. Run starts with Observe and ListApps before requesting AI decisions.")
        Text("The key stays in this page's memory. Enable accessibility and connect Shell UserService before running a task. Avoid other tool tests while running.")
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("OpenAI API Key") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy)
        OutlinedTextField(model, { model = it }, label = { Text("Model ID") }, singleLine = true, enabled = !busy)
        OutlinedTextField(task, { task = it }, label = { Text("Task") }, enabled = !busy)
        OutlinedTextField(displayInput, { displayInput = it }, label = { Text("Target Display ID (> 0)") },
            singleLine = true, enabled = !busy)
        OutlinedTextField(limitInput, { limitInput = it }, label = { Text("Maximum Tool Calls (1-20)") },
            singleLine = true, enabled = !busy)
        Button(enabled = valid, onClick = { start(true) }) { Text("Preview AI Decision") }
        Button(enabled = valid, onClick = { start(false) }) { Text("Run OpenAI Task") }
        OutlinedButton(enabled = busy, onClick = { status = "Cancelling..."; job?.cancel() }) { Text("Cancel") }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        SelectionContainer { Text(status) }
        Text("History: ${steps.size} tool calls")
        SelectionContainer {
            Text(steps.mapIndexed { index, step ->
                "${index + 1}. ${step.call}\nSuccess=${step.result.success}\n${step.result.message}"
            }.joinToString("\n\n"), modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)
                .verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
        }
    }
}
