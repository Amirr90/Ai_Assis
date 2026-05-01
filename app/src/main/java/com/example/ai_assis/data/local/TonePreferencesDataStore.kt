package com.example.ai_assis.data.local

import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.model.SuggestionTone
import com.example.ai_assis.domain.repository.ToneRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TonePreferencesDataStore @Inject constructor(
    private val toneRepository: ToneRepository,
) {
    fun observeTone(): Flow<SuggestionTone> = toneRepository.toneFlow.map { it.toSuggestionTone() }

    suspend fun saveTone(tone: SuggestionTone) {
        toneRepository.saveTone(tone.toReplyTone())
    }
}

private fun ReplyTone.toSuggestionTone(): SuggestionTone {
    return when (this) {
        ReplyTone.CASUAL -> SuggestionTone.CASUAL
        ReplyTone.PROFESSIONAL -> SuggestionTone.PROFESSIONAL
        ReplyTone.FLIRTY -> SuggestionTone.CASUAL
        ReplyTone.ANGRY -> SuggestionTone.PROFESSIONAL
        ReplyTone.FUNNY -> SuggestionTone.HUMOROUS
        ReplyTone.SHORT -> SuggestionTone.SHORT
    }
}

private fun SuggestionTone.toReplyTone(): ReplyTone {
    return when (this) {
        SuggestionTone.CASUAL -> ReplyTone.CASUAL
        SuggestionTone.PROFESSIONAL -> ReplyTone.PROFESSIONAL
        SuggestionTone.SHORT -> ReplyTone.SHORT
        SuggestionTone.HUMOROUS -> ReplyTone.FUNNY
    }
}
