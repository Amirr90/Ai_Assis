package com.example.ai_assis.service

internal object NotificationSummaryDetector {
    private val summaryExactRegexes = listOf(
        Regex("^\\d+\\s+messages?\\s+from\\s+\\d+\\s+chats?$"),
        Regex("^\\d+\\s+new\\s+messages?(\\b.*)?$"),
        Regex("^\\d+\\s+unread\\s+messages?(\\b.*)?$"),
    )

    private val summaryContainsRegexes = listOf(
        Regex("\\bmessages?\\s+from\\b"),
        Regex("\\bfrom\\s+\\d+\\s+chats?\\b"),
        Regex("\\bmultiple\\s+chats?\\b"),
        Regex("\\bnew\\s+messages?\\s+in\\b"),
        Regex("\\bchats?\\b.*\\bmessages?\\b"),
        Regex("\\bconversations?\\b.*\\bmessages?\\b"),
    )

    fun isLikelySummaryText(text: String): Boolean {
        val value = text.trim().lowercase()
        if (value.isBlank()) return false
        if (summaryExactRegexes.any { it.matches(value) }) return true
        if (summaryContainsRegexes.any { it.containsMatchIn(value) }) return true
        return false
    }
}
