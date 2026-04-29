package com.example.ai_assis.data.repository

import android.util.Log
import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.local.OnDeviceSuggestionGenerator
import com.example.ai_assis.data.local.TonePreferencesDataStore
import com.example.ai_assis.data.remote.OpenAiApiService
import com.example.ai_assis.data.remote.SuggestionApiService
import com.example.ai_assis.data.remote.dto.PromptPolicyDto
import com.example.ai_assis.data.remote.dto.SuggestionGenerateRequestDto
import com.example.ai_assis.data.mapper.SuggestionMapper
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
    private val suggestionApiService: SuggestionApiService,
    private val suggestionMapper: SuggestionMapper,
) : SmartSuggestionRepository {
    override suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return runCatching { onDeviceSuggestionGenerator.generate(context) }
    }

    override suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        val recentMessages = context.recentMessages.takeLast(8)
        val compiledConversationContext = buildCompiledConversationContext(
            sender = context.sender,
            recentMessages = recentMessages,
            latestMessage = context.latestMessage,
        )
        val primaryProvider = configuredProvider()
        Log.d(
            logTag,
            "Cloud provider selected=$primaryProvider",
        )

        return fetchCloudSuggestions(
            provider = primaryProvider,
            context = context,
            recentMessages = recentMessages,
            compiledConversationContext = compiledConversationContext,
        )
    }

    private suspend fun fetchCloudSuggestions(
        provider: CloudProvider,
        context: ConversationContext,
        recentMessages: List<String>,
        compiledConversationContext: String,
    ): Result<List<Suggestion>> {
        return runCatching {
            when (provider) {
                CloudProvider.OPEN_AI -> {
                    val replies = openAiApiService.getReplies(
                        message = context.latestMessage,
                        tone = context.tone.toReplyTone(),
                        sender = context.sender,
                        recentMessages = recentMessages,
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
                    }
                }

                CloudProvider.GEMINI -> {
                    val response = suggestionApiService.generateSuggestions(
                        request = SuggestionGenerateRequestDto(
                            messageId = context.messageId,
                            appPackage = context.appPackage,
                            sender = context.sender,
                            latestMessage = context.latestMessage,
                            recentMessages = recentMessages,
                            compiledConversationContext = compiledConversationContext,
                            tone = context.tone.name,
                            languageHint = context.languageHint,
                            maxSuggestions = 3,
                            promptPolicy = PromptPolicyDto(),
                        ),
                    )
                    suggestionMapper.fromCloudResponse(response)
                }
            }.take(3)
        }
    }

    override suspend fun saveTone(tone: SuggestionTone) {
        tonePreferencesDataStore.saveTone(tone)
    }

    override fun observeTone(): Flow<SuggestionTone> = tonePreferencesDataStore.observeTone()

    private fun configuredProvider(): CloudProvider {
        return when (BuildConfig.SUGGESTION_PROVIDER.uppercase()) {
            "OPENAI", "OPEN_AI" -> CloudProvider.OPEN_AI
            else -> CloudProvider.GEMINI
        }
    }
}

private enum class CloudProvider {
    GEMINI,
    OPEN_AI,
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

private const val logTag = "SmartAssistant"
