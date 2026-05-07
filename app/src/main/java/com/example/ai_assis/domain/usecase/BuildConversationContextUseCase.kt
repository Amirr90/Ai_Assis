package com.example.ai_assis.domain.usecase

import com.example.ai_assis.domain.model.AdaptiveConversationProfile
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.ConversationIntelligence
import com.example.ai_assis.domain.model.ConversationTurn
import com.example.ai_assis.domain.model.LanguagePreference
import com.example.ai_assis.domain.model.MemoryDepth
import com.example.ai_assis.domain.model.ReplyObjective
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.SuggestionFeedbackSnapshot
import java.util.Locale
import javax.inject.Inject

class BuildConversationContextUseCase @Inject constructor() {
    operator fun invoke(
        message: ChatMessage,
        recentTurns: List<ConversationTurn>,
        adaptiveProfile: AdaptiveConversationProfile,
        conversationSummary: String,
        continuityAnchors: List<String>,
        replyLength: ReplyLength = ReplyLength.MEDIUM,
        aiEnabled: Boolean = true,
        adaptiveRepliesEnabled: Boolean = true,
        rememberContextEnabled: Boolean = true,
        memoryDepth: MemoryDepth = MemoryDepth.BALANCED,
        languagePreference: LanguagePreference = LanguagePreference.AUTO,
        languageHint: String? = null,
        styleHint: String? = null,
        highQualityMode: Boolean = false,
        promptTurnCap: Int = 10,
        conversationIntelligence: ConversationIntelligence = ConversationIntelligence(),
        replyObjective: ReplyObjective = ReplyObjective.KEEP_IT_BRIEF,
        feedbackSnapshot: SuggestionFeedbackSnapshot = SuggestionFeedbackSnapshot(),
    ): ConversationContext {
        val cap = promptTurnCap.coerceIn(5, 30)
        val normalizedRecentTurns = recentTurns
            .map { turn -> turn.copy(text = turn.text.trim()) }
            .filter { it.text.isNotBlank() }
            .takeLast(cap)
        val normalizedRecent = normalizedRecentTurns
            .map { it.text }
            .filter { it.isNotBlank() }
        return ConversationContext(
            messageId = "${message.appSource}:${message.sender}:${message.message.hashCode()}",
            appPackage = message.appSource,
            sender = message.sender,
            latestMessage = message.message,
            messageType = message.messageType,
            recentTurns = normalizedRecentTurns,
            recentMessages = normalizedRecent,
            replyLength = replyLength,
            aiEnabled = aiEnabled,
            adaptiveRepliesEnabled = adaptiveRepliesEnabled,
            rememberContextEnabled = rememberContextEnabled,
            memoryDepth = memoryDepth,
            languagePreference = languagePreference,
            languageHint = languageHint ?: resolveLanguageHint(message.message, languagePreference),
            styleHint = styleHint,
            highQualityMode = highQualityMode,
            conversationSummary = conversationSummary.trim(),
            continuityAnchors = continuityAnchors,
            adaptiveProfile = adaptiveProfile,
            promptTurnCap = cap,
            conversationIntelligence = conversationIntelligence,
            replyObjective = replyObjective,
            feedbackSnapshot = feedbackSnapshot,
        )
    }

    companion object {
        fun resolveLanguageHint(message: String, preference: LanguagePreference): String? {
            return when (preference) {
                LanguagePreference.ENGLISH -> Locale.ENGLISH.language
                LanguagePreference.HINDI_HINGLISH -> "hi"
                LanguagePreference.AUTO -> {
                    when {
                        message.any { it.code in 0x0900..0x097F } -> "hi"
                        message.any { it.isLetter() } -> Locale.ENGLISH.language
                        else -> null
                    }
                }
            }
        }
    }
}
