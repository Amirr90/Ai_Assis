package com.example.ai_assis.domain.model

enum class ReplyTone(val displayName: String, val promptTone: String) {
    CASUAL(displayName = "Casual", promptTone = "casual"),
    PROFESSIONAL(displayName = "Professional", promptTone = "professional"),
    FUNNY(displayName = "Funny", promptTone = "funny"),
    SHORT(displayName = "Short", promptTone = "short"),
}
