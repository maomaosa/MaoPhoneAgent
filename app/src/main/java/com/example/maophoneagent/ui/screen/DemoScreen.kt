/*
 * File: DemoScreen.kt
 * Module: ui.screen
 *
 * Description:
 * Provides a compact demo for service setup and natural-language Agent tasks.
 * Shows live execution progress between connection controls and the task composer.
 * API credentials stay in memory and are configured through a separate dialog.
 */
package com.example.maophoneagent.ui.screen

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.maophoneagent.agent.ai.OpenAiClient
import com.example.maophoneagent.agent.model.*
import com.example.maophoneagent.agent.planner.OpenAiAgentPlanner
import com.example.maophoneagent.agent.runtime.AgentRunner
import com.example.maophoneagent.agent.tool.PhoneToolExecutor
import com.example.maophoneagent.infrastructure.accessibility.PhoneAccessibilityService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Hosts demo controls while connection lifecycle remains owned by MainActivity. */
@Composable
fun DemoScreen(
    executor: PhoneToolExecutor,
    shizukuStatus: String,
    shellConnected: Boolean,
    onRequestShizukuPermission: () -> Unit,
    onRefreshShizukuStatus: () -> Unit,
    onConnectShell: () -> Unit,
    onOpenTests: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accessibilityConnected by PhoneAccessibilityService.connected.collectAsStateWithLifecycle()
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var apiKey by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("gpt-5.6-luna") }
    var displayInput by remember { mutableStateOf("1") }
    var task by remember { mutableStateOf("") }
    var showAiSettings by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf("连接服务后，输入你想完成的任务。") }
    var steps by remember { mutableStateOf<List<AgentStep>>(emptyList()) }
    var finished by remember { mutableStateOf<Boolean?>(null) }
    val displayId = displayInput.toIntOrNull()
    val ready = debug && accessibilityConnected && shellConnected && apiKey.isNotBlank() && model.isNotBlank()
    val canRun = ready && !busy && displayId != null && displayId > 0 && task.isNotBlank()
    val resultScroll = rememberScrollState()

    // Keeps the latest progress visible without scrolling the connection and input areas away.
    LaunchedEffect(steps.size, status) { resultScroll.animateScrollTo(resultScroll.maxValue) }

    fun runTask() {
        if (!canRun) return
        val id = displayId ?: return
        val description = task.trim()
        val key = apiKey.trim()
        val selectedModel = model.trim()
        busy = true
        steps = emptyList()
        finished = null
        status = "正在开始任务…"
        job = scope.launch {
            try {
                val planner = OpenAiAgentPlanner(OpenAiClient(key), selectedModel)
                val runner = AgentRunner(planner, executor::execute)
                // Search tasks need several action/observation pairs beyond app launch.
                val outcome = runner.run(description, AgentSession(id), maxToolCalls = 20,
                    timeoutMs = 180_000L) { message, history ->
                    steps = history
                    status = if (message == "Requesting next decision...") "正在分析下一步…"
                        else if (message.startsWith("Executing step")) "正在执行操作…"
                        else "已执行 ${history.size} 步，准备继续…"
                }
                steps = outcome.steps
                finished = outcome.completed
                status = outcome.message
            } catch (e: CancellationException) {
                status = "任务已停止。已经执行的操作不会撤销。"
                throw e
            } catch (e: Exception) {
                finished = false
                status = "运行失败：${e.message?.replace(key, "[redacted]")}"
            } finally {
                busy = false
                job = null
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("MaoPhoneAgent", style = MaterialTheme.typography.titleLarge)
                    Text("虚拟屏幕助手 · Demo", style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    TextButton(enabled = !busy && debug, onClick = { showAiSettings = true }) { Text("AI 设置") }
                    TextButton(enabled = !busy, onClick = onOpenTests) { Text("测试") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("无障碍：${if (accessibilityConnected) "已连接" else "未连接"}",
                            modifier = Modifier.weight(1f).padding(top = 10.dp))
                        TextButton(enabled = !busy, onClick = {
                            try { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            catch (_: ActivityNotFoundException) { status = "无法打开无障碍设置，请在系统设置中启用服务。" }
                        }) { Text("打开设置") }
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Shizuku", modifier = Modifier.weight(1f).padding(top = 10.dp))
                        TextButton(enabled = !busy, onClick = onRequestShizukuPermission) { Text("连接 / 授权") }
                        TextButton(enabled = !busy, onClick = onRefreshShizukuStatus) { Text("刷新") }
                    }
                    Text(shizukuStatus, style = MaterialTheme.typography.bodySmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("UserService：${if (shellConnected) "已连接" else "未连接"}",
                            modifier = Modifier.weight(1f).padding(top = 10.dp))
                        TextButton(enabled = !busy, onClick = onConnectShell) { Text("连接") }
                    }
                }
            }
            OutlinedTextField(displayInput, { displayInput = it }, label = { Text("目标屏幕 ID") },
                supportingText = { Text("填写实际屏幕 ID，必须大于 0") },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                isError = displayInput.isNotEmpty() && (displayId == null || displayId <= 0))

            // Uses remaining height for a separately scrollable result panel.
            OutlinedCard(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(resultScroll).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (busy) "正在运行" else if (finished == true) "任务完成"
                        else if (finished == false) "任务未完成" else "运行结果",
                        style = MaterialTheme.typography.titleMedium)
                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    SelectionContainer { Text(status) }
                    steps.forEachIndexed { index, step ->
                        HorizontalDivider()
                        Text("${index + 1}. ${demoToolLabel(step.call)}", style = MaterialTheme.typography.titleSmall)
                        val detail = if (!step.result.success) step.result.message else when (step.call) {
                            AgentToolCall.Observe -> "读取 ${step.result.nodeCount} 个控件 · ${if (step.result.screenshotPath != null) "已截图" else "未取得截图"}"
                            AgentToolCall.ListApps -> "已读取 ${step.result.message.lineSequence().count { it.trim().startsWith("package:") }} 个应用包名"
                            else -> "操作执行成功"
                        }
                        SelectionContainer { Text(detail, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            OutlinedTextField(task, { task = it }, label = { Text("你想做什么？") },
                placeholder = { Text("例如：打开设置") }, modifier = Modifier.fillMaxWidth(),
                enabled = !busy, minLines = 2, maxLines = 3)
            if (!ready && !busy) Text(
                if (!debug) "当前 Demo 的直接 API 配置仅用于 Debug 构建。"
                else if (apiKey.isBlank()) "请先在 AI 设置中输入 API Key。"
                else "请连接无障碍服务和 Shell UserService。",
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = canRun, onClick = { runTask() }, modifier = Modifier.weight(1f)) { Text("运行任务") }
                OutlinedButton(enabled = busy, onClick = { status = "正在停止…"; job?.cancel() }) { Text("停止") }
            }
        }
    }

    if (showAiSettings) AlertDialog(onDismissRequest = { showAiSettings = false },
        title = { Text("AI 设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("配置仅保存在当前页面内存中。任务画面与信息将发送给 OpenAI。")
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(model, { model = it }, label = { Text("模型 ID") }, singleLine = true)
            }
        }, confirmButton = { TextButton(onClick = { showAiSettings = false }) { Text("完成") } })
}

/** Presents existing tool requests as readable demo progress. */
private fun demoToolLabel(call: AgentToolCall): String = when (call) {
    AgentToolCall.Observe -> "观察屏幕"
    AgentToolCall.ListApps -> "读取应用列表"
    is AgentToolCall.LaunchApp -> "打开应用 ${call.packageName}"
    is AgentToolCall.Tap -> "点击 (${call.x}, ${call.y})"
    is AgentToolCall.Swipe -> "滑动屏幕"
    is AgentToolCall.InputText -> "输入文字 ${call.text}"
    AgentToolCall.SubmitInput -> "提交输入 / 搜索"
    AgentToolCall.Home -> "返回桌面"
    AgentToolCall.Back -> "返回上一页"
}
