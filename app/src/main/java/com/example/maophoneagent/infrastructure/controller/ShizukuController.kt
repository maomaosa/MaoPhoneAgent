/*
 * File: ShizukuController.kt
 * Module: infrastructure.controller
 *
 * Description:
 * Monitors the Shizuku service connection and manages permission requests.
 * Reports connection, compatibility, and authorization status through
 * a callback for display by the caller.
 *
 * Listeners are registered in start() and removed in stop().
 * This controller manages Shizuku availability and permissions;
 * it does not execute phone interactions.
 */

package com.example.maophoneagent.infrastructure.controller

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Coordinates Shizuku connection monitoring and permission handling.
 *
 * @param onStatusChanged The callback that receives human-readable status messages.
 */
class ShizukuController(
    private val onStatusChanged: (String) -> Unit
) {

    companion object {
        // Identifies permission results belonging to this controller's request.
        private const val REQUEST_CODE = 1001
    }

    // Refreshes status when the Shizuku service binder becomes available.
    private val binderReceivedListener =
        Shizuku.OnBinderReceivedListener {
            updateStatus()
        }

    // Reports when the Shizuku service binder disconnects.
    private val binderDeadListener =
        Shizuku.OnBinderDeadListener {
            onStatusChanged("Shizuku service disconnected")
        }

    // Handles only permission results matching this controller's request code.
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { code, result ->
            if (code == REQUEST_CODE) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    updateStatus()
                } else {
                    onStatusChanged("Shizuku permission denied")
                }
            }
        }

    /**
     * Registers connection and permission listeners, then reports the current status.
     * Call stop() when monitoring is no longer needed to remove the listeners.
     */
    fun start() {
        // The sticky listener also receives an already available binder.
        Shizuku.addBinderReceivedListenerSticky(
            binderReceivedListener
        )
        Shizuku.addBinderDeadListener(
            binderDeadListener
        )
        Shizuku.addRequestPermissionResultListener(
            permissionListener
        )

        updateStatus()
    }

    /**
     * Removes this controller's connection and permission listeners.
     * Does not stop the Shizuku service or revoke its permission.
     */
    fun stop() {
        Shizuku.removeBinderReceivedListener(
            binderReceivedListener
        )
        Shizuku.removeBinderDeadListener(
            binderDeadListener
        )
        Shizuku.removeRequestPermissionResultListener(
            permissionListener
        )
    }

    /**
     * Requests Shizuku permission if the service is running and permission is needed.
     * Reports existing authorization, prior denial, or request errors through the callback.
     * The permission listener handles the result of a new request.
     */
    fun requestPermission() {
        // Permission requests require a reachable Shizuku service binder.
        if (!Shizuku.pingBinder()) {
            onStatusChanged("Shizuku is not running")
            return
        }

        try {
            // Refreshes status instead of requesting permission that is already granted.
            if (Shizuku.checkSelfPermission() ==
                PackageManager.PERMISSION_GRANTED
            ) {
                updateStatus()
                return
            }

            // Directs the user to Shizuku permission management after a prior denial.
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                onStatusChanged(
                    "Permission was denied. Manage this app's permission in Shizuku."
                )
                return
            }

            Shizuku.requestPermission(REQUEST_CODE)

        } catch (e: Exception) {
            onStatusChanged(
                "Shizuku permission request failed: ${e.message}"
            )
        }
    }

    /**
     * Checks service connectivity, version compatibility, and permission state.
     * Reports the service UID when authorized, or an error if the check fails.
     */
    fun updateStatus() {
        val status = try {
            when {
                !Shizuku.pingBinder() ->
                    "Shizuku is not connected"

                Shizuku.isPreV11() ->
                    "The current Shizuku version is not supported"

                Shizuku.checkSelfPermission() ==
                        PackageManager.PERMISSION_GRANTED ->
                    "Shizuku permission granted, UID=${Shizuku.getUid()}"

                else ->
                    "Shizuku is connected, awaiting permission"
            }
        } catch (e: Exception) {
            "Shizuku status check failed: ${e.message}"
        }

        onStatusChanged(status)
    }
}
