package com.selfmod.agent.llm

/**
 * Pluggable on-device inference backend. MediaPipe is the default
 * implementation; MNN is a drop-in that loads only when native libs exist.
 *
 * The engine is session-oriented: [load] maps weights (mmap / GPU shader
 * compile), [generate] streams tokens, [unload] frees RAM/VRAM.
 */
interface LocalLlmEngine {
    val id: String
    val displayName: String
    val isAvailable: Boolean
    val isLoaded: Boolean
    val loadedModelPath: String?

    fun load(config: LlmConfig)
    fun generate(
        prompt: String,
        config: LlmConfig,
        onToken: (String) -> Unit = {},
    ): String
    fun unload()
}

class LocalEngineException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
