package com.selfmod.agent.llm

import android.util.Log
import java.io.File

/**
 * MNN-LLM backend, matching the engine inside Alibaba's MNN Chat app.
 *
 * Native symbols live in `libmnnllm_jni.so` (plus `libMNN.so` / `libMNN_LLM.so`
 * as transitive deps). Drop those .so files into `app/src/main/jniLibs/arm64-v8a/`
 * after compiling MNN with:
 *
 *     -DMNN_BUILD_LLM=true -DMNN_ARM82=true -DMNN_LOW_MEMORY=true
 *     -DMNN_CPU_WEIGHT_DEQUANT_GEMM=true -DMNN_SUPPORT_TRANSFORMER_FUSE=true
 *     -DMNN_OPENCL=true
 *
 * When the library is absent this engine reports [isAvailable] = false and
 * never crashes the process.
 */
class MnnEngine : LocalLlmEngine {

    override val id: String = "mnn"
    override val displayName: String = "MNN-LLM (OpenCL/CPU)"

    @Volatile private var nativeHandle: Long = 0L
    @Volatile private var modelPath: String? = null
    @Volatile private var loadedFlag: Boolean = false

    override val isAvailable: Boolean
        get() = NATIVE_OK

    override val isLoaded: Boolean
        get() = loadedFlag && nativeHandle != 0L

    override val loadedModelPath: String?
        get() = modelPath

    @Synchronized
    override fun load(config: LlmConfig) {
        if (!NATIVE_OK) {
            throw LocalEngineException(
                "MNN 原生库未打包。将 libmnnllm_jni.so / libMNN.so / libMNN_LLM.so 放入 app/src/main/jniLibs/arm64-v8a/ 后重编。",
            )
        }
        val path = config.localModelPath.trim()
        if (path.isEmpty()) throw LocalEngineException("未设置本地模型路径")
        val dir = File(path)
        if (!dir.exists()) throw LocalEngineException("模型路径不存在: $path")
        if (isLoaded && modelPath == path) return
        unload()

        val handle = nativeCreate(
            path,
            config.localBackend,
            config.temperature.toFloat(),
            config.localTopK,
            config.localTopP.toFloat(),
            config.localMaxTokens,
        )
        if (handle == 0L) throw LocalEngineException("MNN 会话创建失败，检查模型目录是否含 config.json 与权重")
        nativeHandle = handle
        modelPath = path
        loadedFlag = true
        Log.i(TAG, "MNN loaded $path backend=${config.localBackend}")
    }

    override fun generate(prompt: String, config: LlmConfig, onToken: (String) -> Unit): String {
        if (!isLoaded) load(config)
        val handle = nativeHandle
        if (handle == 0L) throw LocalEngineException("MNN 会话未就绪")
        val acc = StringBuilder()
        val listener = object : TokenSink {
            override fun onToken(token: String, done: Boolean) {
                if (token.isNotEmpty()) {
                    acc.append(token)
                    runCatching { onToken(token) }
                }
            }
        }
        val rc = nativeGenerate(handle, prompt, listener)
        if (rc != 0) throw LocalEngineException("MNN 推理返回错误码 $rc")
        return acc.toString()
    }

    @Synchronized
    override fun unload() {
        val h = nativeHandle
        nativeHandle = 0L
        loadedFlag = false
        modelPath = null
        if (h != 0L && NATIVE_OK) runCatching { nativeRelease(h) }
        Log.i(TAG, "MNN unloaded")
    }

    /** Called from JNI on the inference thread. */
    interface TokenSink {
        fun onToken(token: String, done: Boolean)
    }

    private external fun nativeCreate(
        modelDir: String,
        backend: String,
        temperature: Float,
        topK: Int,
        topP: Float,
        maxNewTokens: Int,
    ): Long

    private external fun nativeGenerate(handle: Long, prompt: String, sink: TokenSink): Int
    private external fun nativeRelease(handle: Long)

    companion object {
        private const val TAG = "MnnEngine"
        private val NATIVE_OK: Boolean = runCatching {
            System.loadLibrary("MNN")
            runCatching { System.loadLibrary("MNN_LLM") }
            System.loadLibrary("mnnllm_jni")
            true
        }.getOrElse {
            Log.i(TAG, "MNN native libs not present: ${it.message}")
            false
        }
    }
}
