package com.example.ai_assis.domain.usecase

import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import javax.inject.Inject

class GetCloudSuggestionsUseCase @Inject constructor(
    private val repository: SmartSuggestionRepository,
) {
    suspend operator fun invoke(context: ConversationContext): Result<List<Suggestion>> {
        return repository.getCloudSuggestions(context)
    }
}
