/*
 * File: MainActivity.kt
 * Module: com.example.maophoneagent
 *
 * Description:
 * Provides the main activity for the MaoPhoneAgent application.
 * Connects the phone controller, application service, and ViewModel,
 * and displays HomeScreen using the application theme.
 *
 * Currently uses MockPhoneController to verify operation calls
 * while actual device control is pending implementation.
 */

package com.example.maophoneagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import com.example.maophoneagent.application.service.PhoneActionService
import com.example.maophoneagent.domain.controller.PhoneController
//import com.example.maophoneagent.infrastructure.controller.MockPhoneController
import com.example.maophoneagent.infrastructure.controller.AccessibilityPhoneController
import com.example.maophoneagent.ui.screen.HomeScreen
import com.example.maophoneagent.ui.theme.MaoPhoneAgentTheme
import com.example.maophoneagent.ui.viewmodel.PhoneViewModel
import com.example.maophoneagent.infrastructure.controller.ShizukuController
import com.example.maophoneagent.infrastructure.shizuku.ShizukuShellClient

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

                    // Uses the temporary controller for verifying operation calls.
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

        setContent {
            MaoPhoneAgentTheme {
                HomeScreen(
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
                    }


                )
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
        shellClient.close()
        super.onDestroy()
    }
}
