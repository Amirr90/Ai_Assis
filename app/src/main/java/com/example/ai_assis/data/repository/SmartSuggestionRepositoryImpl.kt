package com.example.ai_assis.data.repository

import com.example.ai_assis.data.local.OnDeviceSuggestionGenerator
import com.example.ai_assis.data.local.TonePreferencesDataStore
import com.example.ai_assis.data.remote.OpenAiApiService
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.model.SuggestionTone
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SmartSuggestionRepositoryImpl @Inject constructor(
    private val tonePreferencesDataStore: TonePreferencesDataStore,
    private val onDeviceSuggestionGenerator: OnDeviceSuggestionGenerator,
    private val openAiApiService: OpenAiApiService,
) : SmartSuggestionRepository {
    override suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return runCatching { onDeviceSuggestionGenerator.generate(context) }
    }

    override suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return runCatching {
            val compiledConversationContext = buildCompiledConversationContext(
                sender = context.sender,
                recentMessages = context.recentMessages.takeLast(8),
                latestMessage = context.latestMessage,
            )
            val replies = openAiApiService.getReplies(
                message = context.latestMessage,
                tone = context.tone.toReplyTone(),
                sender = context.sender,
                recentMessages = context.recentMessages.takeLast(8),
                compiledConversationContext = compiledConversationContext,
                languageHint = context.languageHint,
                styleHint = context.styleHint,
            )
            replies.map { reply ->
                Suggestion(
                    text = reply.trim(),
                    confidence = 0.85,
                    source = SuggestionSource.CLOUD,
                )
            }.take(3)
        }
    }

    override suspend fun saveTone(tone: SuggestionTone) {
        tonePreferencesDataStore.saveTone(tone)
    }

    override fun observeTone(): Flow<SuggestionTone> = tonePreferencesDataStore.observeTone()
}

private fun buildCompiledConversationContext(
    sender: String,
    recentMessages: List<String>,
    latestMessage: String,
): String {
    val contextLines = recentMessages
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .mapIndexed { index, text ->
            val role = if (index % 2 == 0) "CONTACT($sender)" else "USER"
            "$role: $text"
        }
        .toMutableList()

    contextLines += "CONTACT($sender): ${latestMessage.trim()}"
    return contextLines.joinToString(separator = "\n")
}

private fun SuggestionTone.toReplyTone(): ReplyTone {
    return when (this) {
        SuggestionTone.CASUAL -> ReplyTone.CASUAL
        SuggestionTone.PROFESSIONAL -> ReplyTone.PROFESSIONAL
        SuggestionTone.SHORT -> ReplyTone.SHORT
        SuggestionTone.HUMOROUS -> ReplyTone.FUNNY
    }
}
