package com.example.ai_assis.data.remote

import com.example.ai_assis.BuildConfig
import com.example.ai_assis.data.remote.dto.SuggestionGenerateRequestDto
import com.example.ai_assis.data.remote.dto.SuggestionGenerateResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import javax.inject.Inject

class SuggestionApiService @Inject constructor(
    private val httpClient: HttpClient,
) {
    suspend fun generateSuggestions(request: SuggestionGenerateRequestDto): SuggestionGenerateResponseDto {
        return httpClient.post("${BuildConfig.GEMINI_BACKEND_BASE_URL}/v1/suggestions/generate") {
            contentType(ContentType.Application.Json)
            if (BuildConfig.GEMINI_BACKEND_API_KEY.isNotBlank()) {
                header(HttpHeaders.Authorization, "Bearer ${BuildConfig.GEMINI_BACKEND_API_KEY}")
            }
            setBody(request)
        }.body()
    }
}
