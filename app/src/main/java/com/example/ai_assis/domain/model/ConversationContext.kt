package com.example.ai_assis.domain.model

data class ConversationContext(
    val messageId: String,
    val appPackage: String,
    val sender: String,
    val latestMessage: String,
    val recentMessages: List<String>,
    val tone: SuggestionTone,
    val languageHint: String? = null,
    val styleHint: String? = null,
    val highQualityMode: Boolean = false,
)
