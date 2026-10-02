package com.selfmod.agent.agent

import com.selfmod.agent.llm.ChatMessage
import com.selfmod.agent.llm.LlmRouter
import com.selfmod.agent.plugin.PluginRegistry
import com.selfmod.agent.repo.CodeRepository
import com.selfmod.agent.script.ScriptApi
import com.selfmod.agent.script.ScriptEngine
import com.selfmod.agent.store.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The agent's reasoning+acting loop. It maintains a chat history including
 * system prompt, sends it to the LLM with the current tool schema, dispatches
 * any returned tool calls against [ToolRegistry], appends the observations,
 * and repeats until the LLM answers without a tool call (or hits the cap).
 *
 * All steps are streamed to [onStep] so the UI can render a live trace.
 */
class AgentCore(
    private val llm: LlmRouter,
    private val settings: SettingsStore,
    scriptEngine: ScriptEngine,
    scriptHost: ScriptApi,
    repo: CodeRepository,
    plugins: PluginRegistry,
    private val uiNotifier: (String, String) -> Unit,
) {
    private val tools: ToolRegistry = ToolRegistry().apply {
        registerAll(Tools.all(scriptEngine, scriptHost, repo, plugins, settings, uiNotifier))
    }

    fun systemPrompt(): String {
        val local = settings.llmConfig().isLocal
        return PromptTemplates.systemFor(local, tools.names())
    }

    suspend fun run(
        history: MutableList<ChatMessage>,
        userText: String,
        onStep: (AgentStep) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        history.add(ChatMessage("user", userText))
        onStep(AgentStep.Started)

        var iteration = 0
        while (iteration++ < MAX_ITERATIONS) {
            val cfg = settings.llmConfig()
            var streamed = false
            val result = try {
                llm.chat(cfg, history, tools.specs()) { token ->
                    streamed = true
                    onStep(AgentStep.Thought(token))
                }
            } catch (e: Throwable) {
                onStep(AgentStep.Error(e.message ?: e.toString()))
                return@withContext "ERROR: ${e.message}"
            }

            history.add(
                ChatMessage(
                    role = "assistant",
                    content = result.content,
                    toolCalls = result.toolCalls,
                )
            )

            // 本地引擎已把 token 流式推成多个 Thought，避免再整段重复一次。
            if (!streamed && result.content.isNotBlank()) {
                onStep(AgentStep.Thought(result.content))
            }

            if (result.toolCalls.isEmpty()) {
                onStep(AgentStep.Answer(result.content))
                return@withContext result.content
            }

            for (tc in result.toolCalls) {
                val name = tc.function.name
                val args = tc.function.arguments
                onStep(AgentStep.Action(name, args))
                val out = tools.invoke(name, args)
                val trimmed = if (out.length > 6000) out.substring(0, 6000) + "...(truncated)" else out
                onStep(AgentStep.Observation(trimmed))
                history.add(
                    ChatMessage(
                        role = "tool",
                        content = out,
                        name = name,
                        toolCallId = tc.id,
                    )
                )
            }
        }
        val msg = "已达到最大推理步数 ($MAX_ITERATIONS)，请缩小任务或换用更具体的指令。"
        onStep(AgentStep.Error(msg))
        msg
    }

    companion object {
        private const val MAX_ITERATIONS = 12
    }
}
