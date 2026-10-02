package com.selfmod.agent.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry used by AgentCore and ScriptHost. Remote OpenAI-compatible
 * backends keep native function-calling; local engines get a flattened
 * prompt plus ReAct parsing so they can still drive tools.
 */
class LlmRouter(
    private val remote: LlmClient,
    private val engines: List<LocalLlmEngine>,
) {
    fun engine(kind: BackendKind): LocalLlmEngine? = engines.firstOrNull { it.id == kind.toEngineId() }

    fun availableEngines(): List<LocalLlmEngine> = engines

    fun chat(
        config: LlmConfig,
        messages: List<ChatMessage>,
        tools: List<ToolSpec> = emptyList(),
        onToken: (String) -> Unit = {},
    ): ChatResult {
        if (!config.isLocal) {
            return remote.chat(config, messages, tools)
        }
        val engine = engine(config.backend)
            ?: throw LocalEngineException("未知本地后端: ${config.backend}")
        if (!engine.isAvailable) {
            throw LocalEngineException("${engine.displayName} 当前不可用")
        }
        if (config.localModelPath.isBlank()) {
            throw LocalEngineException("请先在配置页设置本地模型路径")
        }
        if (!engine.isLoaded || engine.loadedModelPath != config.localModelPath) {
            engine.load(config)
        }
        val prompt = PromptFormatter.format(messages, config.backend)
        Log.d(TAG, "local generate backend=${config.backend} promptChars=${prompt.length}")
        val raw = engine.generate(prompt, config, onToken)
        return if (tools.isEmpty()) {
            ChatResult(content = raw, raw = raw, finishReason = "stop")
        } else {
            ReActParser.parse(raw)
        }
    }

    suspend fun chatAsync(
        config: LlmConfig,
        messages: List<ChatMessage>,
        tools: List<ToolSpec> = emptyList(),
        onToken: (String) -> Unit = {},
    ): ChatResult = withContext(Dispatchers.IO) {
        chat(config, messages, tools, onToken)
    }

    fun unloadAll() {
        engines.forEach { runCatching { it.unload() } }
    }

    companion object {
        private const val TAG = "LlmRouter"
    }
}

private fun BackendKind.toEngineId(): String = when (this) {
    BackendKind.MEDIAPIPE -> "mediapipe"
    BackendKind.MNN -> "mnn"
    BackendKind.REMOTE -> ""
}
