package com.example.ai_assis.domain.usecase

import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.SuggestionTone
import java.util.Locale
import javax.inject.Inject

class BuildConversationContextUseCase @Inject constructor() {
    operator fun invoke(
        message: ChatMessage,
        tone: SuggestionTone,
        recentMessages: List<String>,
        styleHint: String? = null,
        highQualityMode: Boolean = false,
        replyLength: ReplyLength = ReplyLength.MEDIUM,
        aiEnabled: Boolean = true,
    ): ConversationContext {
        val normalizedRecent = recentMessages
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .takeLast(8)
        return ConversationContext(
            messageId = "${message.appSource}:${message.sender}:${message.message.hashCode()}",
            appPackage = message.appSource,
            sender = message.sender,
            latestMessage = message.message,
            messageType = message.messageType,
            recentMessages = normalizedRecent,
            tone = tone,
            replyLength = replyLength,
            aiEnabled = aiEnabled,
            languageHint = detectLanguageHint(message.message),
            styleHint = styleHint,
            highQualityMode = highQualityMode,
        )
    }

    private fun detectLanguageHint(message: String): String? {
        if (message.any { it.code in 0x0900..0x097F }) return "hi"
        if (message.any { it.isLetter() }) return Locale.ENGLISH.language
        return null
    }
}
