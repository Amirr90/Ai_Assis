package com.example.ai_assis.data.remote

import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.prompt.PromptPolicyBuilder
import com.example.ai_assis.data.remote.dto.MessageDto
import com.example.ai_assis.data.remote.dto.ReplyRequestDto
import com.example.ai_assis.data.remote.dto.ReplyResponseDto
import com.example.ai_assis.data.remote.model.OpenAiReplyResult
import com.example.ai_assis.data.remote.model.OpenAiTokenUsage
import com.example.ai_assis.domain.model.ConversationContext
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

    suspend fun getRepliesAdaptive(
        context: ConversationContext,
        compiledConversationContext: String,
    ): OpenAiReplyResult {
        val maxChars = PromptPolicyBuilder.effectiveMaxChars(context)
        val languageHint = context.languageHint
        Log.d(
            logTag,
            "OpenAI adaptive request. hasApiKey=${BuildConfig.OPENAI_KEY.isNotBlank()} sender=${context.sender}",
        )
        val userContent = PromptPolicyBuilder.openAiUserPromptBody(
            context = context,
            compiledConversationContext = compiledConversationContext,
        )
        return postChatCompletion(
            userContent = userContent,
            message = context.latestMessage,
            languageHint = languageHint,
            maxChars = maxChars,
        )
    }

    /** Legacy path (settings-free); kept for older use cases. */
    suspend fun getReplies(
        message: String,
        tone: ReplyTone,
        sender: String? = null,
        recentMessages: List<String> = emptyList(),
        compiledConversationContext: String? = null,
        languageHint: String? = null,
        styleHint: String? = null,
    ): OpenAiReplyResult {
        val maxChars = 120
        val prompt = buildString {
            appendLine("You are generating realistic WhatsApp replies.")
            appendLine()
            appendLine("Rules:")
            appendLine("* Sound human; match thread")
            appendLine("* Avoid assistant tone; short natural lines")
            appendLine("* Generate 3 different replies")
            appendLine()
            if (!sender.isNullOrBlank()) appendLine("Chat with: $sender")
            appendLine("Latest message: $message")
            appendLine("Tone hint: ${tone.promptTone}")
            if (!languageHint.isNullOrBlank()) appendLine("Language hint: $languageHint")
            if (!styleHint.isNullOrBlank()) appendLine("Style hint: $styleHint")
            appendLine()
            appendLine("Conversation history:")
            appendLine(compiledConversationContext?.ifBlank { "Friend: $message" } ?: "Friend: $message")
            appendLine()
            appendLine("Return JSON only: {\"replies\":[\"...\",\"...\",\"...\"]} max $maxChars chars each.")
        }
        return postChatCompletion(
            userContent = prompt,
            message = message,
            languageHint = languageHint,
            maxChars = maxChars,
        )
    }

    private suspend fun postChatCompletion(
        userContent: String,
        message: String,
        languageHint: String?,
        maxChars: Int,
    ): OpenAiReplyResult {
        val contextKeywords = extractKeywords(text = userContent)

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
                                content = "Generate realistic chat replies using conversation context. Match language/script. Return JSON {\"replies\":[\"...\",\"...\",\"...\"]}.",
                            ),
                            MessageDto(role = "user", content = userContent),
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

        val firstChoice = parsedResponse.choices.firstOrNull()
        val content = firstChoice?.message?.content.orEmpty()
        val rawCandidates = parseJsonReplies(content).ifEmpty {
            content
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .map { line -> line.removePrefix("-").trim().replace(Regex("^\\d+[.)]\\s*"), "") }
                .toList()
        }

        val ranked = rawCandidates
            .asSequence()
            .map { candidate: String -> candidate.trim() }
            .filter { candidate: String -> candidate.isNotBlank() }
            .distinct()
            .map { candidate: String -> candidate.take(maxChars) }
            .sortedByDescending { candidate ->
                scoreCandidate(
                    candidate = candidate,
                    contextKeywords = contextKeywords,
                    languageHint = languageHint,
                )
            }
            .take(3)
            .toList()

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
    val isHinglish = !isHindi && listOf("kya", "kaise", "kidhar", "kaha", "yaar", "bhai", "chal", "milte").any {
        lower.contains(it)
    }
    return if (isHindi) {
        when {
            lower.contains("?") -> listOf("हाँ, बताओ?", "हाँ बोल, क्या scene?", "हां, करते हैं।")
            lower.contains("कहाँ") || lower.contains("लोकेशन") -> listOf("लोकेशन भेज रहा हूँ।", "मैं पास ही हूँ, आ जा।", "बस 10 मिन में पहुंचता हूँ।")
            lower.contains("धन्यवाद") || lower.contains("शुक्रिया") -> listOf("अरे कोई बात नहीं!", "चल ठीक है yaar.", "Anytime!")
            else -> listOf("ठीक है, चल करते हैं।", "समझ गया, scene set.", "हां, मैं हूँ।")
        }
    } else if (isHinglish) {
        when {
            lower.contains("?") -> listOf("haan bol?", "haan yaar, karte hain.", "scene kya hai?")
            lower.contains("where") || lower.contains("location") || lower.contains("kidhar") || lower.contains("kaha") ->
                listOf("location bhejta hoon.", "main paas hi hoon.", "aa raha hoon, 10 min.")
            lower.contains("thanks") || lower.contains("thx") -> listOf("arey chill.", "koi na yaar.", "anytime bro.")
            else -> listOf("haan done.", "theek hai, chalte hain.", "mast, milte hain.")
        }
    } else {
        when {
            lower.contains("?") -> listOf("yeah, tell me?", "yup, sounds good.", "cool, let's do it.")
            lower.contains("where") || lower.contains("location") -> listOf("sharing location.", "nearby only.", "on my way.")
            lower.contains("thanks") || lower.contains("thank you") || lower.contains("thx") -> listOf("anytime.", "no worries.", "got you.")
            else -> listOf("done.", "sounds good.", "cool, works.")
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
