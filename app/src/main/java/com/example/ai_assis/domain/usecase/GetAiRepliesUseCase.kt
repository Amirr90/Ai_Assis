package com.example.ai_assis.domain.usecase

import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.OpenAiRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GetAiRepliesUseCase @Inject constructor(
    private val openAiRepository: OpenAiRepository,
) {
    operator fun invoke(message: String): Flow<Result<List<String>>> = flow {
        emit(runCatching {
            openAiRepository.getReplies(message = message, tone = ReplyTone.CASUAL)
        })
    }
}
