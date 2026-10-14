/*
 * File: OpenAiToolProtocol.kt
 * Module: agent.ai
 *
 * Description:
 * Defines Responses API function schemas and validates model-selected tool arguments.
 * Only the existing Agent tools and task completion can be selected.
 */
package com.example.maophoneagent.agent.ai

import com.example.maophoneagent.agent.model.AgentDecision
import com.example.maophoneagent.agent.model.AgentToolCall
import org.json.JSONArray
import org.json.JSONObject

object OpenAiToolProtocol {
    private val packagePattern = Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)+""")

    /** Each strict schema requires all declared fields and rejects extra arguments. */
    fun tools(): JSONArray {
        fun function(name: String, description: String, vararg fields: Pair<String, String>): JSONObject {
            val properties = JSONObject()
            fields.forEach { (key, type) -> properties.put(key, JSONObject().put("type", type)) }
            return JSONObject().put("type", "function").put("name", name)
                .put("description", description).put("strict", true)
                .put("parameters", JSONObject().put("type", "object")
                    .put("properties", properties).put("required", JSONArray(fields.map { it.first }))
                    .put("additionalProperties", false))
        }
        return JSONArray(listOf(
            function("observe", "Read current UI and screenshot. Use after actions to verify the screen."),
            function("tap", "Tap a control at non-negative original screen pixel coordinates. For app-opening tasks, use launch_app first when the installed package is known; do not open the app drawer instead.", "x" to "integer", "y" to "integer"),
            function("swipe", "Swipe non-negative screen coordinates; durationMs must be 1-10000.",
                "startX" to "integer", "startY" to "integer", "endX" to "integer", "endY" to "integer", "durationMs" to "integer"),
            function("input_text", "Replace the entire text of the focused editable field on the selected display. Tap the field and observe focus first. Supports Unicode and spaces; empty text clears it. Maximum 2000 characters. Observe afterward to verify.", "text" to "string"),
            function("submit_input", "Submit the focused editor's configured IME action, such as Search, Go, or Done. If unsupported, tap the visible search or submit button. Observe afterward to verify results."),
            function("home", "Send Home on the application-selected display."),
            function("back", "Send Back on the application-selected display."),
            function("launch_app", "First choice for opening a known installed app on the selected display. For Settings use com.android.settings if installed. Observe after launch to verify the screen.", "packageName" to "string"),
            function("list_apps", "List device-wide installed package names."),
            function("complete", "End immediately when observation confirms the requested goal. For open-app-only tasks, seeing the target app open is enough; do not perform extra actions.", "message" to "string")
        ))
    }

    /** Parses one Responses function_call without executing it. */
    fun parse(response: JSONObject): AgentDecision {
        check(response.optString("status") == "completed") { "OpenAI response was not completed" }
        val output = response.getJSONArray("output")
        val calls = (0 until output.length()).map { output.getJSONObject(it) }
            .filter { it.optString("type") == "function_call" }
        require(calls.size == 1) { "Expected exactly one OpenAI function call" }
        val call = calls.single()
        val name = call.getString("name")
        val args = JSONObject(call.getString("arguments"))
        fun fields(vararg expected: String) {
            require(args.keys().asSequence().toSet() == expected.toSet()) { "Invalid arguments for $name" }
        }
        fun integer(key: String): Int {
            val value = args.get(key)
            require(value is Number) { "$key must be an integer" }
            val number = value.toDouble()
            require(number.isFinite() && number >= 0 && number <= Int.MAX_VALUE && number % 1.0 == 0.0) {
                "$key must be a non-negative integer"
            }
            return number.toInt()
        }
        fun string(key: String): String {
            val value = args.get(key)
            require(value is String && value.isNotBlank()) { "$key must be a non-blank string" }
            return value
        }
        val tool = when (name) {
            "observe" -> { fields(); AgentToolCall.Observe }
            "tap" -> { fields("x", "y"); AgentToolCall.Tap(integer("x"), integer("y")) }
            "swipe" -> {
                fields("startX", "startY", "endX", "endY", "durationMs")
                val duration = integer("durationMs")
                require(duration in 1..10000) { "Swipe duration must be 1-10000 ms" }
                AgentToolCall.Swipe(integer("startX"), integer("startY"), integer("endX"), integer("endY"), duration)
            }
            "input_text" -> {
                fields("text")
                val value = args.get("text")
                require(value is String && value.length <= 2000) { "text must be a string of at most 2000 characters" }
                AgentToolCall.InputText(value)
            }
            "submit_input" -> { fields(); AgentToolCall.SubmitInput }
            "home" -> { fields(); AgentToolCall.Home }
            "back" -> { fields(); AgentToolCall.Back }
            "list_apps" -> { fields(); AgentToolCall.ListApps }
            "launch_app" -> {
                fields("packageName")
                val value = string("packageName")
                require(packagePattern.matches(value)) { "Invalid package name" }
                AgentToolCall.LaunchApp(value)
            }
            "complete" -> { fields("message"); return AgentDecision.Complete(string("message")) }
            else -> error("Unsupported OpenAI tool: $name")
        }
        return AgentDecision.CallTool(tool)
    }
}
