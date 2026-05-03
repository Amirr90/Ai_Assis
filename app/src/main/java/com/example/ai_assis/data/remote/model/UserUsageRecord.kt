package com.example.ai_assis.data.remote.model

/**
 * Mirrors the Firestore document at users/{uid}.
 *
 * subscriptionType : "free" | "monthly" | "yearly" | "credits"
 * creditsRemaining : credits left (Credits plan only)
 * freeUsageCount   : total AI suggestions consumed on the free tier (gate = 30)
 * dailyCounts      : map of ISO date "YYYY-MM-DD" → suggestion count (analytics)
 */
data class UserUsageRecord(
    val subscriptionType: String = SubscriptionType.FREE,
    val creditsRemaining: Int = 0,
    val freeUsageCount: Int = 0,
    val dailyCounts: Map<String, Int> = emptyMap(),
    /** Key format: "YYYY-MM-DD_HH" (24h), e.g. "2026-05-03_14". Used for hourly Today chart. */
    val hourlyCounts: Map<String, Int> = emptyMap(),
) {
    fun canGenerate(): Boolean = when (subscriptionType) {
        SubscriptionType.MONTHLY, SubscriptionType.YEARLY -> true
        SubscriptionType.CREDITS -> creditsRemaining > 0
        else -> freeUsageCount < FREE_SUGGESTION_LIMIT
    }

    fun isPro(): Boolean =
        subscriptionType == SubscriptionType.MONTHLY ||
            subscriptionType == SubscriptionType.YEARLY

    companion object {
        const val FREE_SUGGESTION_LIMIT = 30
    }
}

object SubscriptionType {
    const val FREE = "free"
    const val MONTHLY = "monthly"
    const val YEARLY = "yearly"
    const val CREDITS = "credits"
}
