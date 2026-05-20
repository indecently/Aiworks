package com.birkneo.Aiworks.ai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

sealed class ModelStatus {
    object NotLoaded : ModelStatus()
    data class Loading(val progress: Float = 0f) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
}

class GemmaInference(private val context: Context) {
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var currentModelPath: String? = null
    private val loadMutex = Mutex()

    private val powerManager by lazy { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    
    // ARCHITECTURAL OPTIMIZATION: Separate dispatchers for User Inference and Background Tasks
    // Prevents Auto-Summarization from starving active chat responsiveness.
    private var userInferenceExecutor = createExecutorService("gemma-user-inference", Thread.MAX_PRIORITY)
    private var backgroundTaskExecutor = createExecutorService("gemma-background-task", Thread.NORM_PRIORITY)
    
    private var userInferenceDispatcher: CoroutineDispatcher = userInferenceExecutor.asCoroutineDispatcher()
    private var backgroundTaskDispatcher: CoroutineDispatcher = backgroundTaskExecutor.asCoroutineDispatcher()

    private fun createExecutorService(name: String, priority: Int) = Executors.newSingleThreadExecutor(object : ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return Thread(r, name).apply {
                this.priority = priority
            }
        }
    })

    suspend fun loadModel(
        modelPathOrUri: String,
        temperature: Double = 0.8,
        maxTokens: Int = 512,
        topK: Int = 40,
        topP: Double = 0.95,
        useGpu: Boolean = true,
        systemInstruction: String? = null,
        onProgress: (Float) -> Unit = {}
    ): ModelStatus = loadMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                val finalPath = if (modelPathOrUri.startsWith("content://")) {
                    val uri = Uri.parse(modelPathOrUri)
                    copyUriToInternalStorage(uri, onProgress)
                } else {
                    modelPathOrUri
                }

                if (finalPath == null) {
                    return@withContext ModelStatus.Error("Failed to resolve model path from URI")
                }

                // Skip if already loaded and same path (Re-init conversation with new params)
                if (engine != null && finalPath == currentModelPath) {
                    resetConversation(temperature, maxTokens, topK, topP, systemInstruction)
                    return@withContext ModelStatus.Ready
                }

                val modelFile = File(finalPath)
                if (!modelFile.exists()) {
                    currentModelPath = null
                    return@withContext ModelStatus.Error("Model file not found at $finalPath")
                }

                onProgress(1.0f)

                // Close existing session before re-init
                closeInternal()

                // OPTIMIZATION: Check thermal status and RAM before selecting backend/config
                val thermalStatus = powerManager.currentThermalStatus
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memoryInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                
                // Determine context window based on available RAM
                val totalRamGb = memoryInfo.totalMem / (1024 * 1024 * 1024)
                val maxContextTokens = when {
                    totalRamGb >= 8 -> 4096
                    totalRamGb >= 4 -> 3072
                    else -> 2048 // Conservative for low-end devices
                }

                // MULTI-STAGE INITIALIZATION: NPU -> GPU -> CPU
                val backendsToTry = mutableListOf<Backend>()
                if (useGpu && thermalStatus < PowerManager.THERMAL_STATUS_SEVERE) {
                    // Check if NPU dispatch libraries actually exist to avoid native crashes
                    val nativeLibDir = context.applicationInfo.nativeLibraryDir
                    val hasNpuLibs = try {
                        val libFolder = File(nativeLibDir)
                        libFolder.exists() && libFolder.listFiles()?.any { 
                            it.name.contains("liblitert_dispatch") || it.name.contains("libQnn") 
                        } == true
                    } catch (e: Exception) { false }

                    if (hasNpuLibs) {
                        backendsToTry.add(Backend.NPU(nativeLibDir))
                    }
                    backendsToTry.add(Backend.GPU())
                }
                backendsToTry.add(Backend.CPU())

                var lastError: Exception? = null
                var initSuccess = false

                for (trialBackend in backendsToTry) {
                    val config = EngineConfig(
                        modelPath = finalPath,
                        backend = trialBackend,
                        visionBackend = trialBackend,
                        audioBackend = Backend.CPU(),
                        cacheDir = context.cacheDir.path,
                        maxNumTokens = maxContextTokens
                    )

                    // CRITICAL FIX: Initialization MUST happen on Dispatchers.IO.
                    // Using userInferenceDispatcher (MAX_PRIORITY) for initialization leads to
                    // CPU starvation of the Main Thread during mmap and delegate creation.
                    val result = withContext(Dispatchers.IO) {
                        try {
                            // Ensure thread priority doesn't steal cycles from Choreographer
                            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                            val newEngine = Engine(config)
                            newEngine.initialize()
                            engine = newEngine
                            true
                        } catch (e: Exception) {
                            lastError = e
                            false
                        }
                    }

                    if (result) {
                        initSuccess = true
                        break
                    }
                }

                if (!initSuccess) {
                    currentModelPath = null
                    return@withContext ModelStatus.Error("AI Engine initialization failed: ${lastError?.message ?: "Unknown error"}")
                }

                currentModelPath = finalPath

                resetConversation(temperature, maxTokens, topK, topP, systemInstruction)
                ModelStatus.Ready
            } catch (e: Exception) {
                currentModelPath = null
                ModelStatus.Error("Failed to load model: ${e.message}")
            }
        }
    }

    private fun copyUriToInternalStorage(uri: Uri, onProgress: (Float) -> Unit): String? {
        return try {
            val destinationFile = File(context.filesDir, "model.litertlm")
            
            // OPTIMIZATION: Use FileChannel transferTo for zero-copy I/O throughput
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val inputChannel = input.channel
                        val outputChannel = output.channel
                        val size = inputChannel.size()
                        var transferred = 0L
                        val chunkSize = 8L * 1024L * 1024L // 8MB chunks for high-speed transfer
                        
                        while (transferred < size) {
                            val count = inputChannel.transferTo(transferred, chunkSize, outputChannel)
                            if (count <= 0) break
                            transferred += count
                            onProgress(transferred.toFloat() / size * 0.99f)
                        }
                    }
                }
            }
            destinationFile.absolutePath
        } catch (e: Exception) {
            // Fallback for URIs that don't support PFD
            try {
                val destinationFile = File(context.filesDir, "model.litertlm")
                val inputStream = context.contentResolver.openInputStream(uri) ?: return null
                val fileSize = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L

                val outputStream = FileOutputStream(destinationFile)
                val buffer = ByteArray(1024 * 1024)
                var bytesCopied = 0L
                var bytesRead: Int
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                            if (fileSize > 0) {
                                val progress = (bytesCopied.toFloat() / fileSize) * 0.99f
                                onProgress(progress)
                            }
                        }
                    }
                }
                destinationFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    fun sendMessage(
        text: String,
        imageUri: String? = null,
        audioUri: String? = null
    ): Flow<String>? {
        val contents = mutableListOf<Content>()
        
        // OPTIMIZED: sendMessage now focuses strictly on the active turn.
        // History and System instructions are managed by the KV cache.
        val promptStart = buildString {
            append("<start_of_turn>user\n")
            append(text).append("\n")
        }
        
        contents.add(Content.Text(promptStart))
        
        imageUri?.let { uriString ->
            val localPath = resolveMediaUri(Uri.parse(uriString), "image")
            if (localPath != null) {
                contents.add(Content.ImageFile(localPath))
            }
        }
        
        audioUri?.let { uriString ->
            val localPath = resolveMediaUri(Uri.parse(uriString), "audio")
            if (localPath != null) {
                contents.add(Content.AudioFile(localPath))
            }
        }

        // Close the turn and open the model response
        contents.add(Content.Text("\n<end_of_turn>\n<start_of_turn>model\n"))

        if (contents.isEmpty()) return null

        val message = Message.user(Contents.of(contents))
        return conversation?.sendMessageAsync(message)
            ?.onEach { 
                checkThermalAndThrottle()
            }
            ?.map { responseMessage ->
                val contentsList = responseMessage.contents.contents
                var textResult = ""
                for (content in contentsList) {
                    if (content is Content.Text) {
                        textResult += content.text
                    }
                }
                // Clean the output: remove only the specific '***' prefix if present, 
                // but preserve all other whitespace and prose formatting.
                textResult.removePrefix("***")
            }
            ?.flowOn(userInferenceDispatcher)
    }

    private suspend fun checkThermalAndThrottle() {
        val status = powerManager.currentThermalStatus
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            delay(500) // Inject cooling delay
        } else if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
            delay(100) // Light throttling
        } else {
            // OPTIMIZATION: Yield more frequently even in normal thermal states
            // to allow the EGL producer (UI thread) to signal its fences and refresh buffers.
            delay(8)
        }
    }

    private fun resolveMediaUri(uri: Uri, type: String): String? {
        if (uri.scheme != "content") return uri.path
        
        return try {
            val extension = if (type == "image") "jpg" else "wav"
            val tempFile = File(context.cacheDir, "${type}_${System.currentTimeMillis()}.$extension")
            
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val inputChannel = input.channel
                        val outputChannel = output.channel
                        inputChannel.transferTo(0, inputChannel.size(), outputChannel)
                    }
                }
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            // Fallback for URIs that don't support PFD
            try {
                val extension = if (type == "image") "jpg" else "wav"
                val tempFile = File(context.cacheDir, "${type}_fallback_${System.currentTimeMillis()}.$extension")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile.absolutePath
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun resetConversation(
        temperature: Double = 0.8,
        maxTokens: Int = 512,
        topK: Int = 40,
        topP: Double = 0.95,
        systemInstruction: String? = null
    ) = withContext(Dispatchers.IO) {
        conversation?.close()
        conversation = null
        
        engine?.let { e ->
            val conversationConfig = ConversationConfig(
                systemInstruction = systemInstruction?.let { Contents.of(it) },
                samplerConfig = SamplerConfig(
                    temperature = temperature,
                    topK = topK,
                    topP = topP,
                    seed = 0
                )
            )
            // Creating a conversation can be blocking if it triggers KV cache allocation
            conversation = e.createConversation(conversationConfig)
        }
    }

    suspend fun summarize(textToSummarize: String): String? = withContext(backgroundTaskDispatcher) {
        val currentEngine = engine ?: return@withContext null
        
        // CRITICAL: Close existing conversation first because LiteRT only supports one session at a time.
        // Failing to do this results in FAILED_PRECONDITION: A session already exists.
        conversation?.close()
        conversation = null

        val prompt = "Below is a part of a conversation history. Please write a very concise summary of the key facts, events, and names mentioned so far to help me remember the context. Do not include meta-talk, just the facts. \n\nCONVERSATION:\n$textToSummarize\n\nCONCISE SUMMARY:"
        
        var tempConversation: Conversation? = null
        try {
            tempConversation = currentEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(temperature = 0.1, topK = 1, topP = 1.0, seed = 0)
                )
            )
            
            val result = StringBuilder()
            // Using a timeout or ensuring collection is robust
            tempConversation.sendMessageAsync(prompt).collect { message ->
                val contentsList = message.contents.contents
                for (content in contentsList) {
                    if (content is Content.Text) {
                        result.append(content.text)
                    }
                }
            }
            result.toString().trim().ifEmpty { null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            // CRITICAL: Guarantee native memory is freed to prevent OOM
            tempConversation?.close()
        }
    }

    /**
     * PROCESS B: Recursive Condensation.
     * Merges current Long-Term Memory with new segments into a single high-density core.
     */
    suspend fun distillMemory(currentLtm: String, newSegment: String): String? = withContext(backgroundTaskDispatcher) {
        val currentEngine = engine ?: return@withContext null
        
        conversation?.close()
        conversation = null

        val prompt = """
            Below is the 'EXISTING MEMORY' and a 'NEW CONVERSATION SEGMENT'. 
            Your task is to merge them into a single, high-density 'CORE MEMORY'.
            
            RULES:
            1. Retain high-value facts (user preferences, project names, established facts).
            2. Discard transient noise (greetings, ephemeral errors, social filler).
            3. Be extremely concise. Use short sentences.
            4. If facts conflict, prioritize the 'NEW CONVERSATION SEGMENT'.

            EXISTING MEMORY:
            $currentLtm

            NEW CONVERSATION SEGMENT:
            $newSegment

            NEW CONDENSED CORE MEMORY:
        """.trimIndent()
        
        var tempConversation: Conversation? = null
        try {
            tempConversation = currentEngine.createConversation(
                ConversationConfig(
                    samplerConfig = SamplerConfig(temperature = 0.1, topK = 1, topP = 1.0, seed = 0)
                )
            )
            
            val result = StringBuilder()
            tempConversation.sendMessageAsync(prompt).collect { message ->
                val contentsList = message.contents.contents
                for (content in contentsList) {
                    if (content is Content.Text) {
                        result.append(content.text)
                    }
                }
            }
            result.toString().trim().ifEmpty { null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            tempConversation?.close()
        }
    }

    fun getLoadedModelPath(): String? = currentModelPath

    fun isConversationActive(): Boolean = conversation != null

    suspend fun close() = loadMutex.withLock {
        closeInternal()
    }

    private fun closeInternal() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        currentModelPath = null
        
        // SHUTDOWN EXECUTORS: Rebuild dispatchers on next load to ensure fresh threads
        userInferenceExecutor.shutdown()
        backgroundTaskExecutor.shutdown()

        userInferenceExecutor = createExecutorService("gemma-user-inference", Thread.MAX_PRIORITY)
        backgroundTaskExecutor = createExecutorService("gemma-background-task", Thread.NORM_PRIORITY)
        
        userInferenceDispatcher = userInferenceExecutor.asCoroutineDispatcher()
        backgroundTaskDispatcher = backgroundTaskExecutor.asCoroutineDispatcher()
    }

    fun cleanupMediaCache() {
        try {
            val cacheFiles = context.cacheDir.listFiles()
            cacheFiles?.filter { file ->
                file.name.contains("image_") || 
                file.name.contains("audio_") || 
                file.name.contains("camera_capture_")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            // No-op
        }
    }

    fun estimateTokens(text: String): Int {
        // Rough estimate: 3 characters per token for English
        val rawEstimate = (text.length / 3) + 1
        // Apply a 10% safety buffer to account for dense text or code snippets
        return (rawEstimate * 1.1).toInt()
    }
}

