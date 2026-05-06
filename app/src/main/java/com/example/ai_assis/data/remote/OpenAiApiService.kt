package com.example.ai_assis.data.remote

import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.remote.dto.MessageDto
import com.example.ai_assis.data.remote.dto.ReplyRequestDto
import com.example.ai_assis.data.remote.dto.ReplyResponseDto
import com.example.ai_assis.data.remote.model.OpenAiReplyResult
import com.example.ai_assis.data.remote.model.OpenAiTokenUsage
import com.example.ai_assis.domain.model.ReplyTone
import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

class OpenAiApiService @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun getReplies(
        message: String,
        tone: ReplyTone,
        sender: String? = null,
        recentMessages: List<String> = emptyList(),
        compiledConversationContext: String? = null,
        languageHint: String? = null,
        styleHint: String? = null,
    ): OpenAiReplyResult {
        val hasApiKey = BuildConfig.OPENAI_KEY.isNotBlank()
        Log.d(logTag, "OpenAI request start. hasApiKey=$hasApiKey tone=${tone.name} sender=${sender ?: "unknown"}")

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
                        model = "gpt-4.1-nano",
                        messages = listOf(
                            MessageDto(
                                role = "system",
                                content = "You are a multilingual smart-reply assistant. Use latest message + context, keep continuity, and reply in the same language/script (including Hinglish). Prefer specific, relatable replies over generic acknowledgements. Return JSON: {\"replies\":[\"...\",\"...\",\"...\"]}.",
                            ),
                            MessageDto(role = "user", content = prompt),
                        ),
                    ),
                )
            }
        } catch (exception: ResponseException) {
            val statusCode = exception.response.status.value
            val responseBody = runCatching { exception.response.bodyAsText() }.getOrDefault("<unable_to_read_body>")
            Log.e(logTag, "OpenAI HTTP error. status=$statusCode body=$responseBody")
            return OpenAiReplyResult(
                replies = fallbackReplies(message = message, languageHint = languageHint),
                usage = null,
                chargedApiCall = false,
            )
        } catch (exception: CancellationException) {
            Log.d(logTag, "OpenAI request cancelled: ${exception.message}")
            throw exception
        } catch (throwable: Throwable) {
            Log.e(logTag, "OpenAI request failed. ${throwable.message}", throwable)
            return OpenAiReplyResult(
                replies = fallbackReplies(message = message, languageHint = languageHint),
                usage = null,
                chargedApiCall = false,
            )
        }

        val responseBody = runCatching { response.bodyAsText() }.getOrDefault("")
        Log.d(
            logTag,
            "OpenAI success response. status=${response.status.value} bodySnippet=${responseBody.toLogSnippet()}",
        )
        Log.d(logTag, "OpenAI raw response body=$responseBody")
        val parsedResponse = runCatching { json.decodeFromString(ReplyResponseDto.serializer(), responseBody) }
            .getOrElse { parseError ->
                Log.e(logTag, "OpenAI response parse failed. ${parseError.message}")
                return OpenAiReplyResult(
                    replies = fallbackReplies(message = message, languageHint = languageHint),
                    usage = null,
                    chargedApiCall = true,
                )
            }

        val usageSnapshot = parsedResponse.usage?.let { u ->
            OpenAiTokenUsage(
                promptTokens = u.promptTokens,
                completionTokens = u.completionTokens,
                totalTokens = u.totalTokens,
            )
        }
        if (usageSnapshot != null) {
            Log.d(
                logTag,
                "OpenAI usage tokens prompt=${usageSnapshot.promptTokens} completion=${usageSnapshot.completionTokens} total=${usageSnapshot.totalTokens}",
            )
        }

        val firstChoice = parsedResponse.choices.firstOrNull()
        val content = firstChoice?.message?.content.orEmpty()
        Log.d(
            logTag,
            "OpenAI response received. choices=${parsedResponse.choices.size} rawContentLength=${content.length} finishReason=${firstChoice?.finishReason ?: "unknown"}",
        )
        if (content.isBlank()) {
            Log.w(logTag, "OpenAI content is blank. bodySnippet=${responseBody.toLogSnippet()}")
        }
        val rawCandidates = parseJsonReplies(content).ifEmpty {
            content
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { line -> line.removePrefix("-").trim().replace(Regex("^\\d+[.)]\\s*"), "") }
                .toList()
        }
        Log.d(logTag, "OpenAI raw parsed candidates=$rawCandidates")

        val ranked = rawCandidates
            .asSequence()
            .map { candidate: String -> candidate.trim() }
            .filter { candidate: String -> candidate.isNotBlank() }
            .distinct()
            .map { candidate: String -> candidate.take(90) }
            .sortedByDescending { candidate ->
                scoreCandidate(
                    candidate = candidate,
                    contextKeywords = contextKeywords,
                    languageHint = languageHint,
                )
            }
            .take(3)
            .toList()

        Log.d(logTag, "OpenAI final ranked replies count=${ranked.size} replies=$ranked")
        val finalReplies = ranked.ifEmpty { fallbackReplies(message = message, languageHint = languageHint) }
        return OpenAiReplyResult(
            replies = finalReplies,
            usage = usageSnapshot,
            chargedApiCall = true,
        )
    }

    private companion object {
        const val logTag = BuildConfig.APPLICATION_ID
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
    val lower = message.lowercase()
    val isHindi = languageHint == "hi" || message.any { it.code in 0x0900..0x097F }
    return if (isHindi) {
        when {
            lower.contains("?") -> listOf("हाँ, मैं चेक करके बताता हूँ।", "जी, इसका अपडेट अभी देता हूँ।", "ठीक है, मैं कन्फर्म करके बताता हूँ।")
            lower.contains("कहाँ") || lower.contains("लोकेशन") -> listOf("मैं लोकेशन भेज रहा हूँ।", "मैं पास ही हूँ, 10 मिनट में आता हूँ।", "बस पहुँचने वाला हूँ।")
            lower.contains("मीटिंग") || lower.contains("रिपोर्ट") || lower.contains("डेडलाइन") -> listOf("ठीक है, इसे प्राथमिकता देता हूँ।", "मीटिंग से पहले अपडेट शेयर कर दूँगा।", "रिपोर्ट का स्टेटस अभी भेजता हूँ।")
            lower.contains("धन्यवाद") || lower.contains("शुक्रिया") -> listOf("कोई बात नहीं!", "हमेशा मदद के लिए तैयार हूँ।", "खुशी हुई मदद करके।")
            else -> listOf("ठीक है, मैं इसे देख रहा हूँ।", "समझ गया, अभी जवाब देता हूँ।", "हाँ, इस पर अपडेट देता हूँ।")
        }
    } else {
        when {
            lower.contains("?") -> listOf("Yes, let me confirm and get back.", "I will check this and update you shortly.", "Noted, sharing a confirmed update soon.")
            lower.contains("where") || lower.contains("location") || lower.contains("kidhar") || lower.contains("kaha") -> listOf("I am nearby, sharing location now.", "On the way, I will reach soon.", "I am heading there, will update in a bit.")
            lower.contains("meeting") || lower.contains("report") || lower.contains("deadline") -> listOf("Noted, I will prioritize this and update.", "I will share the latest status before the deadline.", "Understood, I will align this before the meeting.")
            lower.contains("thanks") || lower.contains("thank you") || lower.contains("thx") -> listOf("You are welcome, happy to help.", "Anytime, glad this helped.", "No problem, feel free to ping anytime.")
            else -> listOf("Got it, I will check and update you.", "Understood, I will get back shortly.", "Noted, let me confirm and reply.")
        }
    }
}

private val stopWords = setOf(
    "this", "that", "with", "from", "have", "will", "your", "about", "just", "what",
    "where", "when", "then", "there", "please", "karke", "karte", "nahi", "haan",
)

private fun String.toLogSnippet(maxLen: Int = 1200): String {
    val sanitized = replace("\n", "\\n")
    return if (sanitized.length <= maxLen) sanitized else sanitized.take(maxLen) + "...(truncated)"
}
