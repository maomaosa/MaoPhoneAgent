
package com.example.maophoneagent.infrastructure.shizuku

import androidx.annotation.Keep
import com.example.maophoneagent.IShellUserService

/**
 * Runs in a separate process started by Shizuku.
 *
 * With ADB-started Shizuku, this process uses
 * the Android shell identity (UID 2000).
 */
@Keep
class ShizukuShellUserService : IShellUserService.Stub() {

    /**
     * Executes a fixed diagnostic command.
     * No arbitrary shell commands are accepted.
     */
    override fun runId(): String {
        return try {
            val process = ProcessBuilder(
                "/system/bin/id"
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText().trim() }

            val exitCode = process.waitFor()

            "Exit code: $exitCode\n$output"

        } catch (e: Exception) {
            "Shell command failed: ${e.message}"
        }
    }


    /**
     * Sends a tap to a specific virtual display.
     *
     * @param displayId Target Android display ID.
     * @param x Horizontal coordinate.
     * @param y Vertical coordinate.
     * @return Shell execution result.
     */
    override fun tap(
        displayId: Int,
        x: Int,
        y: Int
    ): String {

        // Prevent accidentally controlling the main screen.
        if (displayId <= 0) {
            return "Invalid displayId: $displayId"
        }
        // Prevent abnormal x and y value
        if (x < 0 || y < 0) {
            return "Invalid coordinates: ($x, $y)"
        }

        return try {
            val process = ProcessBuilder(
                "/system/bin/input",
                "-d",
                displayId.toString(),
                "tap",
                x.toString(),
                y.toString()
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText().trim() }

            val exitCode = process.waitFor()

            buildString {
                append("Tap display=$displayId ($x, $y)")
                append("\nExit code: $exitCode")

                if (output.isNotEmpty()) {
                    append("\n$output")
                }
            }

        } catch (e: Exception) {
            "Tap failed: ${e.message}"
        }
    }


    /**
     * Called when Shizuku removes this UserService.
     */
    override fun destroy() {
        System.exit(0)
    }

}
