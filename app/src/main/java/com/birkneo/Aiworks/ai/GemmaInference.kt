package com.birkneo.Aiworks.ai

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
    
    // Dedicated high-priority dispatcher for inference
    private var executorService = createExecutorService()
    private var inferenceDispatcher: CoroutineDispatcher = executorService.asCoroutineDispatcher()

    private fun createExecutorService() = Executors.newSingleThreadExecutor(object : ThreadFactory {
        override fun newThread(r: Runnable): Thread {
            return Thread(r, "gemma-inference-thread").apply {
                // Adjust to BACKGROUND priority to ensure UI thread is never starved
                priority = Thread.MIN_PRIORITY
            }
        }
    })

    suspend fun loadModel(
        modelPathOrUri: String,
        temperature: Double = 0.8,
        maxTokens: Int = 512,
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

                // Skip if already loaded and same path
                if (engine != null && finalPath == currentModelPath) {
                    resetConversation(temperature, maxTokens, systemInstruction)
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

                val config = EngineConfig(
                    modelPath = finalPath,
                    backend = Backend.GPU(),
                    visionBackend = Backend.GPU(),
                    audioBackend = Backend.CPU(),
                    cacheDir = context.cacheDir.path,
                    maxNumTokens = 4096 // Reduced for stability during cold starts
                )

                // Try GPU first
                val result = withContext(inferenceDispatcher) {
                    try {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
                        val newEngine = Engine(config)
                        newEngine.initialize()
                        engine = newEngine
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }

                if (!result) {
                    // FALLBACK TO CPU: GPU might fail if triggered from assistant overlay or background
                    val cpuConfig = config.copy(
                        backend = Backend.CPU(),
                        visionBackend = Backend.CPU()
                    )
                    withContext(inferenceDispatcher) {
                        try {
                            val newEngine = Engine(cpuConfig)
                            newEngine.initialize()
                            engine = newEngine
                        } catch (e: Exception) {
                            currentModelPath = null
                            return@withContext ModelStatus.Error("AI Engine initialization failed: ${e.message}")
                        }
                    }
                }

                currentModelPath = finalPath

                val conversationConfig = ConversationConfig(
                    systemInstruction = systemInstruction?.let { Contents.of(it) },
                    samplerConfig = SamplerConfig(
                        temperature = temperature,
                        topK = 40,
                        topP = 0.95,
                        seed = 0
                    )
                )

                conversation = engine?.createConversation(conversationConfig)
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
            
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                FileInputStream(pfd.fileDescriptor).use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        val inputChannel = input.channel
                        val outputChannel = output.channel
                        val size = inputChannel.size()
                        var transferred = 0L
                        val chunkSize = 1024L * 1024L // 1MB chunks for progress reporting
                        
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
            ?.flowOn(inferenceDispatcher)
    }

    private suspend fun checkThermalAndThrottle() {
        val status = powerManager.currentThermalStatus
        if (status >= PowerManager.THERMAL_STATUS_SEVERE) {
            delay(500) // Inject cooling delay
        } else if (status >= PowerManager.THERMAL_STATUS_MODERATE) {
            delay(100) // Light throttling
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
        systemInstruction: String? = null
    ) = withContext(inferenceDispatcher) {
        conversation?.close()
        conversation = null
        
        engine?.let { e ->
            val conversationConfig = ConversationConfig(
                systemInstruction = systemInstruction?.let { Contents.of(it) },
                samplerConfig = SamplerConfig(
                    temperature = temperature,
                    topK = 40,
                    topP = 0.95,
                    seed = 0
                )
            )
            conversation = e.createConversation(conversationConfig)
        }
    }

    suspend fun summarize(textToSummarize: String): String? = withContext(inferenceDispatcher) {
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
    suspend fun distillMemory(currentLtm: String, newSegment: String): String? = withContext(inferenceDispatcher) {
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

    suspend fun close() = loadMutex.withLock {
        closeInternal()
    }

    private fun closeInternal() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
        currentModelPath = null
        
        // SHUTDOWN EXECUTOR: Prevent thread leaks by rebuilding the dispatcher on next load.
        executorService.shutdown()
        executorService = createExecutorService()
        inferenceDispatcher = executorService.asCoroutineDispatcher()
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

