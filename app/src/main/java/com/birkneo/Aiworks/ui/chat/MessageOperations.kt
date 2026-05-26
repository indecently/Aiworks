package com.birkneo.Aiworks.ui.chat

import androidx.lifecycle.viewModelScope
import com.birkneo.Aiworks.ai.ModelPersistenceService
import com.birkneo.Aiworks.ai.ModelStatus
import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.util.MediaPersistenceManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

fun ChatViewModel.sendMessage(text: String) {
    val sessionId = currentSessionId ?: return
    if (text.isBlank() && pendingImageUri.value == null && pendingAudioUri.value == null) return
    if (isGenerating.value) return

    val imageUri = pendingImageUri.value
    val audioUri = pendingAudioUri.value
    
    _pendingImageUri.value = null
    _pendingAudioUri.value = null

    viewModelScope.launch {
        val session = repository.getSessionById(sessionId)
        val msgId = UUID.randomUUID().toString()
        
        // PERSIST MEDIA: Move files from temp cache/content URIs to permanent internal storage
        val persistedImage = MediaPersistenceManager.persistMedia(getApplication(), imageUri)
        val persistedAudio = MediaPersistenceManager.persistMedia(getApplication(), audioUri)

        repository.insertMessage(sessionId, ChatMessage(
            id = msgId,
            role = MessageRole.USER,
            text = text,
            imageUri = persistedImage,
            audioUri = persistedAudio
        ))
        
        if (session?.title == "New Chat") {
            val newTitle = if (text.length > 20) text.take(17) + "..." else if (text.isBlank()) "Media Message" else text
            repository.updateSessionTitle(sessionId, newTitle)
        }
        
        // Use the persisted URIs for inference as well for consistency
        retriggerInference(text, persistedImage, persistedAudio, msgId)

        val count = (sessionMessageCounters[sessionId] ?: 0) + 1
        sessionMessageCounters[sessionId] = count
        if (count >= 5) {
            sessionMessageCounters[sessionId] = 0
            generateLongTermMemory(sessionId)
        }
    }
}

fun ChatViewModel.deleteMessageById(messageId: String) {
    val sessionId = currentSessionId ?: return
    viewModelScope.launch {
        // Optional: Cleanup disk space on message deletion
        val recent = repository.getRecentMessages(sessionId, 1000)
        val target = recent.find { it.id == messageId }
        MediaPersistenceManager.deleteMedia(target?.imageUri)
        MediaPersistenceManager.deleteMedia(target?.audioUri)
        
        repository.deleteMessageById(sessionId, messageId)
    }
}

fun ChatViewModel.editMessage(messageId: String, newText: String) {
    if (isGenerating.value) return
    viewModelScope.launch {
        try {
            repository.updateMessageText(messageId, newText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun ChatViewModel.undoLastExchange() {
    val sessionId = currentSessionId ?: return
    if (isGenerating.value) return
    
    viewModelScope.launch {
        stopGeneration()
        val lastMsg = repository.getLastMessage(sessionId)
        if (lastMsg != null) {
            repository.deleteMessageById(sessionId, lastMsg.id)
            val precedingMsg = repository.getLastMessage(sessionId)
            if (precedingMsg?.role == MessageRole.USER) {
                repository.deleteMessageById(sessionId, precedingMsg.id)
            }
        }
        inferenceSessionId = null
    }
}

fun ChatViewModel.regenerateResponse() {
    val sessionId = currentSessionId ?: return
    stopGeneration()
    
    viewModelScope.launch {
        try {
            val lastMsg = repository.getLastMessage(sessionId)
            if (lastMsg?.role == MessageRole.ASSISTANT) {
                repository.deleteMessageById(sessionId, lastMsg.id)
                val userMsg = repository.getLastMessage(sessionId)
                if (userMsg?.role == MessageRole.USER) {
                    retriggerInference(userMsg.text, userMsg.imageUri, userMsg.audioUri)
                }
            }
        } catch (e: Exception) {
            _isGenerating.value = false
            ModelPersistenceService.updateStatus(getApplication(), false)
        }
    }
}

fun ChatViewModel.clearChat() {
    val sessionId = currentSessionId ?: return
    viewModelScope.launch {
        stopGeneration()
        repository.clearMessagesForSession(sessionId)
        repository.updateSessionMemory(sessionId, null)
    }
}

fun ChatViewModel.clearChatMemory(sessionId: String) {
    viewModelScope.launch {
        repository.updateSessionMemory(sessionId, null)
        if (currentSessionId == sessionId) inferenceSessionId = null
    }
}

fun ChatViewModel.generateLongTermMemory(sessionId: String) {
    viewModelScope.launch {
        engineMutex.withLock {
            val session = repository.getSessionById(sessionId)
            val msgList = repository.getRecentMessages(sessionId, 10)
            
            if (msgList.size < 2) return@withLock 
            
            _isCompressingContext.value = true
            try {
                val textToSummarize = msgList.filter { it.text.isNotBlank() }
                    .joinToString("\n") { "${it.role}: ${it.text}" }

                if (textToSummarize.isBlank()) return@withLock

                val currentMemory = session?.longTermMemory ?: ""
                val newSummary = if (currentMemory.isNotEmpty()) {
                    gemmaInference.distillMemory(currentMemory, textToSummarize)
                } else {
                    gemmaInference.summarize(textToSummarize)
                }
                
                if (newSummary != null) {
                    repository.updateSessionMemory(sessionId, newSummary)
                }

                if (currentSessionId == sessionId) {
                    cachedSystemPrompt = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isCompressingContext.value = false
            }
        }
    }
}
