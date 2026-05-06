package com.example.ai_assis.di

import com.example.ai_assis.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.observer.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient {

        return HttpClient(Android) {

            // =========================
            // JSON
            // =========================
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                        prettyPrint = true
                    }
                )
            }

            // =========================
            // REQUEST + RESPONSE LOGGER
            // =========================
            install(Logging) {

                logger = object : Logger {
                    override fun log(message: String) {
                        Log.d("KTOR_API", message)
                    }
                }

                level = LogLevel.ALL

                sanitizeHeader { header ->
                    header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                            header.equals("x-goog-api-key", ignoreCase = true)
                }
            }

            // =========================
            // RESPONSE OBSERVER
            // =========================
            install(ResponseObserver) {
                onResponse { response ->

                    Log.d("API_RESPONSE", "====================")
                    Log.d("API_RESPONSE", "URL: ${response.request.url}")
                    Log.d("API_RESPONSE", "STATUS: ${response.status}")

                    try {
                        Log.d(
                            "API_RESPONSE",
                            "BODY: ${response.bodyAsText()}"
                        )
                    } catch (e: Exception) {
                        Log.e(
                            "API_RESPONSE",
                            "ERROR: ${e.message}"
                        )
                    }

                    Log.d("API_RESPONSE", "====================")
                }
            }

            // =========================
            // REQUEST INTERCEPTOR
            // =========================
            install(DefaultRequest) {

                Log.d("API_REQUEST", "====================")
                Log.d("API_REQUEST", "URL: ${url.buildString()}")
                Log.d("API_REQUEST", "HEADERS: ${headers.build()}")
                Log.d("API_REQUEST", "====================")
            }
        }
    }
}