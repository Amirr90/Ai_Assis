package com.example.ai_assis.data.remote

import android.util.Log
import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.remote.dto.SuggestionItemDto
import com.example.ai_assis.data.remote.dto.SuggestionSafetyDto
import com.example.ai_assis.data.remote.dto.SuggestionGenerateRequestDto
import com.example.ai_assis.data.remote.dto.SuggestionGenerateResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SuggestionApiService @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun generateSuggestions(request: SuggestionGenerateRequestDto): SuggestionGenerateResponseDto {
        Log.d(logTag, "Gemini request start. hasApiKey=${BuildConfig.GEMINI_BACKEND_API_KEY.isNotBlank()}")
        val maxChars = request.promptPolicy.maxCharsPerReply.coerceIn(40, 400)
        val prompt = buildPrompt(request)
        val primaryResponse = callGemini(prompt)
        var parsed = extractSuggestions(primaryResponse, maxChars)
        Log.d(
            logTag,
            "Gemini primary parsed count=${parsed.size} finishReasons=${primaryResponse.candidates.map { it.finishReason }}",
        )

        if (parsed.isEmpty()) {
            val retryPrompt = buildRetryPrompt(request)
            val retryResponse = callGemini(retryPrompt)
            parsed = extractSuggestions(retryResponse, maxChars)
            Log.d(
                logTag,
                "Gemini retry parsed count=${parsed.size} finishReasons=${retryResponse.candidates.map { it.finishReason }}",
            )
        }

        return SuggestionGenerateResponseDto(
            suggestions = parsed.map { text ->
                SuggestionItemDto(
                    text = text,
                    confidence = 0.82,
                )
            },
            model = "gemini-1.5-flash",
            safety = SuggestionSafetyDto(),
        )
    }

    private suspend fun callGemini(prompt: String): GeminiGenerateResponseDto {
        val model = BuildConfig.GEMINI_MODEL.ifBlank { "gemini-2.0-flash" }
        val response = httpClient.post("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent") {
            contentType(ContentType.Application.Json)
            parameter("key", BuildConfig.GEMINI_BACKEND_API_KEY)
            setBody(
                GeminiGenerateRequestDto(
                    contents = listOf(
                        GeminiContentDto(
                            role = "user",
                            parts = listOf(
                                GeminiPartDto(text = prompt),
                            ),
                        ),
                    ),
                    generationConfig = GeminiGenerationConfigDto(
                        temperature = 0.6,
                        topP = 0.9,
                        maxOutputTokens = 180,
                        candidateCount = 1,
                        responseMimeType = "text/plain",
                    ),
                ),
            )
        }

        val status = response.status
        val rawBody = response.bodyAsText()
        Log.d(
            logTag,
            "Gemini model=$model HTTP status=${status.value} bodyPreview=${rawBody.take(500)}",
        )

        if (!status.isSuccess()) {
            Log.e(logTag, "Gemini request failed. model=$model status=${status.value}")
            return GeminiGenerateResponseDto()
        }

        val parsed = runCatching { json.decodeFromString<GeminiGenerateResponseDto>(rawBody) }
            .getOrElse { error ->
                Log.e(logTag, "Gemini decode failure: ${error.message}")
                GeminiGenerateResponseDto()
            }

        if (parsed.candidates.isEmpty()) {
            val blocked = parsed.promptFeedback?.blockReason ?: "none"
            Log.w(logTag, "Gemini candidates empty. blockReason=$blocked")
        }

        return parsed
    }

    private fun buildPrompt(request: SuggestionGenerateRequestDto): String {
        val pp = request.promptPolicy
        val maxChars = pp.maxCharsPerReply.coerceIn(40, 400)
        return buildString {
            appendLine("You are generating realistic WhatsApp replies.")
            appendLine()
            appendLine(pp.instruction)
            appendLine()
            appendLine("Policy rules:")
            pp.rules.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Mode: ${request.tone}")
            appendLine("Chat with: ${request.sender}")
            appendLine("Latest message: ${request.latestMessage}")
            if (!request.languageHint.isNullOrBlank()) {
                appendLine("Language hint: ${request.languageHint}")
            }
            if (pp.conversationSummary.isNotBlank()) {
                appendLine("Rolling summary: ${pp.conversationSummary}")
            }
            if (pp.continuityAnchors.isNotEmpty()) {
                appendLine("Continuity: ${pp.continuityAnchors.joinToString(", ")}")
            }
            appendLine()
            appendLine("Conversation history:")
            appendLine(request.compiledConversationContext.ifBlank { "Friend: ${request.latestMessage}" })
            appendLine()
            appendLine("Output requirements:")
            appendLine("- Return plain text only.")
            appendLine("- Exactly 3 replies.")
            append("- One reply per line, each <= $maxChars characters.")
        }
    }

    private fun buildRetryPrompt(request: SuggestionGenerateRequestDto): String {
        val maxChars = request.promptPolicy.maxCharsPerReply.coerceIn(40, 400)
        val pp = request.promptPolicy
        return buildString {
            appendLine(pp.instruction)
            appendLine("Reply strictly with 3 lines only.")
            appendLine("No JSON, no markdown, no bullets.")
            appendLine("Each line is one smart reply under $maxChars characters.")
            appendLine("Use same language/script as the thread.")
            appendLine("Follow adaptive style: ${pp.adaptiveCompactLine}")
            if (pp.conversationSummary.isNotBlank()) {
                appendLine("Summary: ${pp.conversationSummary}")
            }
            if (pp.continuityAnchors.isNotEmpty()) {
                appendLine("Continuity anchors: ${pp.continuityAnchors.joinToString(", ")}")
            }
            appendLine("Message: ${request.latestMessage}")
            if (request.compiledConversationContext.isNotBlank()) {
                appendLine("Conversation history:")
                appendLine(request.compiledConversationContext)
            }
        }
    }

    private fun extractSuggestions(response: GeminiGenerateResponseDto, maxChars: Int): List<String> {
        val rawText = response.candidates
            .flatMap { it.content.parts }
            .mapNotNull { it.text }
            .joinToString("\n")

        if (rawText.isBlank()) {
            return emptyList()
        }

        val jsonReplies = parseJsonReplies(rawText)
        if (jsonReplies.isNotEmpty()) {
            return jsonReplies
                .map { it.take(maxChars) }
                .take(3)
        }

        val lineReplies = rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("-").trim().replace(Regex("^\\d+[.)]\\s*"), "") }
            .filter { it.isNotBlank() }
            .distinct()
            .map { it.take(maxChars) }
            .take(3)
            .toList()

        if (lineReplies.isNotEmpty()) return lineReplies

        return rawText
            .split(Regex("[.!?]\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.take(maxChars) }
            .distinct()
            .take(3)
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

    private companion object {
        const val logTag = "SmartAssistant"
    }
}

@Serializable
private data class GeminiGenerateRequestDto(
    val contents: List<GeminiContentDto>,
    val generationConfig: GeminiGenerationConfigDto? = null,
)

@Serializable
private data class GeminiContentDto(
    val role: String? = null,
    val parts: List<GeminiPartDto>,
)

@Serializable
private data class GeminiPartDto(
    val text: String,
)

@Serializable
private data class GeminiGenerationConfigDto(
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxOutputTokens: Int? = null,
    val candidateCount: Int? = null,
    val responseMimeType: String? = null,
)

@Serializable
private data class GeminiGenerateResponseDto(
    val candidates: List<GeminiCandidateDto> = emptyList(),
    @SerialName("promptFeedback")
    val promptFeedback: GeminiPromptFeedbackDto? = null,
)

@Serializable
private data class GeminiCandidateDto(
    val content: GeminiContentDto = GeminiContentDto(parts = emptyList()),
    @SerialName("finishReason")
    val finishReason: String? = null,
)

@Serializable
private data class GeminiPromptFeedbackDto(
    @SerialName("blockReason")
    val blockReason: String? = null,
)
