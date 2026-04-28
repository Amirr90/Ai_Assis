package com.example.ai_assis.data.remote

import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.remote.dto.MessageDto
import com.example.ai_assis.data.remote.dto.ReplyRequestDto
import com.example.ai_assis.data.remote.dto.ReplyResponseDto
import com.example.ai_assis.domain.model.ReplyTone
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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
    suspend fun getReplies(message: String, tone: ReplyTone): List<String> {
        val prompt = buildString {
            appendLine("Generate 3 short replies to this message.")
            appendLine("Tone: ${tone.promptTone}")
            appendLine("Message: $message")
            append("Replies should be concise and conversational.")
        }

        val response = httpClient.post(urlString = "https://api.openai.com/v1/chat/completions") {
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer ${BuildConfig.OPENAI_KEY}")
            setBody(
                ReplyRequestDto(
                    model = "gpt-4o-mini",
                    messages = listOf(
                        MessageDto(
                            role = "system",
                            content = "You are a reply assistant. Return concise responses.",
                        ),
                        MessageDto(role = "user", content = prompt),
                    ),
                ),
            )
        }.body<ReplyResponseDto>()

        val content = response.choices.firstOrNull()?.message?.content.orEmpty()
        return content
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { line -> line.removePrefix("-").trim().replace(Regex("^\\d+[.)]\\s*"), "") }
            .take(3)
            .toList()
            .ifEmpty { listOf("Sure, I will reply soon.", "Got it.", "Thanks for the message.") }
    }
}
