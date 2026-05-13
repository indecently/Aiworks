package com.birkneo.Aiworks.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val longTermMemory: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isIncognito: Boolean = false,
    val persona: String? = null,
    val imageUri: String? = null,
    val backgroundUri: String? = null,
    val isFavorite: Boolean = false
)
