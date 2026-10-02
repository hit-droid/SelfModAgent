package com.selfmod.agent.llm

enum class BackendKind {
    REMOTE,
    MEDIAPIPE,
    MNN,
}

/** Configuration for either a remote OpenAI-compatible endpoint or a local engine. */
data class LlmConfig(
    val backend: BackendKind = BackendKind.REMOTE,
    val baseUrl: String = "https://open.bigmodel.cn/api/paas/v4",
    val apiKey: String = "",
    val model: String = "glm-4-plus",
    val temperature: Double = 0.6,
    val maxTokens: Int = 2048,
    val timeoutSeconds: Long = 120,
    val localModelPath: String = "",
    val localBackend: String = "gpu",
    val localMaxTokens: Int = 1024,
    val localTopK: Int = 40,
    val localTopP: Double = 0.9,
) {
    val isLocal: Boolean get() = backend != BackendKind.REMOTE

    companion object {
        val DEFAULT = LlmConfig()
        val PRESETS = listOf(
            "GLM (智谱)" to DEFAULT,
            "DeepSeek" to LlmConfig(
                baseUrl = "https://api.deepseek.com/v1",
                model = "deepseek-chat",
            ),
            "Moonshot (Kimi)" to LlmConfig(
                baseUrl = "https://api.moonshot.cn/v1",
                model = "moonshot-v1-32k",
            ),
            "OpenAI" to LlmConfig(
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-4o-mini",
            ),
            "本地 · MediaPipe" to LlmConfig(
                backend = BackendKind.MEDIAPIPE,
                model = "gemma-2b-it",
                temperature = 0.8,
                maxTokens = 1024,
                localBackend = "gpu",
            ),
            "本地 · MNN" to LlmConfig(
                backend = BackendKind.MNN,
                model = "qwen2.5-1.5b",
                temperature = 0.8,
                maxTokens = 1024,
                localBackend = "opencl",
            ),
        )
    }
}
