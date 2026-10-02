package com.selfmod.agent.llm

import org.json.JSONObject
import java.util.UUID

/**
 * Parses a local-model completion into either a final answer or a tool call.
 *
 * Protocol the model is instructed to emit:
 *
 *     Thought: <short plan>
 *     Action: <tool_name>
 *     Action Input: { ...json... }
 *
 * or, when done:
 *
 *     Thought: <short plan>
 *     Final Answer: <user-facing text>
 *
 * Extra junk around the markers is tolerated. If nothing matches, the whole
 * text is treated as a final answer so the loop always terminates.
 */
object ReActParser {

    private val ACTION = Regex(
        """(?is)Action\s*:\s*([A-Za-z0-9_]+)""",
    )
    private val ACTION_INPUT = Regex(
        """(?is)Action\s*Input\s*:\s*(.+)""",
    )
    private val FINAL = Regex(
        """(?is)Final\s*Answer\s*:\s*(.+)""",
    )
    private val THOUGHT = Regex(
        """(?is)Thought\s*:\s*(.+?)(?=\n\s*(?:Action|Final\s*Answer)\s*:|$)""",
    )

    fun parse(raw: String): ChatResult {
        val text = raw.trim()
        if (text.isEmpty()) {
            return ChatResult(content = "", finishReason = "stop", raw = raw)
        }

        val thought = THOUGHT.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val finalMatch = FINAL.find(text)
        val actionMatch = ACTION.find(text)

        // Prefer Final Answer when both appear (model sometimes restates a plan).
        if (finalMatch != null && (actionMatch == null || finalMatch.range.first < actionMatch.range.first)) {
            val answer = finalMatch.groupValues[1].trim()
            val content = listOf(thought, answer).filter { it.isNotBlank() }.joinToString("\n")
            return ChatResult(content = content.ifBlank { answer }, finishReason = "stop", raw = raw)
        }

        if (actionMatch != null) {
            val name = actionMatch.groupValues[1].trim()
            val inputBlob = ACTION_INPUT.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val args = extractJson(inputBlob).ifBlank { "{}" }
            val call = ToolCall(
                id = "call_" + UUID.randomUUID().toString().replace("-", "").take(12),
                function = ToolFunction(name = name, arguments = args),
            )
            return ChatResult(
                content = thought,
                toolCalls = listOf(call),
                finishReason = "tool_calls",
                raw = raw,
            )
        }

        return ChatResult(content = text, finishReason = "stop", raw = raw)
    }

    /** Pull the first JSON object/array out of a blob that may contain prose. */
    internal fun extractJson(blob: String): String {
        val s = blob.trim()
        if (s.isEmpty()) return "{}"
        val startObj = s.indexOf('{')
        val startArr = s.indexOf('[')
        val start = when {
            startObj < 0 && startArr < 0 -> return tryWrap(s)
            startObj < 0 -> startArr
            startArr < 0 -> startObj
            else -> minOf(startObj, startArr)
        }
        val open = s[start]
        val close = if (open == '{') '}' else ']'
        var depth = 0
        var inStr = false
        var escape = false
        for (i in start until s.length) {
            val c = s[i]
            when {
                escape -> escape = false
                c == '\\' && inStr -> escape = true
                c == '"' -> inStr = !inStr
                inStr -> {}
                c == open -> depth++
                c == close -> {
                    depth--
                    if (depth == 0) {
                        val json = s.substring(start, i + 1)
                        return if (open == '{') json else """{"items":$json}"""
                    }
                }
            }
        }
        return tryWrap(s)
    }

    private fun tryWrap(s: String): String {
        return runCatching { JSONObject(s).toString() }.getOrElse {
            JSONObject().put("value", s).toString()
        }
    }
}
