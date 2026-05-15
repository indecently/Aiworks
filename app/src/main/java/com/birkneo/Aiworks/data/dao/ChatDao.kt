package com.birkneo.Aiworks.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.birkneo.Aiworks.data.entity.ChatMessageEntity
import com.birkneo.Aiworks.data.entity.ChatSession
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    fun getSessionFlowById(sessionId: String): Flow<ChatSession?>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): ChatSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession)

    @Delete
    suspend fun deleteSession(session: ChatSession)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessagesForSession(sessionId: String, limit: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_sessions SET title = :newTitle WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, newTitle: String)

    @Query("UPDATE chat_sessions SET longTermMemory = :memory WHERE id = :sessionId")
    suspend fun updateSessionMemory(sessionId: String, memory: String?)

    @Query("UPDATE chat_sessions SET persona = :persona WHERE id = :sessionId")
    suspend fun updateSessionPersona(sessionId: String, persona: String?)

    @Query("UPDATE chat_sessions SET imageUri = :imageUri WHERE id = :sessionId")
    suspend fun updateSessionImage(sessionId: String, imageUri: String?)

    @Query("UPDATE chat_sessions SET backgroundUri = :backgroundUri WHERE id = :sessionId")
    suspend fun updateSessionBackground(sessionId: String, backgroundUri: String?)

    @Query("UPDATE chat_sessions SET isFavorite = :isFavorite WHERE id = :sessionId")
    suspend fun updateSessionFavorite(sessionId: String, isFavorite: Boolean)

    @Query("UPDATE chat_sessions SET isIncognito = :isIncognito WHERE id = :sessionId")
    suspend fun updateSessionIncognito(sessionId: String, isIncognito: Boolean)

    @Query("UPDATE chat_sessions SET isReasoningMode = :enabled WHERE id = :sessionId")
    suspend fun updateSessionReasoning(sessionId: String, enabled: Boolean)

    @Query("DELETE FROM chat_messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("UPDATE chat_messages SET text = :text WHERE id = :messageId")
    suspend fun updateMessageText(messageId: String, text: String)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId AND timestamp > :timestamp")
    suspend fun deleteMessagesAfter(sessionId: String, timestamp: Long)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForSession(sessionId: String): ChatMessageEntity?

    @Query("DELETE FROM chat_sessions WHERE isIncognito = 1")
    suspend fun deleteIncognitoSessions()
}
