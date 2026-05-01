package com.example.ai_assis.domain.model

data class SuggestionRequest(
    val chatMessage: ChatMessage,
    val tone: ReplyTone,
    val replyLength: ReplyLength,
    val aiEnabled: Boolean,
    val recentMessages: List<String> = emptyList(),
)
