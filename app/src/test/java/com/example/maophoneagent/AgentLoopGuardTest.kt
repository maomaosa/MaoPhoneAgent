package com.example.maophoneagent

import com.example.maophoneagent.agent.model.*
import com.example.maophoneagent.agent.runtime.AgentLoopGuard
import org.junit.Assert.*
import org.junit.Test

class AgentLoopGuardTest {
    private val tap = AgentToolCall.Tap(100, 200)
    private fun observe(text: String = "cats", time: Int = 1, success: Boolean = true) =
        AgentStep(AgentToolCall.Observe, AgentToolResult("observe", success, "Observed",
            uiJson = """{"capturedAtEpochMs":$time,"windows":[{"text":"$text"}]}"""))
    private fun action(call: AgentToolCall = tap) = AgentStep(call, AgentToolResult("action", true, "OK"))

    @Test fun captureTimeDoesNotHideRepeatedActionsOnUnchangedUi() {
        val history = listOf(observe(time = 1), action(), observe(time = 2), action(), observe(time = 3))
        assertEquals(2, AgentLoopGuard.repeatedActionCount(history, tap))
    }

    @Test fun changedUiOrDifferentActionBreaksTheRepeatSequence() {
        val history = listOf(observe("input"), action(), observe("results"), action(), observe("results"))
        assertEquals(1, AgentLoopGuard.repeatedActionCount(history, tap))
        assertEquals(0, AgentLoopGuard.repeatedActionCount(history, AgentToolCall.Tap(101, 200)))
        assertEquals(0, AgentLoopGuard.repeatedActionCount(history + action(AgentToolCall.Back) + observe("results"), tap))
    }

    @Test fun missingFailedOrStaleObservationsDoNotProveAStall() {
        val history = listOf(observe(), action(), observe(), action())
        assertEquals(0, AgentLoopGuard.repeatedActionCount(history, tap))
        assertEquals(0, AgentLoopGuard.repeatedActionCount(history + observe(success = false), tap))
        val screenshotOnly = AgentStep(AgentToolCall.Observe,
            AgentToolResult("observe", true, "Screenshot", screenshotPath = "screen.png"))
        assertEquals(0, AgentLoopGuard.repeatedActionCount(history + screenshotOnly, tap))
    }

    @Test fun repeatedSwipesRemainAvailable() {
        val swipe = AgentToolCall.Swipe(100, 500, 100, 100)
        val history = listOf(observe(), action(swipe), observe(), action(swipe), observe())
        assertEquals(0, AgentLoopGuard.repeatedActionCount(history, swipe))
    }
}
