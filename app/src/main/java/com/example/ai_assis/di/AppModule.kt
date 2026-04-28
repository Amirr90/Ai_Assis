package com.example.ai_assis.di

import com.example.ai_assis.data.local.ToneDataStore
import com.example.ai_assis.data.remote.OpenAiApiService
import com.example.ai_assis.data.repository.OpenAiRepositoryImpl
import com.example.ai_assis.domain.repository.OpenAiRepository
import com.example.ai_assis.domain.repository.ToneRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideOpenAiApiService(
        httpClient: io.ktor.client.HttpClient,
    ): OpenAiApiService = OpenAiApiService(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideOpenAiRepository(
        apiService: OpenAiApiService,
    ): OpenAiRepository = OpenAiRepositoryImpl(apiService = apiService)

    @Provides
    @Singleton
    fun provideToneRepository(
        toneDataStore: ToneDataStore,
    ): ToneRepository = toneDataStore
}
