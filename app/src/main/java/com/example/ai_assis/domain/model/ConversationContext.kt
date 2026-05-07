package com.example.ai_assis.domain.model

data class ConversationContext(
    val messageId: String,
    val appPackage: String,
    val sender: String,
    val latestMessage: String,
    val messageType: MessageType = MessageType.TEXT,
    val recentTurns: List<ConversationTurn> = emptyList(),
    val recentMessages: List<String>,
    val replyLength: ReplyLength = ReplyLength.MEDIUM,
    val aiEnabled: Boolean = true,
    /** When false, use neutral style hints only (still context-aware). */
    val adaptiveRepliesEnabled: Boolean = true,
    val rememberContextEnabled: Boolean = true,
    val memoryDepth: MemoryDepth = MemoryDepth.BALANCED,
    val languagePreference: LanguagePreference = LanguagePreference.AUTO,
    /** Heuristic/script hint derived from recent text (may be overridden by [languagePreference]). */
    val languageHint: String? = null,
    /** Compact hint for on-device styling (derived from adaptive profile when possible). */
    val styleHint: String? = null,
    val highQualityMode: Boolean = false,
    /** Rolling human-readable synopsis for the model (token-capped upstream). */
    val conversationSummary: String = "",
    /** Short hooks: last tease topic, unanswered question gist, names, etc. */
    val continuityAnchors: List<String> = emptyList(),
    val adaptiveProfile: AdaptiveConversationProfile = AdaptiveConversationProfile.NEUTRAL,
    /**
     * Effective cap on recent turns for cloud trimming (plan cap ∧ memory-depth bias).
     */
    val promptTurnCap: Int = 10,
    val conversationIntelligence: ConversationIntelligence = ConversationIntelligence(),
    val replyObjective: ReplyObjective = ReplyObjective.KEEP_IT_BRIEF,
    val feedbackSnapshot: SuggestionFeedbackSnapshot = SuggestionFeedbackSnapshot(),
)
