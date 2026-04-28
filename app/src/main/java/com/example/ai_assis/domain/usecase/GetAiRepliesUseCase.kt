package com.example.ai_assis.domain.usecase

import com.example.ai_assis.domain.repository.OpenAiRepository
import com.example.ai_assis.domain.repository.ToneRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class GetAiRepliesUseCase @Inject constructor(
    private val openAiRepository: OpenAiRepository,
    private val toneRepository: ToneRepository,
) {
    operator fun invoke(message: String): Flow<Result<List<String>>> = flow {
        emit(runCatching {
            val tone = toneRepository.toneFlow.first()
            openAiRepository.getReplies(message = message, tone = tone)
        })
    }
}
