package com.birkneo.Aiworks.ai

/**
 * MODIFICATION NOTICE:
 * This file has been modified to implement custom semantic retrieval (RAG) using 
 * the Gemma embedding model. 
 * Gemma is provided under and subject to the Gemma Terms of Use.
 */

import android.content.Context
import android.content.res.AssetFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Collections
import kotlin.math.sqrt

class EmbeddingInference(private val context: Context) {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private val modelPath = "embeddinggemma-300M_seq512_mixed-precision.tflite"
    
    private val loadMutex = Mutex()
    private var isLoaded = false
    
    // PERF: Cache embeddings for frequently retrieved LTM fragments to avoid TFLite overhead
    private val embeddingCache = Collections.synchronizedMap(LinkedHashMap<String, FloatArray>(100, 0.75f, true))

    /**
     * Ensures the model is loaded into memory.
     * Called lazily to prevent UI hang during app startup.
     */
    suspend fun ensureLoaded() = loadMutex.withLock {
        if (isLoaded) return@withLock
        
        withContext(Dispatchers.IO) {
            try {
                val options = Interpreter.Options()
                try {
                    gpuDelegate = GpuDelegate()
                    options.addDelegate(gpuDelegate)
                    android.util.Log.d("EmbeddingInference", "GPU acceleration enabled for embeddings")
                } catch (e: Throwable) {
                    android.util.Log.e("EmbeddingInference", "Failed to initialize GPU delegate, falling back to CPU", e)
                }
                
                interpreter = Interpreter(loadModelFile(), options)
                isLoaded = true
                android.util.Log.d("EmbeddingInference", "Embedding model loaded successfully")
            } catch (e: Exception) {
                android.util.Log.e("EmbeddingInference", "Critical error loading embedding model", e)
            }
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor: AssetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel: FileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Computes a fixed-size embedding for the given text.
     * Uses a robust word-hash fallback for tokenization to improve over char-codes.
     */
    fun embedText(text: String): FloatArray {
        if (text.isBlank()) return FloatArray(0)
        
        // 1. Check Cache
        embeddingCache[text]?.let { return it }
        
        val interp = interpreter ?: return FloatArray(0)
        val startTime = System.currentTimeMillis()
        
        // 2. Improved Tokenization (Word-Hash Heuristic)
        // Gemma vocabulary is ~256k. We hash words into this space to simulate a real tokenizer.
        val inputIds = IntArray(512)
        val words = text.lowercase().split(Regex("\\s+"))
        for (i in 0 until minOf(words.size, 512)) {
            // Use a stable hash that fits within the Gemma vocab range
            val hash = words[i].hashCode() and 0x3FFFF // Mask to ~262k
            inputIds[i] = hash
        }

        val input = arrayOf(inputIds)
        
        // Dynamically determine output shape
        val outputShape = interp.getOutputTensor(0).shape()
        val dim = outputShape.last()
        val output = Array(1) { FloatArray(dim) }
        
        return try {
            interp.run(input, output)
            val result = output[0]
            
            // 3. Update Cache
            if (embeddingCache.size > 200) {
                val it = embeddingCache.keys.iterator()
                if (it.hasNext()) { it.next(); it.remove() }
            }
            embeddingCache[text] = result
            
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.d("EmbeddingInference", "Inference: \"${text.take(20)}...\" in ${duration}ms (New)")
            result
        } catch (e: Exception) {
            android.util.Log.e("EmbeddingInference", "Inference error", e)
            FloatArray(0)
        }
    }

    fun cosineSimilarity(v1: FloatArray, v2: FloatArray): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dotProduct += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        return if (normA > 0 && normB > 0) dotProduct / (sqrt(normA) * sqrt(normB)) else 0f
    }

    /**
     * Ranks fragments by semantic relevance to the query.
     * This is optimized to run on a background thread and uses the embedding cache.
     */
    suspend fun rankFragments(query: String, fragments: List<String>, topK: Int = 3): List<String> = withContext(Dispatchers.Default) {
        if (fragments.isEmpty()) return@withContext emptyList()
        
        ensureLoaded()
        
        val queryVector = embedText(query)
        if (queryVector.isEmpty()) return@withContext emptyList()
        
        val fragmentScores = fragments.map { fragment ->
            val fragmentVector = embedText(fragment)
            val score = cosineSimilarity(queryVector, fragmentVector)
            fragment to score
        }

        fragmentScores.sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        embeddingCache.clear()
        isLoaded = false
    }
}
