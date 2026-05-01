package com.example.ai_assis.domain.model

enum class ReplyLength(val displayName: String, val maxChars: Int) {
    SHORT(displayName = "Short", maxChars = 60),
    MEDIUM(displayName = "Medium", maxChars = 120),
    DETAILED(displayName = "Detailed", maxChars = 220),
}
