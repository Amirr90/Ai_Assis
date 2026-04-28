package com.example.ai_assis.domain.repository

import com.example.ai_assis.domain.model.ReplyTone
import kotlinx.coroutines.flow.Flow

interface ToneRepository {
    val toneFlow: Flow<ReplyTone>
    suspend fun saveTone(tone: ReplyTone)
}
