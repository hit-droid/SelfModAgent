package com.selfmod.agent.llm

import com.selfmod.agent.agent.PromptTemplates
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptFormatterTest {

    private val messages = listOf(
        ChatMessage("system", "你是助手"),
        ChatMessage("user", "你好"),
        ChatMessage("assistant", "在的"),
        ChatMessage("tool", """{"ok":true}""", name = "list_scripts"),
    )

    @Test
    fun `gemma format uses turn markers and ends with model turn`() {
        val out = PromptFormatter.gemma(messages)

        assertTrue(out.contains("<start_of_turn>user\nSYSTEM:\n你是助手<end_of_turn>"))
        assertTrue(out.contains("<start_of_turn>user\n你好<end_of_turn>"))
        assertTrue(out.contains("<start_of_turn>model\n在的<end_of_turn>"))
        assertTrue(out.contains("[tool list_scripts]"))
        assertTrue(out.endsWith("<start_of_turn>model\n"))
    }

    @Test
    fun `chatml format maps tool role to user and closes im_start`() {
        val out = PromptFormatter.chatml(messages)

        assertTrue(out.contains("<|im_start|>system\n你是助手<|im_end|>"))
        assertTrue(out.contains("<|im_start|>user\n你好<|im_end|>"))
        assertTrue(out.contains("<|im_start|>assistant\n在的<|im_end|>"))
        assertTrue(out.contains("<|im_start|>user\n[tool list_scripts]"))
        assertTrue(out.endsWith("<|im_start|>assistant\n"))
    }

    @Test
    fun `format dispatches by backend kind`() {
        assertTrue(PromptFormatter.format(messages, BackendKind.MEDIAPIPE).contains("<start_of_turn>"))
        assertTrue(PromptFormatter.format(messages, BackendKind.MNN).contains("<|im_start|>"))
    }

    @Test
    fun `format rejects remote backend`() {
        var failed = false
        try {
            PromptFormatter.format(messages, BackendKind.REMOTE)
        } catch (e: IllegalStateException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `react addendum lists tools and protocol markers`() {
        val addendum = PromptTemplates.reactAddendum(listOf("execute_js", "list_scripts"))

        listOf("Thought:", "Action:", "Action Input:", "Final Answer:").forEach {
            assertTrue("missing $it", addendum.contains(it))
        }
        assertTrue(addendum.contains("execute_js"))
        assertTrue(addendum.contains("list_scripts"))
    }

    @Test
    fun `systemFor appends react protocol only for local`() {
        val remote = PromptTemplates.systemFor(false, listOf("list_scripts"))
        val local = PromptTemplates.systemFor(true, listOf("list_scripts"))

        assertFalse(remote.contains("Final Answer:"))
        assertTrue(local.contains("Final Answer:"))
        assertTrue(local.length > remote.length)
    }
}
