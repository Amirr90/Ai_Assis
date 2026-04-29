package com.example.ai_assis.data.remote

import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.remote.dto.MessageDto
import com.example.ai_assis.data.remote.dto.ReplyRequestDto
import com.example.ai_assis.data.remote.dto.ReplyResponseDto
import com.example.ai_assis.domain.model.ReplyTone
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import javax.inject.Inject

class OpenAiApiService @Inject constructor(
    private val httpClient: HttpClient,
) {
    suspend fun getReplies(
        message: String,
        tone: ReplyTone,
        sender: String? = null,
        recentMessages: List<String> = emptyList(),
        compiledConversationContext: String? = null,
        languageHint: String? = null,
        styleHint: String? = null,
    ): List<String> {
        val contextKeywords = extractKeywords(
            text = buildString {
                append(message)
                if (!compiledConversationContext.isNullOrBlank()) {
                    append(' ')
                    append(compiledConversationContext)
                }
            },
        )

        val prompt = buildString {
            appendLine("Generate 3 short replies to this message.")
            appendLine("Tone: ${tone.promptTone}")
            if (!sender.isNullOrBlank()) appendLine("Sender: $sender")
            appendLine("Message: $message")
            if (!compiledConversationContext.isNullOrBlank()) {
                appendLine("Compiled conversation context (role-tagged):")
                appendLine(compiledConversationContext)
            }
            if (recentMessages.isNotEmpty()) {
                appendLine("Recent chat context (latest last):")
                recentMessages.takeLast(8).forEach { msg -> appendLine("- $msg") }
            }
            if (!languageHint.isNullOrBlank()) appendLine("Language hint: $languageHint")
            if (!styleHint.isNullOrBlank()) appendLine("Preferred style for this sender: $styleHint")
            appendLine("IMPORTANT: Detect the language/script of the message (English, Hindi, Hinglish, or any other language) and reply in the EXACT SAME language and script.")
            appendLine("If the message mixes Hindi and English (Hinglish), your replies must also be Hinglish.")
            appendLine("Use BOTH latest Message and conversation context to infer current intent and continuity.")
            appendLine("Avoid generic replies (e.g., only 'ok', 'sure', 'got it') unless context clearly demands it.")
            appendLine("Replies should sound human, specific, and relatable to this exact conversation.")
            appendLine("Return EXACTLY 3 replies.")
            appendLine("No markdown. No numbering. No prefixes.")
            append("Each reply must be <= 90 characters, concise, natural, and conversational. Return JSON only: {\"replies\":[\"...\",\"...\",\"...\"]}")
        }

        val response = try {
            httpClient.post(urlString = "https://api.openai.com/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${BuildConfig.OPENAI_KEY}")
                setBody(
                    ReplyRequestDto(
                        model = "gpt-4o-mini",
                        messages = listOf(
                            MessageDto(
                                role = "system",
                                content = "You are a multilingual smart-reply assistant. Use latest message + context, keep continuity, and reply in the same language/script (including Hinglish). Prefer specific, relatable replies over generic acknowledgements. Return JSON: {\"replies\":[\"...\",\"...\",\"...\"]}.",
                            ),
                            MessageDto(role = "user", content = prompt),
                        ),
                    ),
                )
            }.body<ReplyResponseDto>()
        } catch (_: ResponseException) {
            return fallbackReplies(message = message, languageHint = languageHint)
        }

        val content = response.choices.firstOrNull()?.message?.content.orEmpty()
        val rawCandidates = parseJsonReplies(content).ifEmpty {
            content
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { line -> line.removePrefix("-").trim().replace(Regex("^\\d+[.)]\\s*"), "") }
                .toList()
        }

        val ranked = rawCandidates
            .lineSequence()
            .map { it.toString().trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { it.take(90) }
            .sortedByDescending { candidate ->
                scoreCandidate(
                    candidate = candidate,
                    contextKeywords = contextKeywords,
                    languageHint = languageHint,
                )
            }
            .take(3)
            .toList()

        return ranked.ifEmpty { fallbackReplies(message = message, languageHint = languageHint) }
    }
}

private fun parseJsonReplies(content: String): List<String> {
    val match = Regex("\"replies\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(content)
        ?: return emptyList()
    val payload = match.groupValues.getOrNull(1).orEmpty()
    return Regex("\"(.*?)\"", RegexOption.DOT_MATCHES_ALL)
        .findAll(payload)
        .map { it.groupValues[1].replace("\\n", " ").trim() }
        .filter { it.isNotBlank() }
        .toList()
}

private fun scoreCandidate(
    candidate: String,
    contextKeywords: Set<String>,
    languageHint: String?,
): Int {
    val lower = candidate.lowercase()
    var score = 0
    val generic = setOf("ok", "okay", "sure", "got it", "noted", "hmm")
    if (generic.contains(lower.trim())) score -= 5
    if (lower.length in 16..70) score += 2
    score += contextKeywords.count { key -> key.length >= 4 && lower.contains(key) } * 2
    if (languageHint == "hi" && candidate.any { it.code in 0x0900..0x097F }) score += 2
    return score
}

private fun extractKeywords(text: String): Set<String> {
    return text.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .split(Regex("\\s+"))
        .filter { it.length >= 4 }
        .filterNot { it in stopWords }
        .toSet()
}

private fun fallbackReplies(message: String, languageHint: String?): List<String> {
    val isHindi = languageHint == "hi" || message.any { it.code in 0x0900..0x097F }
    return if (isHindi) {
        listOf("ठीक है, मैं इसे देख रहा हूँ।", "समझ गया, अभी जवाब देता हूँ।", "हाँ, इस पर अपडेट देता हूँ।")
    } else {
        listOf("Got it, I will check and update you.", "Understood, I will get back shortly.", "Noted, let me confirm and reply.")
    }
}

private val stopWords = setOf(
    "this", "that", "with", "from", "have", "will", "your", "about", "just", "what",
    "where", "when", "then", "there", "please", "karke", "karte", "nahi", "haan",
)
