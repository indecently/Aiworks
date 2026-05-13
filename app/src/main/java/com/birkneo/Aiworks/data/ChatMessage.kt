package com.birkneo.Aiworks.data

import java.util.UUID

enum class MessageRole {
    USER, ASSISTANT
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val imageUri: String? = null,
    val audioUri: String? = null,
    val isStreaming: Boolean = false
)
