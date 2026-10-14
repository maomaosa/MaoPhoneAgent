/*
 * File: OpenAppCompletion.kt
 * Module: agent.ai
 *
 * Description:
 * Verifies completion of narrowly matched app-opening tasks from fresh UI observations.
 * Leaves compound tasks and ambiguous app names to the AI planner.
 */
package com.example.maophoneagent.agent.ai

import com.example.maophoneagent.agent.model.*
import org.json.JSONObject

object OpenAppCompletion {
    private val english = Regex(
        """^(?:please\s+)?(?:(?:just|only)\s+)?(?:open|launch|start)\s+(.+?)(?:\s+(?:app|application))?(?:\s+(?:on|in)\s+(?:the\s+)?(?:target|virtual|secondary|second)\s+(?:display|screen))?(?:\s+only)?$""",
        RegexOption.IGNORE_CASE
    )
    private val chinese = Regex("""^(?:请)?(?:在(?:第二(?:块)?|副|虚拟|目标)屏幕(?:上)?)?(?:(?:只|仅)(?:需要)?)?(?:打开|启动)(.+?)(?:应用|软件|APP)?(?:就好|就行|即可)?$""", RegexOption.IGNORE_CASE)

    /** Resolves an exact app-opening request using installed packages and explicit aliases. */
    fun targetPackage(task: String, apps: JSONObject): String? {
        if (!apps.optBoolean("success")) return null
        val normalized = task.trim().trimEnd('.', '!', '。', '！').trim()
        val name = (english.matchEntire(normalized) ?: chinese.matchEntire(normalized))
            ?.groupValues?.get(1)?.trim() ?: return null
        val packages = apps.getJSONArray("packages")
        val installed = (0 until packages.length()).map { packages.getString(it) }.toSet()
        installed.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return it }
        val known = apps.optJSONArray("knownApplications") ?: return null
        val matches = mutableSetOf<String>()
        for (i in 0 until known.length()) {
            val entry = known.getJSONObject(i)
            val packageName = entry.getString("packageName")
            val aliases = entry.getJSONArray("aliases")
            if (packageName in installed && (0 until aliases.length()).any {
                    aliases.getString(it).equals(name, ignoreCase = true)
                }) matches += packageName
        }
        return matches.singleOrNull()
    }

    /**
     * Requests a fresh observation after a successful action, then finishes only when
     * the target package owns a visible active/focused application window.
     * A package in a list, background window, or old observation is not completion evidence.
     */
    fun nextDecision(task: String, history: List<AgentStep>, apps: JSONObject): AgentDecision? {
        val target = targetPackage(task, apps) ?: return null
        val observationIndex = history.indexOfLast { it.call == AgentToolCall.Observe }
        val actionIndex = history.indexOfLast {
            when (it.call) {
                is AgentToolCall.Tap, is AgentToolCall.Swipe, is AgentToolCall.LaunchApp,
                AgentToolCall.Home, AgentToolCall.Back -> true
                else -> false
            }
        }
        if (actionIndex > observationIndex) {
            return if (history[actionIndex].result.success) AgentDecision.CallTool(AgentToolCall.Observe) else null
        }
        val observation = history.getOrNull(observationIndex)?.result ?: return null
        if (!observation.success || observation.uiJson == null) return null
        val confirmed = runCatching {
            val windows = JSONObject(observation.uiJson).getJSONArray("windows")
            (0 until windows.length()).any { index ->
                val window = windows.getJSONObject(index)
                val root = window.optJSONObject("root")
                window.optInt("type") == 1 &&
                    (window.optBoolean("active") || window.optBoolean("focused")) &&
                    root != null && root.optString("packageName") == target && root.optBoolean("visible", true)
            }
        }.getOrDefault(false)
        if (!confirmed) return null
        val message = if (task.any { it in '\u4e00'..'\u9fff' })
            "目标应用已在选定屏幕打开：$target。" else "Target app is open on the selected display: $target."
        return AgentDecision.Complete(message)
    }
}
