package com.example.ai_assis.domain.usecase

import com.example.ai_assis.domain.model.ChatMessage
import javax.inject.Inject

class BuildContextMemoryUseCase @Inject constructor() {
    operator fun invoke(
        message: ChatMessage,
        history: List<String>,
    ): List<String> {
        return (history.takeLast(2) + message.message)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeLast(3)
    }
}
