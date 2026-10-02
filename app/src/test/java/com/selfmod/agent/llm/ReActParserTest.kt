package com.selfmod.agent.llm

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReActParserTest {

    @Test
    fun `parses action with json object input`() {
        val raw = """
            Thought: 我先看看有哪些脚本。
            Action: list_scripts
            Action Input: {}
        """.trimIndent()

        val result = ReActParser.parse(raw)

        assertEquals(1, result.toolCalls.size)
        val call = result.toolCalls.first()
        assertEquals("list_scripts", call.function.name)
        assertEquals("{}", call.function.arguments)
        assertEquals("tool_calls", result.finishReason)
        assertEquals("我先看看有哪些脚本。", result.content)
        assertTrue(call.id.isNotBlank())
    }

    @Test
    fun `parses action with nested json and prose around markers`() {
        val raw = """
            好的，我来写一个脚本。
            Thought: 写入 greet 脚本
            Action: write_script
            Action Input: {"name": "greet", "content": "log('hi')"}
            多余的说明文字
        """.trimIndent()

        val result = ReActParser.parse(raw)

        assertEquals(1, result.toolCalls.size)
        val args = JSONObject(result.toolCalls.first().function.arguments)
        assertEquals("greet", args.getString("name"))
        assertEquals("log('hi')", args.getString("content"))
        assertEquals("write_script", result.toolCalls.first().function.name)
    }

    @Test
    fun `parses final answer without tool call`() {
        val raw = """
            Thought: 已经完成。
            Final Answer: 已为你创建脚本 greet。
        """.trimIndent()

        val result = ReActParser.parse(raw)

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("stop", result.finishReason)
        assertTrue(result.content.contains("已为你创建脚本 greet。"))
    }

    @Test
    fun `prefers final answer when it appears before action`() {
        val raw = """
            Thought: 结束
            Final Answer: 完成
            Action: list_scripts
        """.trimIndent()

        val result = ReActParser.parse(raw)

        assertTrue(result.toolCalls.isEmpty())
        assertTrue(result.content.contains("完成"))
    }

    @Test
    fun `falls back to plain text when no markers`() {
        val raw = "这是一段普通回复，没有协议标记。"

        val result = ReActParser.parse(raw)

        assertTrue(result.toolCalls.isEmpty())
        assertEquals(raw, result.content)
    }

    @Test
    fun `empty input yields empty content`() {
        val result = ReActParser.parse("   ")

        assertTrue(result.toolCalls.isEmpty())
        assertEquals("", result.content)
    }

    @Test
    fun `action input missing defaults to empty json`() {
        val raw = """
            Action: list_scripts
        """.trimIndent()

        val result = ReActParser.parse(raw)

        assertEquals(1, result.toolCalls.size)
        assertEquals("{}", result.toolCalls.first().function.arguments)
    }

    @Test
    fun `extractJson handles array payload`() {
        val json = ReActParser.extractJson("[1, 2, 3] trailing")
        val obj = JSONObject(json)
        val arr: JSONArray = obj.getJSONArray("items")
        assertEquals(3, arr.length())
    }

    @Test
    fun `extractJson ignores braces inside strings`() {
        val blob = """{"code": "if (x) { return '{'; }"}"""
        val json = ReActParser.extractJson(blob)
        assertEquals(blob, json)
        assertEquals("if (x) { return '{'; }", JSONObject(json).getString("code"))
    }

    @Test
    fun `extractJson wraps non json string as value`() {
        val json = ReActParser.extractJson("just some text")
        assertEquals("just some text", JSONObject(json).getString("value"))
    }

    @Test
    fun `extractJson empty blob returns empty object`() {
        assertEquals("{}", ReActParser.extractJson("   "))
    }
}
