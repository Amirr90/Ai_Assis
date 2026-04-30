package com.example.ai_assis.domain.model

data class ChatMessage(
    val sender: String,
    val message: String,
    val appSource: String,
    val replyActionKey: String? = null,
    val isSummaryNotification: Boolean = false,
    val messageType: MessageType = MessageType.TEXT,
    val direction: MessageDirection = MessageDirection.INCOMING,
)
