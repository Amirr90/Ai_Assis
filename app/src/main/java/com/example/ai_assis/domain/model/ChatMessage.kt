package com.example.ai_assis.domain.model

data class ChatMessage(
    val sender: String,
    val message: String,
    val appSource: String,
)
