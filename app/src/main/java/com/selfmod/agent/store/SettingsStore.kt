package com.selfmod.agent.store

import android.content.Context
import android.content.SharedPreferences
import com.selfmod.agent.llm.LlmConfig
import org.json.JSONObject

/** Centralised persistence for LLM config and the agent's key-value memory. */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun llmConfig(): LlmConfig {
        val raw = prefs.getString(KEY_LLM, null) ?: return LlmConfig.DEFAULT
        return runCatching {
            val o = JSONObject(raw)
            LlmConfig(
                backend = runCatching {
                    com.selfmod.agent.llm.BackendKind.valueOf(o.optString("backend", "REMOTE"))
                }.getOrDefault(com.selfmod.agent.llm.BackendKind.REMOTE),
                baseUrl = o.optString("baseUrl", LlmConfig.DEFAULT.baseUrl),
                apiKey = o.optString("apiKey", ""),
                model = o.optString("model", LlmConfig.DEFAULT.model),
                temperature = o.optDouble("temperature", LlmConfig.DEFAULT.temperature),
                maxTokens = o.optInt("maxTokens", LlmConfig.DEFAULT.maxTokens),
                timeoutSeconds = o.optLong("timeoutSeconds", LlmConfig.DEFAULT.timeoutSeconds),
                localModelPath = o.optString("localModelPath", ""),
                localBackend = o.optString("localBackend", "gpu"),
                localMaxTokens = o.optInt("localMaxTokens", 1024),
                localTopK = o.optInt("localTopK", 40),
                localTopP = o.optDouble("localTopP", 0.9),
            )
        }.getOrDefault(LlmConfig.DEFAULT)
    }

    fun setLlmConfig(cfg: LlmConfig) {
        val o = JSONObject()
        o.put("backend", cfg.backend.name)
        o.put("baseUrl", cfg.baseUrl)
        o.put("apiKey", cfg.apiKey)
        o.put("model", cfg.model)
        o.put("temperature", cfg.temperature)
        o.put("maxTokens", cfg.maxTokens)
        o.put("timeoutSeconds", cfg.timeoutSeconds)
        o.put("localModelPath", cfg.localModelPath)
        o.put("localBackend", cfg.localBackend)
        o.put("localMaxTokens", cfg.localMaxTokens)
        o.put("localTopK", cfg.localTopK)
        o.put("localTopP", cfg.localTopP)
        prefs.edit().putString(KEY_LLM, o.toString()).apply()
    }

    fun memoryGet(key: String): String? = prefs.getString("$MEM_PREFIX$key", null)

    fun memorySet(key: String, value: String) {
        prefs.edit().putString("$MEM_PREFIX$key", value).apply()
    }

    fun memoryKeys(): List<String> = prefs.all.keys
        .filter { it.startsWith(MEM_PREFIX) }
        .map { it.removePrefix(MEM_PREFIX) }
        .sorted()

    fun memoryClear(key: String) = prefs.edit().remove("$MEM_PREFIX$key").apply()

    companion object {
        private const val PREFS = "selfmod_prefs"
        private const val KEY_LLM = "llm_config_v1"
        private const val MEM_PREFIX = "mem_"
    }
}
