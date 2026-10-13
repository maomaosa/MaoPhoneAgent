/*
 * File: TestScreen.kt
 * Module: ui.screen
 *
 * Description:
 * Provides a test screen for phone interactions and Shizuku shell operations.
 * Allows users to enter tap coordinates, request a predefined swipe,
 * and trigger Android Back and Home navigation actions.
 * Displays accessibility service connection status and provides
 * a shortcut to system settings for enabling the service.
 *
 * Delegates action requests to PhoneViewModel and displays
 * the current execution status observed with lifecycle awareness.
 * Phone action controls are enabled only while the service is connected.
 */

package com.example.maophoneagent.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.maophoneagent.ui.viewmodel.PhoneViewModel
import android.content.Intent
import android.provider.Settings
import android.content.ActivityNotFoundException
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService

/**
 * Displays test controls for phone actions, shell operations, and their execution status.
 * Observes service connectivity to update status and action availability.
 *
 * @param viewModel The ViewModel used to request actions and observe status.
 */
@Composable
fun TestScreen(
    viewModel: PhoneViewModel,

    // Existing Shizuku parameters.
    shizukuStatus: String,
    onRequestShizukuPermission: () -> Unit,
    onRefreshShizukuStatus: () -> Unit,

    // New Shell UserService parameters.
    shellTestStatus: String,
    onConnectShell: () -> Unit,
    onRunShellId: () -> Unit,
    onTapVirtualDisplay: (Int, Int, Int) -> Unit,
    onSwipeVirtualDisplay: (Int) -> Unit,
    onBackVirtualDisplay: (Int) -> Unit,
    onHomeVirtualDisplay: (Int) -> Unit,
    // Requests an installed package on the selected virtual display.
    onLaunchVirtualApp: (Int, String) -> Unit,
    // Requests installed package names from the shell UserService.
    onListInstalledPackages: () -> Unit
) {

    // Observes the current operation status while the lifecycle is active.
    val status by viewModel.status.collectAsStateWithLifecycle()

    // Keeps coordinate input across recompositions, with initial values in pixels.
    var x by remember { mutableStateOf("500") }
    var y by remember { mutableStateOf("800") }

    // Uses the current Compose context to launch the system settings activity.
    val context = LocalContext.current
    // Observes the actual service connection, rather than only a settings preference.
    // Changes update the connection label and enable or disable phone action buttons.
    val accessibilityConnected by PhoneAccessibilityService.connected.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "MaoPhoneAgent",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Phone Controller Test")

        HorizontalDivider()

        // Shows service availability before the phone action controls.
        Text(
            text = "Accessibility Service",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = if (accessibilityConnected) {
                "Connected"
            } else {
                "Disconnected"
            },
            color = if (accessibilityConnected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        // Keeps the settings shortcut available even when the service is disconnected.
        // The user enables the service in Android settings; this button only opens the page.
        Button(
            onClick = {
                try {
                    val intent = Intent(
                        Settings.ACTION_ACCESSIBILITY_SETTINGS
                    )

                    context.startActivity(intent)

                } catch (e: ActivityNotFoundException) {
                    // Some devices may not provide an activity for this settings page.
                    // Currently ignores the failure without displaying a UI error.
                }
            }
        ) {
            Text("Open Accessibility Settings")
        }

        HorizontalDivider()

        // Provides editable screen coordinates for tap requests.
        Text("Tap Coordinates")

        OutlinedTextField(
            value = x,
            onValueChange = { x = it },
            label = { Text("X") },
            singleLine = true
        )

        OutlinedTextField(
            value = y,
            onValueChange = { y = it },
            label = { Text("Y") },
            singleLine = true
        )


        // Allows tap requests only while the accessibility service is connected.
        Button(
            enabled = accessibilityConnected,
            onClick = {
                // Requests a tap only when both inputs can be parsed as integers.
                val tapX = x.toIntOrNull()
                val tapY = y.toIntOrNull()

                // Invalid integer input is ignored; coordinate validation is handled by the service layer.
                if (tapX != null && tapY != null) {
                    viewModel.tap(tapX, tapY)
                }
            }
        ) {
            Text("Execute Tap")
        }

        HorizontalDivider()

        // Provides a predefined swipe for testing the connected service.
        Button(
            enabled = accessibilityConnected,
            onClick = {
                // Requests a downward swipe from (500, 50) to (500, 900) over 500 ms.
                viewModel.swipe(
                    500, 50,
                    500, 900,
                    500L
                )
            }
        ) {
            Text("Execute Swipe")
        }

        // Groups the Android system navigation controls, enabled only while connected.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                enabled = accessibilityConnected,
                onClick = { viewModel.back() }
            ) {
                Text("Back")
            }

            Button(
                enabled = accessibilityConnected,
                onClick = { viewModel.home() }
            ) {
                Text("Home")
            }
        }

        HorizontalDivider()

        // Displays the latest operation status provided by the ViewModel.
        Text("Execution Status")

        // Execution Status
        Text(
            text = status,
            color = MaterialTheme.colorScheme.primary
        )

        // ==========================================
        // Shizuku Service Test
        // ==========================================

        HorizontalDivider()

        Text(
            text = "Shizuku Service",
            style = MaterialTheme.typography.titleMedium
        )

        // Display Shizuku connection status.
        Text(
            text = shizukuStatus,
            color = if (
                shizukuStatus.startsWith("Shizuku connected (UID:")
            ) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            }
        )

        // Request permission through MainActivity.
        Button(
            onClick = onRequestShizukuPermission
        ) {
            Text("Request Shizuku Permission")
        }

        // Refresh service connection status.
        OutlinedButton(
            onClick = onRefreshShizukuStatus
        ) {
            Text("Refresh Shizuku Status")
        }


        // ==========================================
        // Shell UserService Test
        // ==========================================

        HorizontalDivider()

        Text(
            text = "Shell UserService Test",
            style = MaterialTheme.typography.titleMedium
        )

        // Display remote service or command status.
        Text(
            text = shellTestStatus,
            color = MaterialTheme.colorScheme.primary
        )

        // Connect to the Shizuku UserService.
        Button(
            onClick = onConnectShell
        ) {
            Text("Connect Shell UserService")
        }

        // Execute the diagnostic command.
        OutlinedButton(
            onClick = onRunShellId
        ) {
            Text("Execute Shell ID")
        }


        // ==========================================
        // Virtual Display Tap Test
        // ==========================================

        HorizontalDivider()

        Text(
            text = "Virtual Display Test",
            style = MaterialTheme.typography.titleMedium
        )

        // Display ID can change when the display is recreated.
        var virtualDisplayId by remember {
            mutableStateOf("2")
        }

        var virtualTapX by remember {
            mutableStateOf("350")
        }

        var virtualTapY by remember {
            mutableStateOf("500")
        }

        OutlinedTextField(
            value = virtualDisplayId,
            onValueChange = { virtualDisplayId = it },
            label = { Text("Virtual Display ID") },
            supportingText = { Text("Enter the actual display ID greater than 1.") },
            singleLine = true
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = virtualTapX,
                onValueChange = { virtualTapX = it },
                label = { Text("X") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            OutlinedTextField(
                value = virtualTapY,
                onValueChange = { virtualTapY = it },
                label = { Text("Y") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }

        val targetDisplayId = virtualDisplayId.toIntOrNull()
        val targetX = virtualTapX.toIntOrNull()
        val targetY = virtualTapY.toIntOrNull()

        Button(
            enabled = targetDisplayId != null &&
                    targetDisplayId > 1 &&
                    targetX != null &&
                    targetX >= 0 &&
                    targetY != null &&
                    targetY >= 0,

            onClick = {
                val id = targetDisplayId
                val x = targetX
                val y = targetY

                if (id != null && x != null && y != null) {
                    onTapVirtualDisplay(id, x, y)
                }
            }
        ) {
            Text("Tap Virtual Display")
        }

        // ==========================================
        // Virtual Display Swipe Test
        // ==========================================

        HorizontalDivider()

        Text(
            text = "Virtual Display Swipe Test",
            style = MaterialTheme.typography.titleMedium
        )

        // Shares the display ID entered in the tap test above.
        Text("Uses the Virtual Display ID above.")
        Text("Swipe: (350, 950) to (350, 300), duration: 500 ms")

        // MainActivity supplies the predefined gesture through this callback.
        Button(
            enabled = targetDisplayId != null && targetDisplayId > 1,
            onClick = {
                val id = targetDisplayId
                if (id != null && id > 1) {
                    onSwipeVirtualDisplay(id)
                }
            }
        ) {
            Text("Swipe Virtual Display")
        }

        // ==========================================
        // Virtual Display Shell Navigation Test
        // ==========================================

        HorizontalDivider()

        Text(
            text = "Virtual Display Shell Navigation Test",
            style = MaterialTheme.typography.titleMedium
        )

        // Shares the target display with tap and swipe; results appear in the shell status.
        Text("Uses the Virtual Display ID above.")

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                enabled = targetDisplayId != null && targetDisplayId > 1,
                onClick = {
                    val id = targetDisplayId
                    if (id != null && id > 1) {
                        onBackVirtualDisplay(id)
                    }
                }
            ) {
                Text("Shell Back")
            }

            Button(
                enabled = targetDisplayId != null && targetDisplayId > 1,
                onClick = {
                    val id = targetDisplayId
                    if (id != null && id > 1) {
                        onHomeVirtualDisplay(id)
                    }
                }
            ) {
                Text("Shell Home")
            }
        }

        // ==========================================
        // Virtual Display App Launch Test
        // ==========================================

        HorizontalDivider()

        Text(
            text = "Virtual Display App Launch Test",
            style = MaterialTheme.typography.titleMedium
        )

        // Shares the display ID above; Settings provides a convenient initial test.
        var virtualAppPackage by remember { mutableStateOf("com.android.settings") }
        val targetPackage = virtualAppPackage.trim()
        val packagePattern = remember {
            Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")
        }
        val validPackage = packagePattern.matches(targetPackage)

        Text("Uses the Virtual Display ID above (greater than 1).")

        OutlinedTextField(
            value = virtualAppPackage,
            onValueChange = { virtualAppPackage = it },
            label = { Text("App Package Name") },
            singleLine = true,
            isError = virtualAppPackage.isNotEmpty() && !validPackage,
            supportingText = { Text("Example: com.android.settings") }
        )

        // Validates input before delegating to the shell client through MainActivity.
        Button(
            enabled = targetDisplayId != null && targetDisplayId > 1 && validPackage,
            onClick = {
                val id = targetDisplayId
                if (id != null && id > 1 && validPackage) {
                    onLaunchVirtualApp(id, targetPackage)
                }
            }
        ) {
            Text("Launch App on Virtual Display")
        }

        // ==========================================
        // Shell Installed Packages Test
        // ==========================================

        HorizontalDivider()

        Text(
            text = "Shell Installed Packages Test",
            style = MaterialTheme.typography.titleMedium
        )

        // Package listing is device-wide and does not require a display ID.
        Text("Lists installed package names, including apps without launcher activities.")

        Button(onClick = onListInstalledPackages) {
            Text("List Installed Packages")
        }


    }
}
