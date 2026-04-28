package com.example.ai_assis.domain.repository

import com.example.ai_assis.domain.model.ReplyTone

interface OpenAiRepository {
    suspend fun getReplies(message: String, tone: ReplyTone): List<String>
}
