package com.example.ai_assis.service

internal object SelfMessageHeuristics {
    private val selfSenderTokens = setOf(
        "you",
        "you:",
        "me",
        "me:",
    )

    fun isLikelySelfSender(sender: String?, packageName: String): Boolean {
        val normalizedSender = sender.orEmpty().trim().lowercase()
        if (normalizedSender.isBlank()) return false

        // Keep this strict to avoid dropping real incoming messages.
        if (normalizedSender in selfSenderTokens) return true

        return when (packageName) {
            "com.instagram.android" -> normalizedSender == "you sent"
            else -> false
        }
    }

    fun isLikelySelfText(messageText: String): Boolean {
        val normalized = messageText.trim().lowercase()
        return normalized.startsWith("you:") ||
            normalized.startsWith("me:") ||
            normalized.startsWith("you ") ||
            normalized.startsWith("me ")
    }
}
