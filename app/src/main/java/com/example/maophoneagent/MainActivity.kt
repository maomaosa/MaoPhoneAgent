/*
 * File: MainActivity.kt
 * Module: com.example.maophoneagent
 *
 * Description:
 * Provides the main activity for the MaoPhoneAgent application.
 * Connects the phone controller, application service, and ViewModel,
 * and displays the demo or test screen using the application theme.
 *
 * Uses accessibility for observation and primary-screen actions, and Shizuku
 * for Agent operations on a selected secondary display.
 */

package com.example.maophoneagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.maophoneagent.ui.screen.DemoScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import com.example.maophoneagent.application.service.PhoneActionService
import com.example.maophoneagent.domain.controller.PhoneController
import com.example.maophoneagent.infrastructure.controller.AccessibilityPhoneController
import com.example.maophoneagent.ui.screen.TestScreen
import com.example.maophoneagent.ui.theme.MaoPhoneAgentTheme
import com.example.maophoneagent.ui.viewmodel.PhoneViewModel
import com.example.maophoneagent.infrastructure.shizuku.ShizukuController
import com.example.maophoneagent.infrastructure.shizuku.ShizukuShellClient
import android.hardware.display.DisplayManager
import com.example.maophoneagent.agent.tool.PhoneToolExecutor
import com.example.maophoneagent.agent.planner.ScriptedAgentPlanner
import com.example.maophoneagent.agent.runtime.AgentRunner
import com.example.maophoneagent.application.service.PhoneObservationService
import com.example.maophoneagent.infrastructure.display.VirtualDisplayOverlayService

/**
 * Hosts the phone action screen and provides its ViewModel dependencies.
 */
class MainActivity : ComponentActivity() {
    /**
     * Lazily obtains the activity-scoped ViewModel through a custom factory.
     * The ViewModel is retained across activity configuration changes.
     */
    private val phoneViewModel: PhoneViewModel by lazy {
        ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                /**
                 * Creates a PhoneViewModel with its service and controller.
                 *
                 * @param modelClass The requested ViewModel class.
                 * @return A PhoneViewModel instance with its dependencies.
                 * @throws IllegalArgumentException If the requested class cannot
                 * accept a PhoneViewModel instance.
                 */
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(PhoneViewModel::class.java))

                    // Uses the accessibility implementation for phone action tests.
                    val controller: PhoneController = AccessibilityPhoneController()

                    // Supplies the controller through the application service.
                    val service = PhoneActionService(controller)

                    @Suppress("UNCHECKED_CAST")
                    return PhoneViewModel(service) as T
                }
            }
        )[PhoneViewModel::class.java]
    }

    // Shizuku status observed by Compose.
    private var shizukuStatus by mutableStateOf("Shizuku status unknown")

    // Shizuku controller.
    private val shizukuController by lazy {
        ShizukuController { status ->
            runOnUiThread {
                shizukuStatus = status
            }
        }
    }

    // Shell test status
    private var shellTestStatus by mutableStateOf("Shell test not started")

    // Shell client.
    private lateinit var shellClient: ShizukuShellClient


    /**
     * Initializes the activity and displays the themed phone action screen.
     *
     * @param savedInstanceState The previously saved activity state, or null
     * when the activity is created for the first time.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Shell UserService client.
        shellClient = ShizukuShellClient(
            applicationContext
        ) { message ->
            shellTestStatus = message
        }

        // Reuses one executor so Agent test requests share its execution mutex.
        val agentToolExecutor = PhoneToolExecutor(
            PhoneObservationService(),
            shellClient,
            getSystemService(DisplayManager::class.java)
        )

        // The bound function executes tools through the existing shared executor.
        val agentRunner = AgentRunner(ScriptedAgentPlanner(), agentToolExecutor::execute)

        setContent {
            MaoPhoneAgentTheme {
                var showTests by remember { mutableStateOf(false) }
                val shellConnected by shellClient.connected.collectAsStateWithLifecycle()
                if (showTests) {
                    BackHandler { showTests = false }
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(onClick = { showTests = false }) { Text("返回 Demo") }
                        Box(modifier = Modifier.weight(1f)) {
                            TestScreen(
                                viewModel = phoneViewModel,
            
                                // Existing Shizuku authorization UI.
                                shizukuStatus = shizukuStatus,
            
                                onRequestShizukuPermission = {
                                    shizukuController.requestPermission()
                                },
            
                                onRefreshShizukuStatus = {
                                    shizukuController.updateStatus()
                                },
            
                                // New Shell UserService test UI.
                                shellTestStatus = shellTestStatus,
            
                                onConnectShell = {
                                    shellClient.connect()
                                },
            
                                onRunShellId = {
                                    shellClient.runId()
                                },
            
                                onTapVirtualDisplay = { displayId, x, y ->
                                    shellClient.tap(displayId, x, y)
                                },
            
                                onSwipeVirtualDisplay = { displayId ->
                                    shellClient.swipe(
                                        displayId,
                                        350, 950,
                                        350, 300,
                                        500
                                    )
                                },
            
                                onBackVirtualDisplay = { displayId ->
                                    shellClient.back(displayId)
                                },
            
                                onHomeVirtualDisplay = { displayId ->
                                    shellClient.home(displayId)
                                },
            
                                onLaunchVirtualApp = { displayId, packageName ->
                                    shellClient.launchApp(displayId, packageName)
                                },
            
                                onListInstalledPackages = {
                                    shellClient.listInstalledPackages()
                                },
            
                                agentToolExecutor = agentToolExecutor,
                                agentRunner = agentRunner,
                                shellClient = shellClient
            
                            )
                        }
                    }
                } else {
                    DemoScreen(
                        executor = agentToolExecutor,
                        shizukuStatus = shizukuStatus,
                        shellConnected = shellConnected,
                        onRequestShizukuPermission = { shizukuController.requestPermission() },
                        onRefreshShizukuStatus = { shizukuController.updateStatus() },
                        onConnectShell = { shellClient.connect() },
                        onOpenTests = { showTests = true }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        shizukuController.start()
    }

    override fun onStop() {
        shizukuController.stop()
        super.onStop()
    }

    override fun onDestroy() {
        // Keep the shared Shell screen alive while its foreground preview service owns a binding.
        shellClient.close(removeService = !VirtualDisplayOverlayService.running.value)
        super.onDestroy()
    }
}
