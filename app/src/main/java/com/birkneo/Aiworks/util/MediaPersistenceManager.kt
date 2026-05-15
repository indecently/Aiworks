package com.birkneo.Aiworks.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaPersistenceManager {
    /**
     * Copies media from a temporary URI (cache or content provider) to permanent internal storage.
     * Returns the absolute path of the persisted file, or the original if it's already persisted.
     */
    fun persistMedia(context: Context, uriString: String?): String? {
        if (uriString == null) return null
        
        // If it's already in the permanent media directory, don't copy again
        if (uriString.contains("/files/media/")) return uriString
        
        return try {
            val uri = Uri.parse(uriString)
            val mediaDir = File(context.filesDir, "media").apply { 
                if (!exists()) mkdirs() 
            }
            
            // Heuristic to determine extension
            val extension = when {
                uriString.contains("audio") || uriString.endsWith(".wav") || uriString.contains("audio_") -> "wav"
                else -> "jpg"
            }
            
            val fileName = "media_${UUID.randomUUID()}.$extension"
            val destFile = File(mediaDir, fileName)
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            // If copying fails, return the original URI so the UI can at least try to show it
            uriString
        }
    }

    /**
     * Helper to delete a file when a message is deleted (optional but good for hygiene)
     */
    fun deleteMedia(path: String?) {
        if (path == null) return
        try {
            val file = File(path)
            if (file.exists() && path.contains("/files/media/")) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
