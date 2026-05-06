package com.example.ai_assis.data.remote.model

/**
 * Mirrors the Firestore document at users/{uid}.
 *
 * subscriptionType : "free" | "monthly" | "yearly" | "credits"
 * creditsRemaining : credits left (Credits plan only)
 * freeUsageCount   : total AI suggestions consumed on the free tier (gate = 30)
 * dailyCounts      : map of ISO date "YYYY-MM-DD" → suggestion count (analytics)
 * openAi*          : cumulative OpenAI Chat Completions metrics (token usage + API calls)
 */
data class UserUsageRecord(
    val activePlanId: String = PlanIds.FREE,
    val subscriptionType: String = SubscriptionType.FREE,
    val entitlements: Map<String, Long> = defaultEntitlementsFor(PlanIds.FREE),
    val creditsRemaining: Int = 0,
    val freeUsageCount: Int = 0,
    val planStartedAtMs: Long = 0L,
    val planUpdatedAtMs: Long = 0L,
    val planExpiresAtMs: Long = 0L,
    val dailyCounts: Map<String, Int> = emptyMap(),
    /** Key format: "YYYY-MM-DD_HH" (24h), e.g. "2026-05-03_14". Used for hourly Today chart. */
    val hourlyCounts: Map<String, Int> = emptyMap(),
    /** Successful OpenAI HTTP round-trips (billable requests). */
    val openAiApiCalls: Long = 0,
    val openAiPromptTokensTotal: Long = 0,
    val openAiCompletionTokensTotal: Long = 0,
    val openAiTotalTokensTotal: Long = 0,
) {
    fun resolvedPlanId(): String = when {
        activePlanId.isNotBlank() -> activePlanId
        subscriptionType == SubscriptionType.MONTHLY -> PlanIds.MONTHLY
        subscriptionType == SubscriptionType.YEARLY -> PlanIds.YEARLY
        subscriptionType == SubscriptionType.CREDITS -> PlanIds.CREDITS
        else -> PlanIds.FREE
    }

    fun maxDailySuggestions(): Int {
        val value = entitlements[EntitlementKeys.MAX_DAILY_SUGGESTIONS]
        return value?.toInt() ?: when (resolvedPlanId()) {
            PlanIds.FREE -> FREE_SUGGESTION_LIMIT
            else -> UNLIMITED_DAILY_SUGGESTIONS
        }
    }

    fun hasUnlimitedSuggestions(): Boolean =
        entitlements[EntitlementKeys.UNLIMITED_SUGGESTIONS] == 1L ||
            resolvedPlanId() == PlanIds.MONTHLY ||
            resolvedPlanId() == PlanIds.YEARLY

    fun canGenerate(): Boolean = when {
        hasUnlimitedSuggestions() -> true
        resolvedPlanId() == PlanIds.CREDITS -> creditsRemaining > 0
        else -> freeUsageCount < maxDailySuggestions()
    }

    fun migratedWithDefaults(nowMs: Long): UserUsageRecord {
        val planId = resolvedPlanId()
        val mergedEntitlements = defaultEntitlementsFor(planId) + entitlements
        return copy(
            activePlanId = planId,
            entitlements = mergedEntitlements,
            planStartedAtMs = if (planStartedAtMs > 0L) planStartedAtMs else nowMs,
            planUpdatedAtMs = if (planUpdatedAtMs > 0L) planUpdatedAtMs else nowMs,
        )
    }

    fun isLegacyRecord(): Boolean = activePlanId.isBlank() || entitlements.isEmpty()

    fun isPro(): Boolean =
        hasUnlimitedSuggestions()

    companion object {
        const val FREE_SUGGESTION_LIMIT = 30
        private const val UNLIMITED_DAILY_SUGGESTIONS = Int.MAX_VALUE
    }
}

object SubscriptionType {
    const val FREE = "free"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
    const val CREDITS = "credits"
}

object PlanIds {
    const val FREE = "free"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
    const val CREDITS = "credits"
}

object EntitlementKeys {
    const val MAX_DAILY_SUGGESTIONS = "maxDailySuggestions"
    const val UNLIMITED_SUGGESTIONS = "unlimitedSuggestions"
    const val PRIORITY_SPEED = "prioritySpeed"
    const val ALL_TONES = "allTones"
}

fun defaultEntitlementsFor(planId: String): Map<String, Long> = when (planId) {
    PlanIds.MONTHLY, PlanIds.YEARLY -> mapOf(
        EntitlementKeys.MAX_DAILY_SUGGESTIONS to UserUsageRecord.FREE_SUGGESTION_LIMIT.toLong(),
        EntitlementKeys.UNLIMITED_SUGGESTIONS to 1L,
        EntitlementKeys.PRIORITY_SPEED to 1L,
        EntitlementKeys.ALL_TONES to 1L,
    )
    PlanIds.CREDITS -> mapOf(
        EntitlementKeys.MAX_DAILY_SUGGESTIONS to UserUsageRecord.FREE_SUGGESTION_LIMIT.toLong(),
        EntitlementKeys.UNLIMITED_SUGGESTIONS to 0L,
        EntitlementKeys.PRIORITY_SPEED to 0L,
        EntitlementKeys.ALL_TONES to 0L,
    )
    else -> mapOf(
        EntitlementKeys.MAX_DAILY_SUGGESTIONS to UserUsageRecord.FREE_SUGGESTION_LIMIT.toLong(),
        EntitlementKeys.UNLIMITED_SUGGESTIONS to 0L,
        EntitlementKeys.PRIORITY_SPEED to 0L,
        EntitlementKeys.ALL_TONES to 0L,
    )
}
