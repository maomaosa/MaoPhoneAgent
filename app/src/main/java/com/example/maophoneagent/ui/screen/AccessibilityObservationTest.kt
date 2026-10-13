/*
 * File: AccessibilityObservationTest.kt
 * Module: ui.screen
 *
 * Description:
 * Provides display-specific UI snapshot and screenshot tests.
 * Supports delayed sampling, selectable JSON output, and an image preview.
 */
package com.example.maophoneagent.ui.screen

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.maophoneagent.ui.viewmodel.PhoneViewModel

/** Displays observation controls; actual sampling is managed by the ViewModel. */
@Composable
fun AccessibilityObservationTest(viewModel: PhoneViewModel, connected: Boolean) {
    val result by viewModel.observation.collectAsStateWithLifecycle()
    var displayInput by remember { mutableStateOf("0") }
    var delayInput by remember { mutableStateOf("3") }
    val displayId = displayInput.toIntOrNull()
    val delaySeconds = delayInput.toIntOrNull()
    val enabled = connected && !result.busy && displayId != null && displayId >= 0 &&
        delaySeconds != null && delaySeconds in 0..10

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider()
        Text("Accessibility Observation Test", style = MaterialTheme.typography.titleMedium)
        Text("Display 0 is the main screen. Enter the actual display ID to test a virtual screen.")
        Text("Use a delay to switch to the target app. UI and screenshot results are captured separately.")
        OutlinedTextField(
            value = displayInput,
            onValueChange = { displayInput = it },
            label = { Text("Observation Display ID") },
            singleLine = true
        )
        OutlinedTextField(
            value = delayInput,
            onValueChange = { delayInput = it },
            label = { Text("Delay in Seconds (0-10)") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = {
                if (displayId != null && delaySeconds != null) {
                    viewModel.observeScreen(displayId, delaySeconds, false)
                }
            }) { Text("Read UI") }
            Button(enabled = enabled, onClick = {
                if (displayId != null && delaySeconds != null) {
                    viewModel.observeScreen(displayId, delaySeconds, true)
                }
            }) { Text("Screenshot") }
        }
        if (result.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(result.status)
        if (result.json.isNotEmpty()) {
            Text("Last UI Snapshot")
            Text(result.jsonPath, style = MaterialTheme.typography.bodySmall)
            // Limits the result area height while keeping the complete JSON selectable.
            SelectionContainer {
                Text(
                    result.json,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        result.image?.let { image ->
            Text("Last Screenshot")
            Text(result.screenshotPath, style = MaterialTheme.typography.bodySmall)
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "Last accessibility screenshot",
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
            )
        }
    }
}
