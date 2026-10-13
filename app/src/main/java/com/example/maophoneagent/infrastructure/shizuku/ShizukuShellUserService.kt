/*
 * File: ShizukuShellUserService.kt
 * Module: infrastructure.shizuku
 *
 * Description:
 * Implements the shell AIDL service in a separate Shizuku-managed process.
 * Executes a fixed identity diagnostic and display-specific tap, swipe,
 * Back, and Home commands. Validates request parameters and returns
 * command output with exit information.
 */

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
     * Executes the fixed /system/bin/id command to inspect the process identity.
     *
     * @return The exit code and combined command output, or an execution error.
     */
    override fun runId(): String {
        return try {
            val process = ProcessBuilder(
                "/system/bin/id"
            )
                .redirectErrorStream(true)
                .start()

            // Reads combined standard output and error before waiting for process exit.
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
     * Executes a tap command on a specific display and waits for process exit.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     * @param x The non-negative horizontal coordinate in pixels.
     * @param y The non-negative vertical coordinate in pixels.
     * @return The tap description, exit code, and available command output,
     * or a validation or execution error.
     */
    override fun tap(
        displayId: Int,
        x: Int,
        y: Int
    ): String {

        // Prevent accidentally controlling the main screen.
        // Reserves display IDs 0 and 1 under the current test policy.
        if (displayId <= 1) {
            return "Invalid displayId: $displayId"
        }
        // Rejects negative screen coordinates.
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
     * Executes a swipe command on a specific display and waits for process exit.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     * @param x1 The non-negative starting horizontal coordinate in pixels.
     * @param y1 The non-negative starting vertical coordinate in pixels.
     * @param x2 The non-negative ending horizontal coordinate in pixels.
     * @param y2 The non-negative ending vertical coordinate in pixels.
     * @param durationMs The swipe duration in milliseconds, from 1 to 10000.
     * @return The swipe description, exit code, and available command output,
     * or a validation or execution error.
     */
    override fun swipe(
        displayId: Int,
        x1: Int,
        y1: Int,
        x2: Int,
        y2: Int,
        durationMs: Int
    ): String {

        // Prevent accidentally controlling the main screen.
        // Reserves display IDs 0 and 1 under the current test policy.
        if (displayId <= 1) {
            return "Invalid display ID"
        }

        // Rejects negative coordinates at either end of the gesture.
        if (listOf(x1, y1, x2, y2).any { it < 0 }) {
            return "Invalid coordinates"
        }

        // Restricts the swipe duration to the supported application range.
        if (durationMs !in 1..10000) {
            return "Invalid swipe duration"
        }

        return try {
            val process = ProcessBuilder(
                "/system/bin/input",
                "-d",
                displayId.toString(),
                "swipe",
                x1.toString(),
                y1.toString(),
                x2.toString(),
                y2.toString(),
                durationMs.toString()
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText().trim() }

            val exitCode = process.waitFor()

            buildString {
                append("Swipe display=$displayId")
                append("\n($x1,$y1) -> ($x2,$y2)")
                append("\nExit code: $exitCode")

                if (output.isNotEmpty()) {
                    append("\n$output")
                }
            }

        } catch (e: Exception) {
            "Swipe failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }



    /**
     * Terminates this service process when Shizuku removes the UserService.
     */
    override fun destroy() {
        System.exit(0)
    }

    /**
     * Sends KEYCODE_HOME to the target display and waits for command completion.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     * @return The action description, exit code, and command output, or an error.
     */
    override fun home(displayId: Int): String {
        // Uses the same display restriction as tap and swipe.
        // Reserves display IDs 0 and 1 under the current test policy.
        if (displayId <= 1) {
            return "Invalid display ID: $displayId"
        }

        return try {
            val process = ProcessBuilder(
                "/system/bin/input",
                "-d",
                displayId.toString(),
                "keyevent",
                "KEYCODE_HOME"
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText().trim() }

            val exitCode = process.waitFor()

            buildString {
                append("Home display=$displayId")
                append("\nExit code: $exitCode")
                if (output.isNotEmpty()) {
                    append("\n$output")
                }
            }
        } catch (e: Exception) {
            "Home failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }


    /**
     * Requests an app launch through am start with an explicit target display.
     * Uses the Settings action for com.android.settings and the launcher intent
     * for other packages. The target display must exist and allow activity launches.
     *
     * @param displayId The target Android display ID greater than 1.
     * @param packageName The installed app's package name.
     * @return The requested package and display, exit code, and combined output,
     * or a validation or execution error. Command completion alone does not
     * confirm that the activity appeared on the target display.
     */
    override fun launchApp(
        displayId: Int,
        packageName: String
    ): String {

        if (displayId <= 1) {
            return "Invalid display ID: $displayId"
        }

        // Restricts input to package names; arguments are passed directly without a shell.
        val packagePattern = Regex(
            """[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+"""
        )

        if (!packagePattern.matches(packageName)) {
            return "Invalid package name: $packageName"
        }

        return try {
            // Keeps Settings resolution within the requested package.
            // Other apps must expose an activity matching MAIN and LAUNCHER.
            val command = if (packageName == "com.android.settings") {
                listOf(
                    "/system/bin/am",
                    "start",
                    "--display",
                    displayId.toString(),
                    "-a",
                    "android.settings.SETTINGS",
                    "-p",
                    packageName
                )
            } else {
                listOf(
                    "/system/bin/am",
                    "start",
                    "--display",
                    displayId.toString(),
                    "-a",
                    "android.intent.action.MAIN",
                    "-c",
                    "android.intent.category.LAUNCHER",
                    "-p",
                    packageName
                )
            }

            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText().trim() }

            val exitCode = process.waitFor()

            "Launch $packageName on display=$displayId" +
                    "\nExit code: $exitCode" +
                    "\n$output"

        } catch (e: Exception) {
            "Launch failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Lists installed package names using the shell package manager command.
     * Includes packages without launcher activities; this does not list app labels.
     *
     * @return The exit code and combined command output, or an execution error.
     */
    override fun listInstalledPackages(): String {
        return try {
            val process = ProcessBuilder(
                "/system/bin/pm",
                "list",
                "packages"
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText().trim() }

            val exitCode = process.waitFor()

            "Installed packages\nExit code: $exitCode\n$output"
        } catch (e: Exception) {
            "Package listing failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /**
     * Sends KEYCODE_BACK to the target display and waits for command completion.
     *
     * @param displayId The target Android display ID greater than 1; IDs 0 and 1 are rejected.
     * @return The action description, exit code, and command output, or an error.
     */
    override fun back(displayId: Int): String {
        // Uses the same display restriction as tap and swipe.
        // Reserves display IDs 0 and 1 under the current test policy.
        if (displayId <= 1) {
            return "Invalid display ID: $displayId"
        }

        return try {
            val process = ProcessBuilder(
                "/system/bin/input",
                "-d",
                displayId.toString(),
                "keyevent",
                "KEYCODE_BACK"
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream
                .bufferedReader()
                .use { it.readText().trim() }

            val exitCode = process.waitFor()

            buildString {
                append("Back display=$displayId")
                append("\nExit code: $exitCode")
                if (output.isNotEmpty()) {
                    append("\n$output")
                }
            }
        } catch (e: Exception) {
            "Back failed: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

}
