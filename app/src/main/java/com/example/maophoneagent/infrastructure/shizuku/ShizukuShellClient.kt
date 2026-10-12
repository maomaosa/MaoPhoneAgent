
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
 * Connects MaoPhoneAgent to the privileged
 * Shell UserService managed by Shizuku.
 */
class ShizukuShellClient(
    context: Context,
    private val onStatusChanged: (String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var remoteService: IShellUserService? = null

    private var bound = false

    private val serviceArgs = Shizuku.UserServiceArgs(
        ComponentName(
            context,
            ShizukuShellUserService::class.java
        )
    )
        .processNameSuffix("mao_shell")
        .tag("maophoneagent_shell")
        .daemon(false)
        .version(8)

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
     * Starts and connects to Shizuku UserService.
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
     * Calls the remote service on a background thread.
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
     * Executes a tap on the target virtual display.
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

        if (displayId <= 0 || x < 0 || y < 0) {
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



    private fun updateStatus(message: String) {
        mainHandler.post {
            onStatusChanged(message)
        }
    }

    /**
     * Releases the connection when Activity is destroyed.
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
