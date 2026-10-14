package com.example.maophoneagent

import com.example.maophoneagent.agent.ai.OpenAiToolProtocol
import com.example.maophoneagent.agent.model.AgentDecision
import com.example.maophoneagent.agent.model.AgentToolCall
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** Tests JSON protocol validation on Android without keys or network requests. */
class OpenAiToolProtocolTest {
    private fun response(name: String, arguments: String) = JSONObject()
        .put("status", "completed").put("output", JSONArray().put(JSONObject()
            .put("type", "function_call").put("name", name).put("arguments", arguments)))

    @Test fun parsesSupportedCallsAndCompletion() {
        assertEquals(AgentDecision.CallTool(AgentToolCall.Tap(10, 20)),
            OpenAiToolProtocol.parse(response("tap", """{"x":10,"y":20}""")))
        assertEquals(AgentDecision.CallTool(AgentToolCall.ListApps),
            OpenAiToolProtocol.parse(response("list_apps", "{}")))
        assertEquals(AgentDecision.Complete("Done"),
            OpenAiToolProtocol.parse(response("complete", """{"message":"Done"}""")))
    }

    @Test fun schemasRejectExtraFieldsAndRequireDeclaredArguments() {
        val tools = OpenAiToolProtocol.tools()
        assertEquals(10, tools.length())
        for (i in 0 until tools.length()) {
            val tool = tools.getJSONObject(i)
            assertTrue(tool.getBoolean("strict"))
            val schema = tool.getJSONObject("parameters")
            assertFalse(schema.getBoolean("additionalProperties"))
            assertEquals(schema.getJSONObject("properties").length(), schema.getJSONArray("required").length())
        }
    }

    @Test fun textInputPreservesUnicodeWhitespaceAndAllowsClearing() {
        listOf("猫咪 视频 with spaces\nsecond line", "", "a".repeat(2000)).forEach { text ->
            assertEquals(AgentDecision.CallTool(AgentToolCall.InputText(text)),
                OpenAiToolProtocol.parse(response("input_text", JSONObject().put("text", text).toString())))
        }
        assertEquals(AgentDecision.CallTool(AgentToolCall.SubmitInput),
            OpenAiToolProtocol.parse(response("submit_input", "{}")))
    }

    @Test fun rejectsInvalidCoordinatesAndUnexpectedTools() {
        listOf(
            response("tap", """{"x":-1,"y":0}"""),
            response("tap", """{"x":1.5,"y":0}"""),
            response("tap", """{"x":"1","y":0}"""),
            response("tap", """{"x":1,"y":0,"displayId":0}"""),
            response("launch_app", """{"packageName":"com.app;id"}"""),
            response("swipe", """{"startX":0,"startY":0,"endX":1,"endY":1,"durationMs":0}"""),
            response("input_text", "{}"),
            response("input_text", """{"text":123}"""),
            response("input_text", JSONObject().put("text", "a".repeat(2001)).toString()),
            response("input_text", """{"text":"hello","displayId":0}"""),
            response("submit_input", """{"text":"hello"}"""),
            response("shell", "{}")
        ).forEach { value ->
            try { OpenAiToolProtocol.parse(value); fail("Invalid decision accepted") }
            catch (_: IllegalArgumentException) { }
            catch (_: IllegalStateException) { }
        }
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsMultipleToolCalls() {
        val value = response("home", "{}")
        value.getJSONArray("output").put(value.getJSONArray("output").getJSONObject(0))
        OpenAiToolProtocol.parse(value)
    }

    @Test(expected = IllegalStateException::class) fun rejectsIncompleteResponses() {
        OpenAiToolProtocol.parse(response("home", "{}").put("status", "incomplete"))
    }
}
