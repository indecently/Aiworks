package com.birkneo.Aiworks.util

import android.content.Context
import android.provider.Settings
import android.speech.tts.TextToSpeech

import java.util.Locale

class TtsManager(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        // Initialize TTS with the system's preferred engine explicitly to ensure it respects user settings
        // instead of potentially defaulting to a hardcoded or internal fallback.
        val defaultEngine = Settings.Secure.getString(context.contentResolver, "tts_default_synth")
        tts = if (defaultEngine != null) {
            TextToSpeech(context.applicationContext, this, defaultEngine)
        } else {
            TextToSpeech(context.applicationContext, this)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isInitialized = true
            }
        }
    }

    fun speak(text: String) {
        if (!isInitialized) return
        
        // Stop any current speech before starting new queued chunks
        tts?.stop()

        // Handle Android's ~4000 character limit by chunking the text
        val chunks = chunkText(text)
        chunks.forEachIndexed { index, chunk ->
            val queueMode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts?.speak(chunk, queueMode, null, "chunk_$index")
        }
    }

    private fun chunkText(text: String): List<String> {
        val maxChunkSize = 3900 // Slightly under 4000 for safety
        if (text.length <= maxChunkSize) return listOf(text)

        val chunks = mutableListOf<String>()
        var remainingText = text

        while (remainingText.isNotEmpty()) {
            if (remainingText.length <= maxChunkSize) {
                chunks.add(remainingText)
                break
            }

            // Try to find a good break point (sentence end or space)
            var breakPoint = remainingText.lastIndexOf(". ", maxChunkSize)
            if (breakPoint == -1) breakPoint = remainingText.lastIndexOf("? ", maxChunkSize)
            if (breakPoint == -1) breakPoint = remainingText.lastIndexOf("! ", maxChunkSize)
            if (breakPoint == -1) breakPoint = remainingText.lastIndexOf(" ", maxChunkSize)
            
            // If no space/punctuation found, just hard cut
            if (breakPoint == -1 || breakPoint < maxChunkSize / 2) {
                breakPoint = maxChunkSize
            } else {
                breakPoint += 1 // Include the punctuation/space
            }

            chunks.add(remainingText.substring(0, breakPoint).trim())
            remainingText = remainingText.substring(breakPoint).trim()
        }
        return chunks
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
