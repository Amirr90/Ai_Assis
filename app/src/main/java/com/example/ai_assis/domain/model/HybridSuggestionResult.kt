package com.example.ai_assis.domain.model

data class HybridSuggestionResult(
    val suggestions: List<Suggestion>,
    val source: SuggestionSource,
    val fallbackReason: String? = null,
)
