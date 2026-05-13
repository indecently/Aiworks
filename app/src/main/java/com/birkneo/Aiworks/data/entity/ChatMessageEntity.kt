package com.birkneo.Aiworks.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.birkneo.Aiworks.data.MessageRole
import java.util.UUID

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,
    val text: String,
    val imageUri: String? = null,
    val audioUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
