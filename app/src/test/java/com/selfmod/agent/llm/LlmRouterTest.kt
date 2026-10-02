package com.selfmod.agent.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 记录调用行为的假引擎，避免依赖任何 Android/JNI 环境。 */
private class FakeEngine(
    override val id: String,
    override val displayName: String = "fake",
    override val isAvailable: Boolean = true,
) : LocalLlmEngine {
    var loadCount = 0
    var unloadCount = 0
    var lastPrompt: String? = null
    var reply: String = "Thought: ok\nFinal Answer: done"

    override var isLoaded: Boolean = false
        private set
    override var loadedModelPath: String? = null
        private set

    override fun load(config: LlmConfig) {
        loadCount++
        isLoaded = true
        loadedModelPath = config.localModelPath
    }

    override fun generate(prompt: String, config: LlmConfig, onToken: (String) -> Unit): String {
        lastPrompt = prompt
        if (reply.isNotEmpty()) onToken(reply)
        return reply
    }

    override fun unload() {
        unloadCount++
        isLoaded = false
        loadedModelPath = null
    }
}

class LlmRouterTest {

    private fun localCfg(kind: BackendKind, path: String = "/tmp/model.bin") = LlmConfig(
        backend = kind,
        localModelPath = path,
    )

    @Test
    fun `local engine receives flattened prompt and returns react parsed result`() {
        val engine = FakeEngine("mediapipe")
        val router = LlmRouter(LlmClient(), listOf(engine))
        val cfg = localCfg(BackendKind.MEDIAPIPE)

        val result = router.chat(
            cfg,
            listOf(ChatMessage("user", "hi")),
            listOf(ToolSpec("list_scripts", "d", "{}")),
        )

        assertEquals(1, engine.loadCount)
        assertTrue(engine.lastPrompt!!.contains("<start_of_turn>user\nhi"))
        assertTrue(result.toolCalls.isEmpty())
        assertTrue(result.content.contains("done"))
    }

    @Test
    fun `engine is reloaded only when model path changes`() {
        val engine = FakeEngine("mediapipe")
        val router = LlmRouter(LlmClient(), listOf(engine))

        router.chat(localCfg(BackendKind.MEDIAPIPE, "/a"), listOf(ChatMessage("user", "1")))
        router.chat(localCfg(BackendKind.MEDIAPIPE, "/a"), listOf(ChatMessage("user", "2")))
        assertEquals(1, engine.loadCount)

        router.chat(localCfg(BackendKind.MEDIAPIPE, "/b"), listOf(ChatMessage("user", "3")))
        assertEquals(2, engine.loadCount)
    }

    @Test
    fun `unavailable engine throws friendly error`() {
        val engine = FakeEngine("mediapipe", isAvailable = false)
        val router = LlmRouter(LlmClient(), listOf(engine))

        val error = runCatching { router.chat(localCfg(BackendKind.MEDIAPIPE), listOf(ChatMessage("user", "hi"))) }.exceptionOrNull()

        assertTrue(error is LocalEngineException)
        assertTrue(error!!.message!!.contains("不可用"))
    }

    @Test
    fun `blank model path throws before touching engine`() {
        val engine = FakeEngine("mnn")
        val router = LlmRouter(LlmClient(), listOf(engine))

        val error = runCatching {
            router.chat(LlmConfig(backend = BackendKind.MNN, localModelPath = ""), listOf(ChatMessage("user", "hi")))
        }.exceptionOrNull()

        assertTrue(error is LocalEngineException)
        assertEquals(0, engine.loadCount)
    }

    @Test
    fun `unknown backend throws`() {
        val router = LlmRouter(LlmClient(), listOf(FakeEngine("mediapipe")))
        val error = runCatching {
            router.chat(LlmConfig(backend = BackendKind.MNN, localModelPath = "/x"), listOf(ChatMessage("user", "hi")))
        }.exceptionOrNull()
        assertTrue(error is LocalEngineException)
    }

    @Test
    fun `unloadAll forwards to every engine`() {
        val a = FakeEngine("mediapipe")
        val b = FakeEngine("mnn")
        val router = LlmRouter(LlmClient(), listOf(a, b))

        router.unloadAll()

        assertEquals(1, a.unloadCount)
        assertEquals(1, b.unloadCount)
    }

    @Test
    fun `message without tools returns raw text`() {
        val engine = FakeEngine("mediapipe").apply { reply = "纯文本回复" }
        val router = LlmRouter(LlmClient(), listOf(engine))

        val result = router.chat(localCfg(BackendKind.MEDIAPIPE), listOf(ChatMessage("user", "hi")))

        assertEquals("纯文本回复", result.content)
        assertTrue(result.toolCalls.isEmpty())
    }

    @Test
    fun `config isLocal reflects backend`() {
        assertFalse(LlmConfig().isLocal)
        assertTrue(LlmConfig(backend = BackendKind.MEDIAPIPE).isLocal)
        assertTrue(LlmConfig(backend = BackendKind.MNN).isLocal)
    }
}
