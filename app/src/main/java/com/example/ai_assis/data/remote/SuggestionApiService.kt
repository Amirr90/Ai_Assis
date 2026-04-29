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
        val prompt = buildPrompt(request)
        val primaryResponse = callGemini(prompt)
        var parsed = extractSuggestions(primaryResponse)
        Log.d(
            logTag,
            "Gemini primary parsed count=${parsed.size} finishReasons=${primaryResponse.candidates.map { it.finishReason }}",
        )

        if (parsed.isEmpty()) {
            val retryPrompt = buildRetryPrompt(request)
            val retryResponse = callGemini(retryPrompt)
            parsed = extractSuggestions(retryResponse)
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
        return buildString {
            appendLine("Generate exactly 3 short smart replies.")
            appendLine("Tone: ${request.tone}")
            appendLine("Sender: ${request.sender}")
            appendLine("Latest message: ${request.latestMessage}")
            if (request.compiledConversationContext.isNotBlank()) {
                appendLine("Conversation context:")
                appendLine(request.compiledConversationContext)
            }
            if (request.recentMessages.isNotEmpty()) {
                appendLine("Recent messages:")
                request.recentMessages.takeLast(8).forEach { appendLine("- $it") }
            }
            if (!request.languageHint.isNullOrBlank()) {
                appendLine("Language hint: ${request.languageHint}")
            }
            request.promptPolicy.rules.forEach { appendLine("Rule: $it") }
            appendLine("Output requirements:")
            appendLine("- Return plain text only.")
            appendLine("- One reply per line.")
            append("- Each reply <= 90 characters.")
        }
    }

    private fun buildRetryPrompt(request: SuggestionGenerateRequestDto): String {
        return buildString {
            appendLine("Reply strictly with 3 lines only.")
            appendLine("No JSON, no markdown, no bullets.")
            appendLine("Each line is one smart reply under 90 characters.")
            appendLine("Use same language/script as the message.")
            appendLine("Message: ${request.latestMessage}")
            if (request.compiledConversationContext.isNotBlank()) {
                appendLine("Context: ${request.compiledConversationContext}")
            }
        }
    }

    private fun extractSuggestions(response: GeminiGenerateResponseDto): List<String> {
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
                .map { it.take(90) }
                .take(3)
        }

        val lineReplies = rawText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("-").trim().replace(Regex("^\\d+[.)]\\s*"), "") }
            .filter { it.isNotBlank() }
            .distinct()
            .map { it.take(90) }
            .take(3)
            .toList()

        if (lineReplies.isNotEmpty()) return lineReplies

        return rawText
            .split(Regex("[.!?]\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.take(90) }
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
