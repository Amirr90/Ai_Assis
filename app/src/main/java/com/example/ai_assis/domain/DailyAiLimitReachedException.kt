package com.example.ai_assis.domain

/** Thrown when a free-tier user exceeds the daily cloud AI usage limit (see [com.example.ai_assis.data.local.UsageManager]). */
class DailyAiLimitReachedException(
    message: String = DEFAULT_MESSAGE,
) : Exception(message) {
    companion object {
        const val DEFAULT_MESSAGE: String = "Daily limit reached. Upgrade for more."
    }
}
