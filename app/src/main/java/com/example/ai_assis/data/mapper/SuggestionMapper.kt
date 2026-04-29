package com.example.ai_assis.data.mapper

import com.example.ai_assis.data.remote.dto.SuggestionGenerateResponseDto
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import javax.inject.Inject

class SuggestionMapper @Inject constructor() {
    fun fromCloudResponse(response: SuggestionGenerateResponseDto): List<Suggestion> {
        val safetyFlag = response.safety.reason
        return response.suggestions.map { item ->
            Suggestion(
                text = item.text.trim(),
                confidence = item.confidence.coerceIn(0.0, 1.0),
                source = SuggestionSource.CLOUD,
                safetyFlags = listOfNotNull(safetyFlag),
            )
        }
    }
}
