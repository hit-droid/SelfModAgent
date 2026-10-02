package com.selfmod.agent.llm

import android.content.Context
import android.util.Log
import java.io.File

/**
 * MediaPipe LLM Inference backend (tasks-genai 0.10.x), pinned to the API
 * verified against tasks-genai-0.10.29:
 *
 *   LlmInferenceOptions.builder()
 *       .setModelPath(String)
 *       .setMaxTokens(Int)
 *       .setMaxTopK(Int)
 *       .setPreferredBackend(Backend)
 *       .build()
 *   LlmInference.createFromOptions(Context, LlmInferenceOptions)
 *   inference.generateResponseAsync(String, ProgressListener<String>)
 *   inference.generateResponse(String): String
 *   inference.close()
 *
 * GPU backend needs a short warm-up on first load; call [load] off the main
 * thread. If the AAR is absent, [isAvailable] stays false and no JNI is touched.
 */
class MediaPipeEngine(private val appContext: Context) : LocalLlmEngine {

    override val id: String = "mediapipe"
    override val displayName: String = "MediaPipe (GPU/CPU)"

    @Volatile private var session: Any? = null
    @Volatile private var modelPath: String? = null

    override val isAvailable: Boolean
        get() = CLASS_INFERENCE != null && CLASS_OPTIONS != null

    override val isLoaded: Boolean
        get() = session != null

    override val loadedModelPath: String?
        get() = modelPath

    @Synchronized
    override fun load(config: LlmConfig) {
        if (!isAvailable) throw LocalEngineException("MediaPipe tasks-genai 未打包进 APK")
        val path = config.localModelPath.trim()
        if (path.isEmpty()) throw LocalEngineException("未设置本地模型路径")
        val file = File(path)
        if (!file.exists() || file.length() < 1024) {
            throw LocalEngineException("模型文件不存在或过小: $path")
        }
        if (session != null && modelPath == path) return
        unload()

        val options = buildOptions(path, config)
            ?: throw LocalEngineException("无法构造 LlmInferenceOptions")

        val created = runCatching {
            CLASS_INFERENCE!!.getMethod("createFromOptions", Context::class.java, CLASS_OPTIONS!!)
                .invoke(null, appContext, options)
        }.getOrElse {
            throw LocalEngineException("LlmInference 创建失败: ${it.message}", it)
        }

        session = created
        modelPath = path
        Log.i(TAG, "MediaPipe loaded $path backend=${config.localBackend}")
    }

    override fun generate(prompt: String, config: LlmConfig, onToken: (String) -> Unit): String {
        val sess = session ?: run {
            load(config)
            session
        } ?: throw LocalEngineException("MediaPipe 会话未就绪")

        val acc = StringBuilder()
        val progressClass = CLASS_PROGRESS
        return try {
            if (progressClass != null) {
                val listener = progressListener(progressClass) { partial, complete ->
                    if (partial.isNotEmpty()) {
                        acc.append(partial)
                        runCatching { onToken(partial) }
                    }
                    if (complete) { /* future completes; nothing else to do */ }
                }
                runCatching {
                    sess.javaClass.getMethod("generateResponseAsync", String::class.java, progressClass)
                        .invoke(sess, prompt, listener)
                }.getOrElse { throw LocalEngineException("调用 generateResponseAsync 失败: ${it.message}", it) }
                acc.toString()
            } else {
                val text = (sess.javaClass.getMethod("generateResponse", String::class.java)
                    .invoke(sess, prompt) as? String).orEmpty()
                if (text.isNotEmpty()) onToken(text)
                text
            }
        } catch (e: LocalEngineException) {
            throw e
        } catch (t: Throwable) {
            throw LocalEngineException("MediaPipe 推理失败: ${t.message}", t)
        }
    }

    @Synchronized
    override fun unload() {
        val s = session ?: return
        session = null
        modelPath = null
        runCatching { s.javaClass.getMethod("close").invoke(s) }
        Log.i(TAG, "MediaPipe unloaded")
    }

    private fun buildOptions(path: String, config: LlmConfig): Any? {
        val optionsClass = CLASS_OPTIONS ?: return null
        val builder = runCatching { optionsClass.getMethod("builder").invoke(null) }.getOrNull() ?: return null
        val b = builder.javaClass

        runCatching { b.getMethod("setModelPath", String::class.java).invoke(builder, path) }
        runCatching { b.getMethod("setMaxTokens", Int::class.javaPrimitiveType!!).invoke(builder, config.localMaxTokens) }
        runCatching { b.getMethod("setMaxTopK", Int::class.javaPrimitiveType!!).invoke(builder, config.localTopK) }
        applyPreferredBackend(builder, config.localBackend)
        return runCatching { b.getMethod("build").invoke(builder) }.getOrNull()
    }

    private fun applyPreferredBackend(builder: Any, backend: String) {
        val enumClass = CLASS_BACKEND ?: return
        val want = when (backend.lowercase()) {
            "cpu" -> "CPU"
            else -> "GPU"
        }
        val constants = enumClass.enumConstants ?: return
        val picked = constants.firstOrNull { (it as Enum<*>).name.equals(want, ignoreCase = true) } ?: return
        runCatching {
            builder.javaClass.getMethod("setPreferredBackend", enumClass).invoke(builder, picked)
        }
    }

    private fun progressListener(
        iface: Class<*>,
        cb: (partial: String, complete: Boolean) -> Unit,
    ): Any = java.lang.reflect.Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, method, args ->
        if (method.name == "run") {
            val partial = args?.getOrNull(0)?.toString().orEmpty()
            val complete = args?.getOrNull(1) as? Boolean ?: false
            cb(partial, complete)
        }
        null
    }

    companion object {
        private const val TAG = "MediaPipeEngine"
        private const val PKG = "com.google.mediapipe.tasks.genai.llminference"

        private val CLASS_INFERENCE: Class<*>? = load("$PKG.LlmInference")
        private val CLASS_OPTIONS: Class<*>? = load("$PKG.LlmInference\$LlmInferenceOptions")
        private val CLASS_BACKEND: Class<*>? = load("$PKG.LlmInference\$Backend")
        private val CLASS_PROGRESS: Class<*>? = load("$PKG.ProgressListener")

        private fun load(name: String): Class<*>? = runCatching { Class.forName(name) }.getOrNull()
    }
}
