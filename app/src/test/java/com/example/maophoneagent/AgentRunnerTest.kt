package com.example.maophoneagent

import com.example.maophoneagent.agent.model.*
import com.example.maophoneagent.agent.planner.AgentPlanner
import com.example.maophoneagent.agent.planner.ScriptedAgentPlanner
import com.example.maophoneagent.agent.runtime.AgentRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AgentRunnerTest {
    private val session = AgentSession(1)
    private fun success(call: AgentToolCall) = AgentToolResult(call.toString(), true, "OK")

    @Test fun scriptRunsInOrderAndReportsThreeCompletedSteps() = runBlocking {
        val calls = mutableListOf<AgentToolCall>()
        val histories = mutableListOf<List<AgentStep>>()
        val runner = AgentRunner(ScriptedAgentPlanner(settleDelayMs = 0)) { actualSession, call ->
            assertEquals(session, actualSession)
            calls += call
            success(call)
        }
        val outcome = runner.run("Test Settings", session, maxToolCalls = 3) { _, history ->
            histories += history
        }
        assertTrue(outcome.completed)
        assertEquals(listOf(AgentToolCall.Observe, AgentToolCall.LaunchApp("com.android.settings"),
            AgentToolCall.Observe), calls)
        assertEquals(calls, outcome.steps.map { it.call })
        assertEquals(3, outcome.steps.size)
        assertTrue(histories.first().isEmpty())
        assertEquals(3, histories.last().size)
    }

    @Test fun scriptStopsOnFailureAndPreservesTheFailedStep() = runBlocking {
        var calls = 0
        val runner = AgentRunner(ScriptedAgentPlanner(settleDelayMs = 0)) { _, _ ->
            calls++
            AgentToolResult("observe", false, "Accessibility service is not connected")
        }
        val outcome = runner.run("Test", session)
        assertFalse(outcome.completed)
        assertEquals(1, calls)
        assertEquals(1, outcome.steps.size)
        assertTrue(outcome.message.contains("Accessibility service is not connected"))
    }

    @Test fun toolFailureIsPassedToThePlannerForItsNextDecision() = runBlocking {
        val planner = object : AgentPlanner {
            override suspend fun nextDecision(task: String, history: List<AgentStep>): AgentDecision {
                if (history.isEmpty()) return AgentDecision.CallTool(AgentToolCall.Observe)
                assertFalse(history.single().result.success)
                return AgentDecision.Complete("Failure inspected by planner")
            }
        }
        val runner = AgentRunner(planner) { _, _ -> AgentToolResult("observe", false, "Unavailable") }
        val outcome = runner.run("Test", session)
        assertTrue(outcome.completed)
        assertEquals("Failure inspected by planner", outcome.message)
    }

    @Test fun endlessToolRequestsStopAtTheConfiguredLimit() = runBlocking {
        val planner = object : AgentPlanner {
            override suspend fun nextDecision(task: String, history: List<AgentStep>) =
                AgentDecision.CallTool(AgentToolCall.Observe)
        }
        var calls = 0
        val runner = AgentRunner(planner) { _, call -> calls++; success(call) }
        val outcome = runner.run("Test", session, maxToolCalls = 2)
        assertFalse(outcome.completed)
        assertEquals(2, calls)
        assertEquals(2, outcome.steps.size)
        assertTrue(outcome.message.contains("Tool call limit"))
    }

    @Test(timeout = 5000) fun taskTimeoutStopsWaitingForThePlanner() = runBlocking {
        val planner = object : AgentPlanner {
            override suspend fun nextDecision(task: String, history: List<AgentStep>): AgentDecision {
                delay(10_000)
                return AgentDecision.Complete("Too late")
            }
        }
        val runner = AgentRunner(planner) { _, call -> success(call) }
        val outcome = runner.run("Test", session, timeoutMs = 50)
        assertFalse(outcome.completed)
        assertTrue(outcome.steps.isEmpty())
        assertTrue(outcome.message.contains("timed out"))
    }

    @Test(timeout = 5000) fun cancellationReleasesTheRunnerAndConcurrentTasksAreRejected() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var wait = true
        val planner = object : AgentPlanner {
            override suspend fun nextDecision(task: String, history: List<AgentStep>): AgentDecision {
                entered.complete(Unit)
                if (wait) awaitCancellation()
                return AgentDecision.Complete("Done")
            }
        }
        val runner = AgentRunner(planner) { _, call -> success(call) }
        val job = launch { runner.run("First task", session) }
        entered.await()
        val concurrent = runner.run("Second task", session)
        assertFalse(concurrent.completed)
        assertEquals("Another task is running", concurrent.message)
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        wait = false
        assertTrue(runner.run("After cancellation", session).completed)
    }
}
