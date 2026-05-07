package com.example.ai_assis.data.repository

import android.util.Log
import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.local.OnDeviceSuggestionGenerator
import com.example.ai_assis.data.prompt.PromptPolicyBuilder
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.data.remote.OpenAiApiService
import com.example.ai_assis.data.remote.SuggestionApiService
import com.example.ai_assis.data.remote.model.PlanIds
import com.example.ai_assis.data.remote.dto.PromptPolicyDto
import com.example.ai_assis.data.remote.dto.SuggestionGenerateRequestDto
import com.example.ai_assis.data.mapper.SuggestionMapper
import com.example.ai_assis.domain.model.ConversationTurn
import com.example.ai_assis.domain.model.MessageDirection
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

class SmartSuggestionRepositoryImpl @Inject constructor(
    private val onDeviceSuggestionGenerator: OnDeviceSuggestionGenerator,
    private val openAiApiService: OpenAiApiService,
    private val suggestionApiService: SuggestionApiService,
    private val suggestionMapper: SuggestionMapper,
    private val usageManager: UsageManager,
) : SmartSuggestionRepository {
    private val providerBlockedUntilMs = mutableMapOf<CloudProvider, Long>()

    override suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return runCatching { onDeviceSuggestionGenerator.generate(context) }
    }

    override suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        val cloudStartMs = System.currentTimeMillis()
        val contextWindowPolicy = ContextWindowPolicy.resolve(usageManager.activePlanId())
        val effectiveMaxTurns = minOf(contextWindowPolicy.maxTurns, context.promptTurnCap)
        val effectiveMaxChars = minOf(
            contextWindowPolicy.maxHistoryChars,
            estimateCharBudgetForTurns(effectiveMaxTurns),
        )
        val policyForSelection = ContextWindowPolicy(
            planId = contextWindowPolicy.planId,
            maxTurns = effectiveMaxTurns,
            maxHistoryChars = effectiveMaxChars,
        )

        val normalizedTurns = context.recentTurns.ifEmpty {
            context.recentMessages.map { ConversationTurn(text = it, direction = MessageDirection.UNKNOWN) }
        }
        val selectedTurns = selectTurnsForPrompt(
            turns = normalizedTurns,
            policy = policyForSelection,
        )
        val compiledConversationContext = formatConversationHistory(selectedTurns)
        val recentMessages = selectedTurns.map { it.text }
        val currentMessage = selectedTurns.lastOrNull()?.text ?: context.latestMessage
        val requestContext = context.copy(
            latestMessage = currentMessage,
        )
        val orderedProviders = orderedProviders()
        val promptPolicy = PromptPolicyBuilder.geminiPromptPolicy(requestContext)
        Log.d(
            logTag,
            "Cloud suggestion fetch start sender=${context.sender} app=${context.appPackage} providers=$orderedProviders recentCount=${recentMessages.size} plan=${contextWindowPolicy.planId}",
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
                context = requestContext,
                compiledConversationContext = compiledConversationContext,
                promptPolicy = promptPolicy,
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
        compiledConversationContext: String,
        promptPolicy: PromptPolicyDto,
    ): Result<List<Suggestion>> {
        return try {
            val maxChars = PromptPolicyBuilder.effectiveMaxChars(context)
            val suggestions = when (provider) {
                CloudProvider.OPEN_AI -> {
                    val openAiResult = openAiApiService.getRepliesAdaptive(
                        context = context,
                        compiledConversationContext = compiledConversationContext,
                    )
                    usageManager.recordOpenAiUsage(
                        usage = openAiResult.usage,
                        chargedApiCall = openAiResult.chargedApiCall,
                    )
                    if (!openAiResult.chargedApiCall) {
                        return Result.failure(
                            IllegalStateException("cloud_error_open_ai_synthetic_fallback"),
                        )
                    }
                    openAiResult.replies.map { reply ->
                        Suggestion(
                            text = reply.trim().take(maxChars),
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
                            recentMessages = emptyList(),
                            compiledConversationContext = compiledConversationContext,
                            tone = "adaptive",
                            languageHint = context.languageHint,
                            maxSuggestions = maxSuggestionCount(context.replyLength),
                            promptPolicy = promptPolicy,
                        ),
                    )
                    suggestionMapper.fromCloudResponse(response).map { suggestion ->
                        suggestion.copy(text = suggestion.text.take(maxChars))
                    }
                }
            }.take(3).map { it.copy(text = it.text.trim()) }
            Result.success(suggestions)
        } catch (exception: CancellationException) {
            throw exception
        } catch (throwable: Throwable) {
            Result.failure(throwable)
        }
    }

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

private fun selectTurnsForPrompt(
    turns: List<ConversationTurn>,
    policy: ContextWindowPolicy,
): List<ConversationTurn> {
    val dedupedTurns = turns
        .map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotBlank() }
        .filterNot { it.isSystemLike() }
        .fold(mutableListOf<ConversationTurn>()) { acc, turn ->
            if (acc.lastOrNull()?.let { it.direction == turn.direction && it.text == turn.text } == true) {
                acc
            } else {
                acc += turn
                acc
            }
        }

    val windowed = dedupedTurns.takeLast(policy.maxTurns)
    var selected = windowed
    while (selected.joinToString("\n") { it.text }.length > policy.maxHistoryChars && selected.size > 1) {
        selected = selected.drop(1)
    }
    return selected
}

private fun formatConversationHistory(turns: List<ConversationTurn>): String {
    return turns.joinToString(separator = "\n") { turn ->
        val label = when (turn.direction) {
            MessageDirection.OUTGOING_SELF -> "You"
            MessageDirection.INCOMING, MessageDirection.UNKNOWN -> "Friend"
        }
        "$label: ${turn.text}"
    }
}

private fun ConversationTurn.isSystemLike(): Boolean {
    val lower = text.lowercase()
    return lower.startsWith("system:") ||
        lower.contains("missed call") ||
        lower.contains("notification")
}

private fun estimateCharBudgetForTurns(turns: Int): Int {
    return (turns * 130).coerceIn(380, 2000)
}

private const val logTag = BuildConfig.APPLICATION_ID
private const val transientCooldownMs = 2 * 60 * 1_000L
private const val quotaCooldownMs = 30 * 60 * 1_000L

private data class ContextWindowPolicy(
    val planId: String,
    val maxTurns: Int,
    val maxHistoryChars: Int,
) {
    companion object {
        fun resolve(planId: String): ContextWindowPolicy {
            return when (planId) {
                PlanIds.MONTHLY, PlanIds.YEARLY -> ContextWindowPolicy(planId = planId, maxTurns = 10, maxHistoryChars = 1300)
                PlanIds.CREDITS -> ContextWindowPolicy(planId = planId, maxTurns = 7, maxHistoryChars = 850)
                else -> ContextWindowPolicy(planId = PlanIds.FREE, maxTurns = 5, maxHistoryChars = 550)
            }
        }
    }
}
