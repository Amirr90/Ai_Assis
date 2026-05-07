package com.example.ai_assis.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class SuggestionGenerateRequestDto(
    val messageId: String,
    val appPackage: String,
    val sender: String,
    val latestMessage: String,
    val recentMessages: List<String>,
    val compiledConversationContext: String,
    val tone: String,
    val languageHint: String? = null,
    val maxSuggestions: Int = 3,
    val promptPolicy: PromptPolicyDto = PromptPolicyDto(),
)

@Serializable
data class PromptPolicyDto(
    val instruction: String = "Generate contextual smart replies based on latest message and conversation history.",
    val rules: List<String> = listOf(
        "Use latestMessage and compiledConversationContext together before generating replies.",
        "Infer user intent from prior turns and keep continuity with the current thread.",
        "Return exactly 3 concise replies in plain JSON.",
        "Each reply must be <= 90 characters.",
        "Match language/script of the incoming chat message.",
        "Do not include markdown, numbering, or fabricated facts.",
    ),
    val adaptiveCompactLine: String = "",
    val conversationSummary: String = "",
    val continuityAnchors: List<String> = emptyList(),
    val maxCharsPerReply: Int = 120,
    val socialMode: String = "neutral_chat",
    val replyObjective: String = "keep_it_brief",
    val relationshipProfile: String = "unknown",
    val emotionalIntent: String = "confirmation",
    val interactionStyle: String = "",
    val preferredStyle: String = "",
    val candidateBehaviors: List<String> = emptyList(),
)

@Serializable
data class SuggestionGenerateResponseDto(
    val suggestions: List<SuggestionItemDto> = emptyList(),
    val model: String = "gemini-1.5-flash",
    val latencyMs: Long = 0L,
    val safety: SuggestionSafetyDto = SuggestionSafetyDto(),
)

@Serializable
data class SuggestionItemDto(
    val text: String,
    val confidence: Double = 0.7,
)

@Serializable
data class SuggestionSafetyDto(
    val blocked: Boolean = false,
    val reason: String? = null,
)
