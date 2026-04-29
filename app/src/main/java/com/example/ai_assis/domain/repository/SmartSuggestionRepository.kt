package com.example.ai_assis.domain.repository

import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionTone
import kotlinx.coroutines.flow.Flow

interface SmartSuggestionRepository {
    suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>>
    suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>>
    suspend fun saveTone(tone: SuggestionTone)
    fun observeTone(): Flow<SuggestionTone>
}
