package com.selfmod.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.selfmod.agent.llm.BackendKind
import com.selfmod.agent.llm.LlmConfig
import com.selfmod.agent.ui.theme.AccentBlue
import com.selfmod.agent.ui.theme.AccentGreen
import com.selfmod.agent.ui.theme.Danger
import com.selfmod.agent.ui.theme.TextPrimary
import com.selfmod.agent.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfigScreen(vm: AgentViewModel) {
    val initial = remember { vm.config() }
    var backend by remember { mutableStateOf(initial.backend) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }
    var tempStr by remember { mutableStateOf(initial.temperature.toString()) }
    var maxTokStr by remember { mutableStateOf(initial.maxTokens.toString()) }
    var localPath by remember { mutableStateOf(initial.localModelPath) }
    var localBackend by remember { mutableStateOf(initial.localBackend) }
    var localMaxTok by remember { mutableStateOf(initial.localMaxTokens.toString()) }
    var localTopK by remember { mutableStateOf(initial.localTopK.toString()) }
    var localTopP by remember { mutableStateOf(initial.localTopP.toString()) }
    var saved by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("推理后端", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(
            "远程接口支持原生 function calling；本地引擎离线运行，用文本 ReAct 协议驱动工具。",
            color = TextSecondary,
            fontSize = 13.sp,
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = backend == BackendKind.REMOTE,
                onClick = { backend = BackendKind.REMOTE; saved = false },
                label = { Text("远程 API") },
            )
            FilterChip(
                selected = backend == BackendKind.MEDIAPIPE,
                onClick = { backend = BackendKind.MEDIAPIPE; saved = false },
                label = { Text("本地 · MediaPipe") },
            )
            FilterChip(
                selected = backend == BackendKind.MNN,
                onClick = { backend = BackendKind.MNN; saved = false },
                label = { Text("本地 · MNN") },
            )
        }

        val engineStatus = vm.localEngines().firstOrNull {
            it.id == if (backend == BackendKind.MNN) "mnn" else "mediapipe"
        }
        if (backend != BackendKind.REMOTE && engineStatus != null) {
            Text(
                if (engineStatus.isAvailable) "引擎状态: 可用" else "引擎状态: 不可用（原生库未打包）",
                color = if (engineStatus.isAvailable) AccentGreen else Danger,
                fontSize = 13.sp,
            )
        }

        if (backend == BackendKind.REMOTE) {
            RemoteSection(
                baseUrl = baseUrl, onBaseUrl = { baseUrl = it; saved = false },
                apiKey = apiKey, onApiKey = { apiKey = it; saved = false },
                model = model, onModel = { model = it; saved = false },
                tempStr = tempStr, onTemp = { tempStr = it; saved = false },
                maxTokStr = maxTokStr, onMaxTok = { maxTokStr = it; saved = false },
            )
        } else {
            LocalSection(
                backend = backend,
                path = localPath, onPath = { localPath = it; saved = false },
                localBackend = localBackend, onLocalBackend = { localBackend = it; saved = false },
                maxTok = localMaxTok, onMaxTok = { localMaxTok = it; saved = false },
                topK = localTopK, onTopK = { localTopK = it; saved = false },
                topP = localTopP, onTopP = { localTopP = it; saved = false },
                tempStr = tempStr, onTemp = { tempStr = it; saved = false },
            )
        }

        Row {
            Button(onClick = {
                val cfg = LlmConfig(
                    backend = backend,
                    baseUrl = baseUrl.trim(),
                    apiKey = apiKey.trim(),
                    model = model.trim(),
                    temperature = tempStr.trim().toDoubleOrNull() ?: 0.6,
                    maxTokens = maxTokStr.trim().toIntOrNull() ?: 2048,
                    localModelPath = localPath.trim(),
                    localBackend = localBackend.trim(),
                    localMaxTokens = localMaxTok.trim().toIntOrNull() ?: 1024,
                    localTopK = localTopK.trim().toIntOrNull() ?: 40,
                    localTopP = localTopP.trim().toDoubleOrNull() ?: 0.9,
                )
                vm.setConfig(cfg)
                saved = true
            }) {
                Text("保存")
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = { vm.unloadLocalModels(); saved = false }) {
                Text("卸载本地模型")
            }
            Spacer(Modifier.width(12.dp))
            if (saved) Text("已保存 ✓", color = AccentGreen, modifier = Modifier.padding(top = 12.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text("记忆 (store.get/set)", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        val keys = remember { vm.settings().memoryKeys() }
        if (keys.isEmpty()) {
            Text("（空）", color = TextSecondary, fontSize = 13.sp)
        } else {
            keys.forEach { k ->
                val v = vm.settings().memoryGet(k)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("$k = ", color = AccentGreen, fontSize = 13.sp)
                    Text((v ?: "").take(80), color = TextPrimary, fontSize = 13.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RemoteSection(
    baseUrl: String, onBaseUrl: (String) -> Unit,
    apiKey: String, onApiKey: (String) -> Unit,
    model: String, onModel: (String) -> Unit,
    tempStr: String, onTemp: (String) -> Unit,
    maxTokStr: String, onMaxTok: (String) -> Unit,
) {
    Text("远程 LLM 配置", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
    Text(
        "OpenAI 兼容接口均可。Key 仅存储在本机应用沙箱内。",
        color = TextSecondary, fontSize = 13.sp,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LlmConfig.PRESETS.filter { !it.second.isLocal }.forEach { (name, cfg) ->
            AssistChip(
                onClick = { onBaseUrl(cfg.baseUrl); onModel(cfg.model) },
                label = { Text(name) },
            )
        }
    }
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrl,
        label = { Text("Base URL (OpenAI 兼容)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKey,
        label = { Text("API Key") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
    )
    OutlinedTextField(
        value = model,
        onValueChange = onModel,
        label = { Text("模型名 (如 glm-4-plus)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = tempStr,
            onValueChange = onTemp,
            label = { Text("temperature") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = maxTokStr,
            onValueChange = onMaxTok,
            label = { Text("max_tokens") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocalSection(
    backend: BackendKind,
    path: String, onPath: (String) -> Unit,
    localBackend: String, onLocalBackend: (String) -> Unit,
    maxTok: String, onMaxTok: (String) -> Unit,
    topK: String, onTopK: (String) -> Unit,
    topP: String, onTopP: (String) -> Unit,
    tempStr: String, onTemp: (String) -> Unit,
) {
    Text("本地模型配置", color = TextPrimary, style = MaterialTheme.typography.titleSmall)
    val hint = if (backend == BackendKind.MNN) {
        "MNN 目录：含 config.json 与权重的文件夹（arm64-v8a 原生库需自行编译放入 jniLibs）。"
    } else {
        "MediaPipe：单个 .task / .bin 模型文件（如 Gemma 2B）。GPU 后端在部分设备首次加载需 2-3 秒预热。"
    }
    Text(hint, color = TextSecondary, fontSize = 13.sp)

    OutlinedTextField(
        value = path,
        onValueChange = onPath,
        label = { Text(if (backend == BackendKind.MNN) "模型目录 (绝对路径)" else "模型文件 (绝对路径)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )

    Text("加速后端", color = TextSecondary, fontSize = 12.sp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val options = if (backend == BackendKind.MNN) {
            listOf("opencl" to "OpenCL (GPU)", "cpu" to "CPU")
        } else {
            listOf("gpu" to "GPU", "cpu" to "CPU")
        }
        options.forEach { (value, label) ->
            FilterChip(
                selected = localBackend == value,
                onClick = { onLocalBackend(value) },
                label = { Text(label) },
            )
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = maxTok,
            onValueChange = onMaxTok,
            label = { Text("max_new_tokens") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = tempStr,
            onValueChange = onTemp,
            label = { Text("temperature") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = topK,
            onValueChange = onTopK,
            label = { Text("topK") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = topP,
            onValueChange = onTopP,
            label = { Text("topP") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
    Text(
        "提示：把模型放在应用私有目录或通过 adb push 导入，路径填绝对路径。",
        color = AccentBlue, fontSize = 11.sp,
    )
}
