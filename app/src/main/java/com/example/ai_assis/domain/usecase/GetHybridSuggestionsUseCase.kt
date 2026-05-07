package com.example.ai_assis.domain.usecase

import com.example.ai_assis.data.local.MediaReplyProvider
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.notifications.EngagementNotificationCoordinator
import com.example.ai_assis.domain.DailyAiLimitReachedException
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.HybridSuggestionResult
import com.example.ai_assis.domain.model.MessageType
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class GetHybridSuggestionsUseCase @Inject constructor(
    private val getOnDeviceSuggestionsUseCase: GetOnDeviceSuggestionsUseCase,
    private val getCloudSuggestionsUseCase: GetCloudSuggestionsUseCase,
    private val mediaReplyProvider: MediaReplyProvider,
    private val getLocalFallbackSuggestionsUseCase: GetLocalFallbackSuggestionsUseCase,
    private val scoreBelievabilityUseCase: ScoreBelievabilityUseCase,
    private val humanizeSuggestionsUseCase: HumanizeSuggestionsUseCase,
    private val usageManager: UsageManager,
    private val engagementNotificationCoordinator: EngagementNotificationCoordinator,
) {
    private val cacheMutex = Mutex()
    private val cloudCache = mutableMapOf<String, CacheEntry>()
    private var cloudConsecutiveFailures = 0
    private var cloudBlockedUntilMs = 0L

    suspend operator fun invoke(context: ConversationContext): Result<HybridSuggestionResult> {
        if (!context.aiEnabled) {
            val localOnly = getLocalFallbackSuggestionsUseCase(
                ChatMessage(
                    sender = context.sender,
                    message = context.latestMessage,
                    appSource = context.appPackage,
                    messageType = context.messageType,
                ),
            )
            return Result.success(
                HybridSuggestionResult(
                    suggestions = finalize(context, localOnly),
                    source = SuggestionSource.ON_DEVICE,
                    fallbackReason = "ai_disabled",
                ),
            )
        }
        if (context.messageType != MessageType.TEXT) {
            val mediaSuggestions = mediaReplyProvider.getReplies(
                messageType = context.messageType,
                appPackage = context.appPackage,
            )
            return Result.success(
                HybridSuggestionResult(
                    suggestions = finalize(context, mediaSuggestions),
                    source = SuggestionSource.ON_DEVICE,
                    fallbackReason = "media_message",
                ),
            )
        }

        val onDeviceResult = getOnDeviceSuggestionsUseCase(context)
        val onDeviceSuggestions = onDeviceResult.getOrDefault(emptyList())
        val fallbackReason = evaluateFallbackReason(context, onDeviceSuggestions)

        if (fallbackReason == null) {
            return Result.success(
                HybridSuggestionResult(
                    suggestions = finalize(context, onDeviceSuggestions),
                    source = SuggestionSource.ON_DEVICE,
                ),
            )
        }

        val cacheKey = cacheKey(context)
        val cached = getCached(cacheKey)
        if (cached != null) {
            return Result.success(
                HybridSuggestionResult(
                    suggestions = finalize(context, cached),
                    source = SuggestionSource.CLOUD,
                    fallbackReason = "cached",
                ),
            )
        }

        if (isCircuitBreakerOpen()) {
            return Result.success(
                HybridSuggestionResult(
                    suggestions = finalize(context, onDeviceSuggestions),
                    source = SuggestionSource.ON_DEVICE,
                    fallbackReason = "circuit_breaker_open",
                ),
            )
        }

        // Server-authoritative gate (activePlanId/entitlements, with legacy subscription fallback).
        if (!usageManager.canUseAI()) {
            return Result.failure(DailyAiLimitReachedException())
        }

        // Count usage as soon as a cloud generation starts so UI (e.g. daily counter) updates while loading.
        usageManager.incrementUsage()

        val cloudResult = withTimeoutOrNull(cloudTimeoutMs) { getCloudSuggestionsUseCase(context) }
        if (cloudResult == null) {
            usageManager.decrementUsage()
            return Result.success(
                HybridSuggestionResult(
                    suggestions = onDeviceSuggestions,
                    source = SuggestionSource.ON_DEVICE,
                    fallbackReason = "cloud_timeout",
                ),
            )
        }

        return when {
            cloudResult.isSuccess -> {
                val cloudSuggestions = cloudResult.getOrNull().orEmpty()
                cloudConsecutiveFailures = 0
                if (cloudSuggestions.isNotEmpty()) {
                    putCache(cacheKey, cloudSuggestions)
                    engagementNotificationCoordinator.onSuccessfulCloudSuggestion()
                    Result.success(
                        HybridSuggestionResult(
                            suggestions = finalize(context, cloudSuggestions),
                            source = SuggestionSource.CLOUD,
                            fallbackReason = null,
                        ),
                    )
                } else {
                    usageManager.decrementUsage()
                    Result.success(
                        HybridSuggestionResult(
                            suggestions = finalize(context, onDeviceSuggestions),
                            source = SuggestionSource.ON_DEVICE,
                            fallbackReason = "cloud_empty",
                        ),
                    )
                }
            }
            else -> {
                usageManager.decrementUsage()
                registerCloudFailure()
                val throwable = cloudResult.exceptionOrNull()
                val reasonMessage = throwable?.message.orEmpty()
                Result.success(
                    HybridSuggestionResult(
                        suggestions = finalize(context, onDeviceSuggestions),
                        source = SuggestionSource.ON_DEVICE,
                        fallbackReason = when {
                            reasonMessage.contains("cloud_error_both_providers_cooldown") -> "cloud_providers_cooldown"
                            reasonMessage.contains("cloud_error_both_providers") -> "cloud_error_both_providers"
                            reasonMessage.contains("rate_limit") || reasonMessage.contains("quota") -> "cloud_rate_limited"
                            reasonMessage.contains("timeout") -> "cloud_timeout"
                            else -> "cloud_error"
                        },
                    ),
                )
            }
        }
    }

    private fun finalize(context: ConversationContext, suggestions: List<Suggestion>): List<Suggestion> {
        val ranked = scoreBelievabilityUseCase(context, suggestions)
            .map { it.suggestion }
            .take(3)
        return humanizeSuggestionsUseCase(context, ranked)
    }

    private fun evaluateFallbackReason(
        context: ConversationContext,
        onDeviceSuggestions: List<Suggestion>,
    ): String? {
        if (context.highQualityMode) return "high_quality_mode"
        if (onDeviceSuggestions.size < minOnDeviceSuggestionCount) return "insufficient_count"
        val maxLen = context.replyLength.maxChars.coerceIn(40, 400)
        if (onDeviceSuggestions.any { it.text.length > maxLen }) return "length_over_limit"
        if (onDeviceSuggestions.any { it.safetyFlags.isNotEmpty() }) return "blocked_content"

        val avgConfidence = onDeviceSuggestions.map { it.confidence }.average()
        if (avgConfidence < minConfidenceThreshold) return "low_confidence"

        val latestHasDevanagari = context.latestMessage.any { it.code in 0x0900..0x097F }
        val suggestionHasDevanagari = onDeviceSuggestions.any { s -> s.text.any { it.code in 0x0900..0x097F } }
        if (latestHasDevanagari != suggestionHasDevanagari) return "language_mismatch"

        return null
    }

    private suspend fun getCached(key: String): List<Suggestion>? = cacheMutex.withLock {
        val entry = cloudCache[key] ?: return null
        if (System.currentTimeMillis() > entry.expiresAtMs) {
            cloudCache.remove(key)
            return null
        }
        entry.value
    }

    private suspend fun putCache(key: String, suggestions: List<Suggestion>) = cacheMutex.withLock {
        cloudCache[key] = CacheEntry(
            value = suggestions,
            expiresAtMs = System.currentTimeMillis() + cacheTtlMs,
        )
    }

    private fun registerCloudFailure() {
        cloudConsecutiveFailures += 1
        if (cloudConsecutiveFailures >= circuitBreakerFailureThreshold) {
            cloudBlockedUntilMs = System.currentTimeMillis() + circuitBreakerCooldownMs
            cloudConsecutiveFailures = 0
        }
    }

    private fun isCircuitBreakerOpen(): Boolean = System.currentTimeMillis() < cloudBlockedUntilMs

    private fun cacheKey(context: ConversationContext): String {
        val historyFingerprint = context.recentTurns
            .joinToString(separator = "|") { "${it.direction}:${it.text}" }
            .hashCode()
        val styleFingerprint = context.adaptiveProfile.compactPromptLine.hashCode()
        val planId = usageManager.activePlanId()
        return "${context.appPackage}:${context.sender}:$planId:${context.latestMessage.hashCode()}:$historyFingerprint:${context.replyLength.maxChars}:$styleFingerprint"
    }

    private data class CacheEntry(
        val value: List<Suggestion>,
        val expiresAtMs: Long,
    )

    private companion object {
        const val minOnDeviceSuggestionCount = 2
        const val minConfidenceThreshold = 0.55
        const val cloudTimeoutMs = 7_000L
        const val cacheTtlMs = 10 * 60 * 1_000L
        const val circuitBreakerFailureThreshold = 3
        const val circuitBreakerCooldownMs = 5 * 60 * 1_000L
    }
}
