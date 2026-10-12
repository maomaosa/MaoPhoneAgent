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
import com.example.maophoneagent.application.service.PhoneActionService
import com.example.maophoneagent.domain.controller.PhoneController
//import com.example.maophoneagent.infrastructure.controller.MockPhoneController
import com.example.maophoneagent.infrastructure.controller.AccessibilityPhoneController
import com.example.maophoneagent.ui.screen.HomeScreen
import com.example.maophoneagent.ui.theme.MaoPhoneAgentTheme
import com.example.maophoneagent.ui.viewmodel.PhoneViewModel

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

    /**
     * Initializes the activity and displays the themed phone action screen.
     *
     * @param savedInstanceState The previously saved activity state, or null
     * when the activity is created for the first time.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaoPhoneAgentTheme {
                HomeScreen(viewModel = phoneViewModel)
            }
        }
    }
}
