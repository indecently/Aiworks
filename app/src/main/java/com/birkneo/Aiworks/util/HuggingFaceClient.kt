package com.birkneo.Aiworks.util

import com.birkneo.Aiworks.ai.HFModel
import com.birkneo.Aiworks.ai.HFSibling
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

class HuggingFaceClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun fetchModels(query: String = "litert"): List<HFModel> {
        // Use expand[]=siblings to get more metadata including potential LFS sizes
        val url = "https://huggingface.co/api/models?search=$query&full=true&expand[]=siblings&limit=20"
        val request = Request.Builder().url(url).build()
        
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val models = json.decodeFromString<List<HFModel>>(body)
                
                // Secondary check: if sizes are still missing for the target files, 
                // we'll resolve them via the tree API in the ViewModel mapping or during individual fetch.
                models
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun fetchModelTree(modelId: String): List<HFSibling> {
        val url = "https://huggingface.co/api/models/$modelId/tree/main"
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList<HFSibling>()
                val body = response.body?.string() ?: return emptyList<HFSibling>()
                json.decodeFromString<List<HFSibling>>(body)
            }
        } catch (e: Exception) {
            emptyList<HFSibling>()
        }
    }

    fun fetchModelInfo(modelId: String): HFModel? {
        val url = "https://huggingface.co/api/models/$modelId"
        val request = Request.Builder().url(url).build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                json.decodeFromString<HFModel>(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun downloadModel(
        modelId: String,
        fileName: String,
        destFile: File,
        onProgress: (Float) -> Unit
    ): Boolean {
        val url = "https://huggingface.co/$modelId/resolve/main/$fileName"
        val request = Request.Builder().url(url).build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                val totalBytes = body.contentLength()
                
                body.byteStream().use { input ->
                    destFile.parentFile?.mkdirs()
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (totalBytes > 0) {
                                onProgress(totalBytesRead.toFloat() / totalBytes)
                            }
                        }
                    }
                }
                
                // VALIDATION: Ensure the file on disk matches the server's reported size
                if (totalBytes > 0 && destFile.length() < totalBytes) {
                    android.util.Log.e("HFClient", "Download validation failed: Expected $totalBytes, got ${destFile.length()}. Deleting corrupted file.")
                    destFile.delete()
                    return false
                }

                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
