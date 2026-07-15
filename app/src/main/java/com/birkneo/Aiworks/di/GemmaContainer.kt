package com.birkneo.Aiworks.di

import android.content.Context
import com.birkneo.Aiworks.ai.EmbeddingInference
import com.birkneo.Aiworks.ai.GemmaInference
import com.birkneo.Aiworks.data.database.ChatDatabase
import com.birkneo.Aiworks.data.repository.ChatRepository
import com.birkneo.Aiworks.settings.SettingsManager
import com.birkneo.Aiworks.util.AudioRecorder
import com.birkneo.Aiworks.util.TtsManager

object GemmaContainer {
    private var gemmaInference: GemmaInference? = null
    private var embeddingInference: EmbeddingInference? = null
    private var settingsManager: SettingsManager? = null
    private var audioRecorder: AudioRecorder? = null
    private var ttsManager: TtsManager? = null
    private var chatRepository: ChatRepository? = null

    fun getEmbeddingInference(context: Context): EmbeddingInference {
        return embeddingInference ?: synchronized(this) {
            embeddingInference ?: EmbeddingInference(context.applicationContext).also { embeddingInference = it }
        }
    }

    fun getChatRepository(context: Context): ChatRepository {
        return chatRepository ?: synchronized(this) {
            chatRepository ?: ChatRepository(ChatDatabase.getDatabase(context.applicationContext).chatDao()).also { chatRepository = it }
        }
    }

    fun getGemmaInference(context: Context): GemmaInference {
        return gemmaInference ?: synchronized(this) {
            gemmaInference ?: GemmaInference(context.applicationContext).also { gemmaInference = it }
        }
    }

    fun getSettingsManager(context: Context): SettingsManager {
        return settingsManager ?: synchronized(this) {
            settingsManager ?: SettingsManager(context.applicationContext).also { settingsManager = it }
        }
    }

    fun getAudioRecorder(context: Context): AudioRecorder {
        return audioRecorder ?: synchronized(this) {
            audioRecorder ?: AudioRecorder(context.applicationContext).also { audioRecorder = it }
        }
    }

    fun getTtsManager(context: Context): TtsManager {
        return ttsManager ?: synchronized(this) {
            ttsManager ?: TtsManager(context.applicationContext).also { ttsManager = it }
        }
    }
}
