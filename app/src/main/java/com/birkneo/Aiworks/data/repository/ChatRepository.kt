package com.birkneo.Aiworks.data.repository

import com.birkneo.Aiworks.data.ChatMessage
import com.birkneo.Aiworks.data.MessageRole
import com.birkneo.Aiworks.data.dao.ChatDao
import com.birkneo.Aiworks.data.entity.ChatMessageEntity
import com.birkneo.Aiworks.data.entity.ChatSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class ChatRepository(private val chatDao: ChatDao) {

    // Volatile memory for incognito sessions (RAM-only)
    private val _volatileMessages = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getSessionFlowById(sessionId).flatMapLatest { session ->
            if (session?.isIncognito == true) {
                _volatileMessages.map { it[sessionId] ?: emptyList() }
            } else {
                chatDao.getMessagesForSession(sessionId).map { entities ->
                    entities.map { it.toDomain() }
                }
            }
        }
    }

    suspend fun insertMessage(sessionId: String, message: ChatMessage) {
        val session = chatDao.getSessionById(sessionId)
        if (session?.isIncognito == true) {
            _volatileMessages.update { current ->
                val list = current[sessionId] ?: emptyList()
                current + (sessionId to (list + message))
            }
        } else {
            chatDao.insertMessage(message.toEntity(sessionId))
        }
    }

    suspend fun deleteMessageById(sessionId: String, messageId: String) {
        val session = chatDao.getSessionById(sessionId)
        if (session?.isIncognito == true) {
            _volatileMessages.update { current ->
                val list = current[sessionId] ?: emptyList()
                current + (sessionId to list.filter { it.id != messageId })
            }
        } else {
            chatDao.deleteMessageById(messageId)
        }
    }

    suspend fun clearMessagesForSession(sessionId: String) {
        val session = chatDao.getSessionById(sessionId)
        if (session?.isIncognito == true) {
            _volatileMessages.update { it - sessionId }
        } else {
            chatDao.deleteMessagesForSession(sessionId)
        }
    }

    suspend fun getRecentMessages(sessionId: String, limit: Int): List<ChatMessage> {
        val session = chatDao.getSessionById(sessionId)
        return if (session?.isIncognito == true) {
            _volatileMessages.value[sessionId]?.takeLast(limit) ?: emptyList()
        } else {
            chatDao.getRecentMessagesForSession(sessionId, limit).reversed().map { it.toDomain() }
        }
    }

    suspend fun convertIncognitoToRegular(sessionId: String) {
        val volatileList = _volatileMessages.value[sessionId] ?: return
        volatileList.forEach { msg ->
            chatDao.insertMessage(msg.toEntity(sessionId))
        }
        _volatileMessages.update { it - sessionId }
    }

    // Proxy methods for DAO
    fun getAllSessionsFlow() = chatDao.getAllSessions()
    suspend fun getSessionById(id: String) = chatDao.getSessionById(id)
    fun getSessionFlowById(id: String) = chatDao.getSessionFlowById(id)
    suspend fun insertSession(session: ChatSession) = chatDao.insertSession(session)
    suspend fun deleteSession(session: ChatSession) = chatDao.deleteSession(session)
    suspend fun updateSessionTitle(id: String, title: String) = chatDao.updateSessionTitle(id, title)
    suspend fun updateSessionMemory(id: String, memory: String?) = chatDao.updateSessionMemory(id, memory)
    suspend fun updateSessionIncognito(id: String, incognito: Boolean) = chatDao.updateSessionIncognito(id, incognito)
    suspend fun updateSessionFavorite(id: String, favorite: Boolean) = chatDao.updateSessionFavorite(id, favorite)
    suspend fun updateSessionImage(id: String, imageUri: String?) = chatDao.updateSessionImage(id, imageUri)
    suspend fun updateSessionBackground(id: String, backgroundUri: String?) = chatDao.updateSessionBackground(id, backgroundUri)
    suspend fun updateSessionPersona(id: String, persona: String?) = chatDao.updateSessionPersona(id, persona)
    suspend fun updateSessionReasoning(id: String, enabled: Boolean) = chatDao.updateSessionReasoning(id, enabled)
    suspend fun deleteIncognitoSessions() = chatDao.deleteIncognitoSessions()
    suspend fun getLastMessage(sessionId: String) = chatDao.getLastMessageForSession(sessionId)?.toDomain()
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long) = chatDao.deleteMessagesAfter(sessionId, timestamp)
    suspend fun updateMessageText(messageId: String, text: String) = chatDao.updateMessageText(messageId, text)

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        role = role,
        text = text,
        imageUri = imageUri,
        audioUri = audioUri,
        isStreaming = false
    )

    private fun ChatMessage.toEntity(sessionId: String) = ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        role = role,
        text = text,
        imageUri = imageUri,
        audioUri = audioUri
    )
}
