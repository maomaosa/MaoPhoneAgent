/*
 * File: OpenAiClient.kt
 * Module: agent.ai
 *
 * Description:
 * Sends HTTPS requests to OpenAI Responses using a caller-supplied, in-memory API key.
 * No credentials, screenshots, requests, or responses are written to logs.
 */
package com.example.maophoneagent.agent.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Direct API access is for local testing; a distributed app should use a backend. */
class OpenAiClient(private val apiKey: String) {
    init {
        require(apiKey.isNotBlank() && apiKey.none { it == '\r' || it == '\n' }) { "Invalid API key" }
    }

    /** Posts one request and returns the raw response, with network cancellation and timeouts. */
    suspend fun respond(request: JSONObject): JSONObject = coroutineScope {
        suspendCancellableCoroutine { continuation ->
            val connection = URL("https://api.openai.com/v1/responses").openConnection() as HttpsURLConnection
            continuation.invokeOnCancellation { connection.disconnect() }
            launch(Dispatchers.IO) {
                try {
                    connection.requestMethod = "POST"
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 45_000
                    connection.doOutput = true
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (code !in 200..299) {
                        val serverMessage = runCatching {
                            JSONObject(body).optJSONObject("error")?.optString("message")
                        }.getOrNull().orEmpty().replace(apiKey, "[redacted]").take(1000)
                        error("OpenAI HTTP $code: $serverMessage")
                    }
                    val result = JSONObject(body)
                    if (continuation.isActive) continuation.resume(result)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}
