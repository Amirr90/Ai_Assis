package com.example.ai_assis.data.repository

import android.util.Log
import com.example.ai_assis.data.local.OnDeviceSuggestionGenerator
import com.example.ai_assis.data.local.TonePreferencesDataStore
import com.example.ai_assis.data.remote.OpenAiApiService
import com.example.ai_assis.data.remote.SuggestionApiService
import com.example.ai_assis.data.remote.dto.PromptPolicyDto
import com.example.ai_assis.data.remote.dto.SuggestionGenerateRequestDto
import com.example.ai_assis.data.mapper.SuggestionMapper
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.model.SuggestionTone
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class SmartSuggestionRepositoryImpl @Inject constructor(
    private val tonePreferencesDataStore: TonePreferencesDataStore,
    private val onDeviceSuggestionGenerator: OnDeviceSuggestionGenerator,
    private val openAiApiService: OpenAiApiService,
    private val suggestionApiService: SuggestionApiService,
    private val suggestionMapper: SuggestionMapper,
) : SmartSuggestionRepository {
    private val providerBlockedUntilMs = mutableMapOf<CloudProvider, Long>()

    override suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return runCatching { onDeviceSuggestionGenerator.generate(context) }
    }

    override suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        val cloudStartMs = System.currentTimeMillis()
        val recentMessages = context.recentMessages.takeLast(8)
        val compiledConversationContext = buildCompiledConversationContext(
            sender = context.sender,
            recentMessages = recentMessages,
            latestMessage = context.latestMessage,
        )
        val orderedProviders = orderedProviders()
        Log.d(
            logTag,
            "Cloud suggestion fetch start sender=${context.sender} app=${context.appPackage} providers=$orderedProviders recentCount=${recentMessages.size}",
        )

        var lastError: Throwable? = null
        var anyAttempted = false
        orderedProviders.forEach { provider ->
            if (isProviderOnCooldown(provider)) {
                Log.d(logTag, "Cloud provider skipped (cooldown) provider=$provider")
                return@forEach
            }
            anyAttempted = true
            Log.d(logTag, "Cloud provider attempt provider=$provider")
            val providerStartMs = System.currentTimeMillis()
            val result = fetchCloudSuggestions(
                provider = provider,
                context = context,
                recentMessages = recentMessages,
                compiledConversationContext = compiledConversationContext,
            )
            result.onSuccess { suggestions ->
                if (suggestions.isNotEmpty()) {
                    clearCooldown(provider)
                    Log.d(
                        logTag,
                        "Cloud provider success provider=$provider count=${suggestions.size} durationMs=${System.currentTimeMillis() - providerStartMs}",
                    )
                    Log.d(logTag, "Cloud suggestion fetch success durationMs=${System.currentTimeMillis() - cloudStartMs}")
                    return Result.success(suggestions.take(3))
                }
                Log.d(
                    logTag,
                    "Cloud provider empty provider=$provider durationMs=${System.currentTimeMillis() - providerStartMs}",
                )
            }.onFailure { throwable ->
                val reason = classifyProviderFailure(throwable)
                val wrapped = IllegalStateException(
                    "cloud_error_${provider.name.lowercase()}_$reason",
                    throwable,
                )
                lastError = wrapped
                registerProviderFailure(provider, wrapped)
                Log.w(
                    logTag,
                    "Cloud provider failed provider=$provider reason=$reason durationMs=${System.currentTimeMillis() - providerStartMs}",
                )
            }
        }

        if (!anyAttempted) {
            Log.w(logTag, "Cloud suggestion fetch aborted: both providers on cooldown")
            return Result.failure(IllegalStateException("cloud_error_both_providers_cooldown"))
        }
        Log.w(logTag, "Cloud suggestion fetch failed durationMs=${System.currentTimeMillis() - cloudStartMs}")
        return Result.failure(lastError ?: IllegalStateException("cloud_error_both_providers"))
    }

    private suspend fun fetchCloudSuggestions(
        provider: CloudProvider,
        context: ConversationContext,
        recentMessages: List<String>,
        compiledConversationContext: String,
    ): Result<List<Suggestion>> {
        return try {
            val suggestions = when (provider) {
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
                            maxSuggestions = maxSuggestionCount(context.replyLength),
                            promptPolicy = PromptPolicyDto(),
                        ),
                    )
                    suggestionMapper.fromCloudResponse(response).map { suggestion ->
                        suggestion.copy(text = suggestion.text.take(context.replyLength.maxChars))
                    }
                }
            }.take(3)
            Result.success(suggestions)
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

    override suspend fun saveTone(tone: SuggestionTone) {
        tonePreferencesDataStore.saveTone(tone)
    }

    override fun observeTone(): Flow<SuggestionTone> = tonePreferencesDataStore.observeTone()

    private fun orderedProviders(): List<CloudProvider> {
        return listOf(CloudProvider.OPEN_AI, CloudProvider.GEMINI)
    }

    private fun registerProviderFailure(provider: CloudProvider, throwable: Throwable) {
        val now = System.currentTimeMillis()
        val message = throwable.message.orEmpty().lowercase()
        val cooldownMs = if (message.contains("quota") || message.contains("rate") || message.contains("429")) {
            quotaCooldownMs
        } else {
            transientCooldownMs
        }
        providerBlockedUntilMs[provider] = now + cooldownMs
        Log.d(logTag, "Cloud provider cooldown provider=$provider cooldownMs=$cooldownMs")
    }

    private fun isProviderOnCooldown(provider: CloudProvider): Boolean {
        val until = providerBlockedUntilMs[provider] ?: return false
        return System.currentTimeMillis() < until
    }

    private fun clearCooldown(provider: CloudProvider) {
        providerBlockedUntilMs.remove(provider)
    }

    private fun classifyProviderFailure(throwable: Throwable): String {
        val message = throwable.message.orEmpty().lowercase()
        return when {
            message.contains("quota") -> "quota"
            message.contains("rate") || message.contains("429") -> "rate_limit"
            message.contains("timeout") -> "timeout"
            else -> "generic"
        }
    }
}

private fun maxSuggestionCount(length: ReplyLength): Int {
    return when (length) {
        ReplyLength.SHORT -> 3
        ReplyLength.MEDIUM -> 3
        ReplyLength.DETAILED -> 4
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
private const val transientCooldownMs = 2 * 60 * 1_000L
private const val quotaCooldownMs = 30 * 60 * 1_000L
