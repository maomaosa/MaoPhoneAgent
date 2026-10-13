/*
 * File: ShizukuShellClient.kt
 * Module: infrastructure.shizuku
 *
 * Description:
 * Connects to the shell UserService managed by Shizuku.
 * Validates gesture and navigation requests and performs remote calls on background threads.
 * Reports connection and command results through a main-thread callback.
 */

package com.example.maophoneagent.infrastructure.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.example.maophoneagent.IShellUserService
import rikka.shizuku.Shizuku
import android.util.Log

/**
 * Connects MaoPhoneAgent to the shell UserService managed by Shizuku.
 *
 * @param context The context used to identify the UserService component.
 * @param onStatusChanged The callback that receives status messages on the main thread.
 */
class ShizukuShellClient(
    context: Context,
    private val onStatusChanged: (String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    // Makes connection updates visible to threads performing remote calls.
    @Volatile
    private var remoteService: IShellUserService? = null

    // Tracks a binding request separately from an established remote connection.
    private var bound = false

    // Uses a consistent configuration when binding and unbinding the service.
    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            context,
            ShizukuShellUserService::class.java
        )
    )
        .processNameSuffix("mao_shell")
        .tag("maophoneagent_shell")
        .daemon(false)
        // Restarts an older UserService after adding the package listing AIDL method.
        .version(11)

    // Converts the connected Binder into the AIDL interface and clears it on disconnect.
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(
            name: ComponentName?,
            binder: IBinder?
        ) {
            if (binder == null) {
                updateStatus("UserService returned null Binder")
                return
            }

            remoteService =
                IShellUserService.Stub.asInterface(binder)

            updateStatus("Shell UserService connected")
        }

        override fun onServiceDisconnected(
            name: ComponentName?
        ) {
            remoteService = null
            updateStatus("Shell UserService disconnected")
        }
    }

    /**
     * Requests a connection to the Shizuku UserService after checking permission.
     * Reports connection progress and errors through the status callback.
     * The remote interface becomes available when the connection callback arrives.
     */
    fun connect() {
        if (!Shizuku.pingBinder()) {
            updateStatus("Shizuku is not running")
            return
        }

        try {
            if (Shizuku.checkSelfPermission() !=
                PackageManager.PERMISSION_GRANTED
            ) {
                updateStatus("Shizuku permission required")
                return
            }

            if (remoteService != null) {
                updateStatus("Shell UserService already connected")
                return
            }

            // Clear a previous connection before retrying.
            if (bound) {
                Shizuku.unbindUserService(
                    serviceArgs,
                    connection,
                    false
                )
                bound = false
            }

            updateStatus("Connecting Shell UserService...")

            Shizuku.bindUserService(
                serviceArgs,
                connection
            )

            bound = true

        } catch (e: Exception) {
            updateStatus("Connection failed: ${e.message}")
        }
    }

    /**
     * Runs the remote id diagnostic command on a background thread.
     * Reports the command result or a connection or execution error.
     */
    fun runId() {
        val service = remoteService

        if (service == null) {
            updateStatus("Connect Shell UserService first")
            return
        }

        updateStatus("Executing id command...")

        Thread {
            try {
                val result = service.runId()
                updateStatus(result)

            } catch (e: Exception) {
                updateStatus(
                    "Shell execution failed: ${e.message}"
                )
            }
        }.start()
    }

    /**
     * Requests a tap on the target display using a background remote call.
     * Reports validation errors, command results, or Binder failure details.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     * @param x The non-negative horizontal coordinate in pixels.
     * @param y The non-negative vertical coordinate in pixels.
     */
    fun tap(
        displayId: Int,
        x: Int,
        y: Int
    ) {
        val service = remoteService

        if (service == null) {
            updateStatus("Shell UserService not connected")
            return
        }

        if (displayId <= 1 || x < 0 || y < 0) {
            updateStatus("Invalid display or coordinates")
            return
        }

        updateStatus(
            "Executing Tap: display=$displayId ($x, $y)"
        )

        Thread {
            try {
                Log.d(
                    "MaoShell",
                    "Calling remote tap: display=$displayId, x=$x, y=$y"
                )

                val result = service.tap(
                    displayId,
                    x,
                    y
                )

                Log.d("MaoShell", "Tap result: $result")

                updateStatus(result)

            } catch (e: Exception) {

                // Print the complete exception stack trace.
                Log.e(
                    "MaoShell",
                    "Remote Tap failed",
                    e
                )

                // Check whether the remote Binder is alive.
                val binderAlive = service.asBinder().isBinderAlive

                val errorType = e.javaClass.simpleName
                val errorMessage = e.message ?: "No message"

                updateStatus(
                    "Tap failed\n" +
                            "Exception: $errorType\n" +
                            "Message: $errorMessage\n" +
                            "Binder alive: $binderAlive"
                )
            }
        }.start()
    }


    /**
     * Requests a swipe on the target display using a background remote call.
     * Reports validation errors, command results, or execution errors.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     * @param x1 The non-negative starting horizontal coordinate in pixels.
     * @param y1 The non-negative starting vertical coordinate in pixels.
     * @param x2 The non-negative ending horizontal coordinate in pixels.
     * @param y2 The non-negative ending vertical coordinate in pixels.
     * @param durationMs The swipe duration in milliseconds, from 1 to 10000.
     */
    fun swipe(
        displayId: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int
    ) {
        val service = remoteService

        if (service == null) {
            updateStatus("Shell UserService not connected")
            return
        }

        if (displayId <= 1 ||
            listOf(x1, y1, x2, y2).any { it < 0 } ||
            durationMs !in 1..10000
        ) {
            updateStatus("Invalid swipe parameters")
            return
        }

        updateStatus("Executing virtual swipe...")

        Thread {
            try {
                val result = service.swipe(
                    displayId,
                    x1, y1,
                    x2, y2,
                    durationMs
                )

                updateStatus(
                    result ?: "Remote swipe returned null"
                )

            } catch (e: Exception) {
                Log.e("MaoShell", "Swipe failed", e)

                updateStatus(
                    "Swipe failed: ${e.javaClass.simpleName}"
                )
            }
        }.start()
    }



    /**
     * Posts a status message to the callback on the main thread.
     *
     * @param message The connection status, command result, or error to report.
     */
    private fun updateStatus(message: String) {
        mainHandler.post {
            onStatusChanged(message)
        }
    }

    /**
     * Requests Home on the target display using a background remote call.
     * Reports validation errors, command results, or execution errors.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     */
    fun home(displayId: Int) {
        val service = remoteService

        if (service == null) {
            updateStatus("Shell UserService not connected")
            return
        }

        if (displayId <= 1) {
            updateStatus("Invalid display ID: $displayId")
            return
        }

        updateStatus("Executing Shell Home: display=$displayId")

        Thread {
            try {
                val result = service.home(displayId)
                updateStatus(result ?: "Remote home returned null")
            } catch (e: Exception) {
                Log.e("MaoShell", "Home failed", e)
                updateStatus("Home failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }

    /**
     * Requests Back on the target display using a background remote call.
     * Reports validation errors, command results, or execution errors.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     */
    fun back(displayId: Int) {
        val service = remoteService

        if (service == null) {
            updateStatus("Shell UserService not connected")
            return
        }

        if (displayId <= 1) {
            updateStatus("Invalid display ID: $displayId")
            return
        }

        updateStatus("Executing Shell Back: display=$displayId")

        Thread {
            try {
                val result = service.back(displayId)
                updateStatus(result ?: "Remote back returned null")
            } catch (e: Exception) {
                Log.e("MaoShell", "Back failed", e)
                updateStatus("Back failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }


    /**
     * Requests an app launch on the target display using a background remote call.
     * Reports parameter errors and the remote command result on the main thread.
     *
     * @param displayId The target Android display ID greater than 1.
     * @param packageName The installed app's package name, without surrounding whitespace.
     */
    fun launchApp(
        displayId: Int,
        packageName: String
    ) {
        val service = remoteService

        if (service == null) {
            updateStatus("Shell UserService not connected")
            return
        }

        // Matches the service-side validation before starting a background call.
        if (displayId <= 1) {
            updateStatus("Invalid display ID: $displayId")
            return
        }
        val packagePattern = Regex(
            """[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+"""
        )
        if (!packagePattern.matches(packageName)) {
            updateStatus("Invalid package name: $packageName")
            return
        }

        updateStatus("Launching $packageName on display=$displayId...")

        Thread {
            try {
                val result = service.launchApp(
                    displayId,
                    packageName
                )

                updateStatus(
                    result ?: "Launch returned null"
                )

            } catch (e: Exception) {
                Log.e("MaoShell", "Launch failed", e)

                updateStatus(
                    "Launch failed: ${e.javaClass.simpleName}: ${e.message}"
                )
            }
        }.start()
    }

    /**
     * Lists installed packages through a background remote call.
     * Reports connection errors, command output, or execution errors on the main thread.
     */
    fun listInstalledPackages() {
        val service = remoteService

        if (service == null) {
            updateStatus("Shell UserService not connected")
            return
        }

        updateStatus("Listing installed packages...")

        Thread {
            try {
                val result = service.listInstalledPackages()
                updateStatus(result ?: "Remote package listing returned null")
            } catch (e: Exception) {
                Log.e("MaoShell", "Package listing failed", e)
                updateStatus("Package listing failed: ${e.javaClass.simpleName}: ${e.message}")
            }
        }.start()
    }


    /**
     * Clears the remote interface and requests removal of the bound UserService.
     * Call this when the owning component no longer needs the client.
     */
    fun close() {
        remoteService = null

        if (bound) {
            try {
                if (Shizuku.pingBinder()) {
                    Shizuku.unbindUserService(
                        serviceArgs,
                        connection,
                        true
                    )
                }
            } catch (_: Exception) {
                // Shizuku may already have stopped.
            }

            bound = false
        }
    }
}
