package com.example.ai_assis.domain.model

data class AppBehaviorPolicy(
    val appPackage: String,
    val defaultTone: ReplyTone,
    val defaultLength: ReplyLength,
    val preferQuickReactionsForMedia: Boolean = true,
)

object AppBehaviorPolicies {
    private val defaults = listOf(
        AppBehaviorPolicy(
            appPackage = "com.whatsapp",
            defaultTone = ReplyTone.CASUAL,
            defaultLength = ReplyLength.MEDIUM,
        ),
        AppBehaviorPolicy(
            appPackage = "com.instagram.android",
            defaultTone = ReplyTone.FUNNY,
            defaultLength = ReplyLength.SHORT,
        ),
    )

    fun forApp(appPackage: String): AppBehaviorPolicy? = defaults.firstOrNull { it.appPackage == appPackage }
}
