package com.birkneo.Aiworks.ui.chat

import androidx.lifecycle.viewModelScope
import com.birkneo.Aiworks.data.entity.ChatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

fun ChatViewModel.selectSession(sessionId: String) {
    if (currentSessionId == sessionId) return
    stopGeneration()
    stopSpeaking()
    currentSessionId = sessionId
    _currentSessionIdFlow.value = sessionId
    messageCollectionJob?.cancel()
    messageCollectionJob = viewModelScope.launch {
        prepareInferenceForSession(sessionId)
    }
}

fun ChatViewModel.createNewSession(title: String = "New Chat", isIncognito: Boolean = false, onCreated: (String) -> Unit) {
    viewModelScope.launch {
        val session = ChatSession(title = title, isIncognito = isIncognito)
        repository.insertSession(session)
        onCreated(session.id)
    }
}

fun ChatViewModel.deleteSession(session: ChatSession) {
    viewModelScope.launch {
        repository.clearMessagesForSession(session.id)
        repository.deleteSession(session)
        if (currentSessionId == session.id) {
            currentSessionId = null
            _currentSessionIdFlow.value = null
        }
    }
}

fun ChatViewModel.toggleFavorite(sessionId: String, isFavorite: Boolean) {
    viewModelScope.launch {
        repository.updateSessionFavorite(sessionId, isFavorite)
    }
}

fun ChatViewModel.updateSessionImage(sessionId: String, imageUri: String?) {
    viewModelScope.launch {
        repository.updateSessionImage(sessionId, imageUri)
    }
}

fun ChatViewModel.updateSessionBackground(sessionId: String, backgroundUri: String?) {
    viewModelScope.launch {
        repository.updateSessionBackground(sessionId, backgroundUri)
    }
}

fun ChatViewModel.duplicateSession(sessionId: String) {
    viewModelScope.launch {
        val session = repository.getSessionById(sessionId) ?: return@launch
        val newSession = session.copy(
            id = UUID.randomUUID().toString(),
            title = "${session.title} (Copy)",
            timestamp = System.currentTimeMillis(),
            isFavorite = false
        )
        repository.insertSession(newSession)
        
        val messagesList = repository.getRecentMessages(sessionId, 1000)
        messagesList.forEach { msg ->
            repository.insertMessage(newSession.id, msg.copy(id = UUID.randomUUID().toString()))
        }
    }
}

fun ChatViewModel.updateSessionTitle(sessionId: String, title: String) {
    viewModelScope.launch {
        repository.updateSessionTitle(sessionId, title)
    }
}

fun ChatViewModel.toggleIncognito(sessionId: String, isIncognito: Boolean) {
    viewModelScope.launch {
        repository.updateSessionIncognito(sessionId, isIncognito)
        if (!isIncognito) {
            repository.convertIncognitoToRegular(sessionId)
        }
    }
}

fun ChatViewModel.updateSessionReasoning(sessionId: String, enabled: Boolean) {
    viewModelScope.launch {
        repository.updateSessionReasoning(sessionId, enabled)
        if (currentSessionId == sessionId) {
            inferenceSessionId = null
        }
    }
}

fun ChatViewModel.getSessionFlow(sessionId: String): Flow<ChatSession?> {
    return repository.getSessionFlowById(sessionId)
}

suspend fun ChatViewModel.getFullChatText(sessionId: String): String {
    val messagesList = repository.getRecentMessages(sessionId, 1000)
    return messagesList.joinToString("\n\n") { "${it.role}: ${it.text}" }
}

fun ChatViewModel.updateSessionPersona(sessionId: String, persona: String?) {
    viewModelScope.launch {
        repository.updateSessionPersona(sessionId, persona)
        if (currentSessionId == sessionId) {
            inferenceSessionId = null
        }
    }
}

fun ChatViewModel.updateSessionMemory(sessionId: String, memory: String?) {
    viewModelScope.launch {
        repository.updateSessionMemory(sessionId, memory)
        if (currentSessionId == sessionId) {
            inferenceSessionId = null
        }
    }
}

fun ChatViewModel.closeSession() {
    val sessionId = currentSessionId ?: return
    stopGeneration()
    stopSpeaking()
    gemmaInference.cleanupMediaCache()

    viewModelScope.launch {
        val session = repository.getSessionById(sessionId)
        if (session?.isIncognito == true) {
            repository.clearMessagesForSession(sessionId)
            repository.deleteSession(session)
        }
        currentSessionId = null
        _currentSessionIdFlow.value = null
        inferenceSessionId = null
        gemmaInference.resetConversation()
    }
}
