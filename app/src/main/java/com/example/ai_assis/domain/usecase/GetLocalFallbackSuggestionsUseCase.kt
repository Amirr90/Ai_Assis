package com.example.ai_assis.domain.usecase

import com.example.ai_assis.data.local.MediaReplyProvider
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.CustomTemplate
import com.example.ai_assis.domain.model.MessageType
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.repository.TemplateRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class GetLocalFallbackSuggestionsUseCase @Inject constructor(
    private val mediaReplyProvider: MediaReplyProvider,
    private val templateRepository: TemplateRepository,
) {
    suspend operator fun invoke(message: ChatMessage): List<Suggestion> {
        if (message.messageType != MessageType.TEXT) {
            return mediaReplyProvider.getReplies(
                messageType = message.messageType,
                appPackage = message.appSource,
            )
        }
        val templates = templateRepository.templatesFlow.first()
        return rankTemplates(templates, message.appSource).map {
            Suggestion(
                text = it.text,
                confidence = 0.92,
                source = SuggestionSource.ON_DEVICE,
            )
        }
    }

    private fun rankTemplates(
        templates: List<CustomTemplate>,
        appPackage: String,
    ): List<CustomTemplate> {
        return templates
            .sortedWith(
                compareByDescending<CustomTemplate> { it.appPackage == appPackage }
                    .thenByDescending { it.lastUsedAtMs },
            )
            .take(4)
    }
}
