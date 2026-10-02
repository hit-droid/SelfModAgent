package com.selfmod.agent.llm

/**
 * Turns a chat history into a single prompt string that local engines can
 * consume. Gemma (MediaPipe) uses turn markers; Qwen / ChatML (MNN) uses
 * im_start / im_end. Remote OpenAI backends never go through this.
 */
object PromptFormatter {

    fun format(messages: List<ChatMessage>, kind: BackendKind): String = when (kind) {
        BackendKind.MEDIAPIPE -> gemma(messages)
        BackendKind.MNN -> chatml(messages)
        BackendKind.REMOTE -> error("remote backends do not use PromptFormatter")
    }

    fun gemma(messages: List<ChatMessage>): String = buildString {
        messages.forEach { m ->
            when (m.role) {
                "system" -> {
                    append("<start_of_turn>user\nSYSTEM:\n")
                    append(m.content.trim())
                    append("<end_of_turn>\n")
                }
                "user" -> {
                    append("<start_of_turn>user\n")
                    append(m.content.trim())
                    append("<end_of_turn>\n")
                }
                "assistant" -> {
                    append("<start_of_turn>model\n")
                    append(m.content.trim())
                    append("<end_of_turn>\n")
                }
                "tool" -> {
                    append("<start_of_turn>user\n")
                    append("[tool ${m.name ?: "unknown"}]\n")
                    append(m.content.trim())
                    append("<end_of_turn>\n")
                }
            }
        }
        append("<start_of_turn>model\n")
    }

    fun chatml(messages: List<ChatMessage>): String = buildString {
        messages.forEach { m ->
            val role = when (m.role) {
                "tool" -> "user"
                else -> m.role
            }
            append("<|im_start|>")
            append(role)
            append('\n')
            if (m.role == "tool") {
                append("[tool ${m.name ?: "unknown"}]\n")
            }
            append(m.content.trim())
            append("<|im_end|>\n")
        }
        append("<|im_start|>assistant\n")
    }
}
