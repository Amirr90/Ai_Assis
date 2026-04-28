package com.example.ai_assis.data.repository

import com.example.ai_assis.data.remote.OpenAiApiService
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.OpenAiRepository
import javax.inject.Inject

class OpenAiRepositoryImpl @Inject constructor(
    private val apiService: OpenAiApiService,
) : OpenAiRepository {
    override suspend fun getReplies(message: String, tone: ReplyTone): List<String> {
        return apiService.getReplies(message = message, tone = tone)
    }
}
