package com.example.ai_assis.domain.model

data class Suggestion(
    val text: String,
    val confidence: Double,
    val source: SuggestionSource,
    val safetyFlags: List<String> = emptyList(),
)

enum class SuggestionSource {
    ON_DEVICE,
    CLOUD,
}
