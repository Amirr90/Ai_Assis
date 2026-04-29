package com.example.ai_assis.domain.model

enum class SuggestionTone(val displayName: String, val apiValue: String) {
    CASUAL(displayName = "Casual", apiValue = "casual"),
    PROFESSIONAL(displayName = "Professional", apiValue = "professional"),
    SHORT(displayName = "Short", apiValue = "short"),
    HUMOROUS(displayName = "Humorous", apiValue = "humorous"),
}
