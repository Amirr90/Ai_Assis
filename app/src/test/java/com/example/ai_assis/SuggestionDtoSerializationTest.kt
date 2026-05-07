package com.example.ai_assis

import com.example.ai_assis.data.remote.dto.SuggestionGenerateRequestDto
import com.example.ai_assis.data.remote.dto.SuggestionGenerateResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionDtoSerializationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request dto serializes expected fields`() {
        val dto = SuggestionGenerateRequestDto(
            messageId = "m1",
            appPackage = "com.whatsapp",
            sender = "Bob",
            latestMessage = "Where are you?",
            recentMessages = listOf("Ping"),
            compiledConversationContext = "Friend: Ping",
            tone = "casual",
            languageHint = "en",
            maxSuggestions = 4,
        )

        val encoded = json.encodeToString(SuggestionGenerateRequestDto.serializer(), dto)

        assertTrue(encoded.contains("\"messageId\":\"m1\""))
        assertTrue(encoded.contains("\"maxSuggestions\":4"))
    }

    @Test
    fun `response dto deserializes suggestions`() {
        val payload = """
            {
              "suggestions":[{"text":"On my way","confidence":0.88}],
              "model":"gemini-1.5-flash",
              "latencyMs":121
            }
        """.trimIndent()

        val decoded = json.decodeFromString(SuggestionGenerateResponseDto.serializer(), payload)

        assertEquals(1, decoded.suggestions.size)
        assertEquals("On my way", decoded.suggestions.first().text)
    }
}
