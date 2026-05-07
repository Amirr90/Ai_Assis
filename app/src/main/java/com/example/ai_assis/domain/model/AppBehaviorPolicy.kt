package com.example.ai_assis.domain.model

data class AppBehaviorPolicy(
    val appPackage: String,
    val defaultLength: ReplyLength,
    val defaultMemoryDepth: MemoryDepth = MemoryDepth.BALANCED,
    val preferQuickReactionsForMedia: Boolean = true,
)

object AppBehaviorPolicies {
    private val defaults = listOf(
        AppBehaviorPolicy(
            appPackage = "com.whatsapp",
            defaultLength = ReplyLength.MEDIUM,
            defaultMemoryDepth = MemoryDepth.BALANCED,
        ),
        AppBehaviorPolicy(
            appPackage = "com.instagram.android",
            defaultLength = ReplyLength.SHORT,
            defaultMemoryDepth = MemoryDepth.LIGHT,
        ),
    )

    fun forApp(appPackage: String): AppBehaviorPolicy? = defaults.firstOrNull { it.appPackage == appPackage }
}
