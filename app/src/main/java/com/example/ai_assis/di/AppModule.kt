package com.example.ai_assis.di

import com.example.ai_assis.data.local.MonitoredAppsDataStore
import com.example.ai_assis.data.local.FeaturePreferencesDataStore
import com.example.ai_assis.data.local.ToneDataStore
import com.example.ai_assis.data.remote.OpenAiApiService
import com.example.ai_assis.data.remote.SuggestionApiService
import com.example.ai_assis.data.repository.OpenAiRepositoryImpl
import com.example.ai_assis.data.repository.SmartSuggestionRepositoryImpl
import com.example.ai_assis.domain.repository.MonitoredAppsRepository
import com.example.ai_assis.domain.repository.OpenAiRepository
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.repository.TemplateRepository
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
    fun provideSuggestionApiService(
        httpClient: io.ktor.client.HttpClient,
    ): SuggestionApiService = SuggestionApiService(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideToneRepository(
        toneDataStore: ToneDataStore,
    ): ToneRepository = toneDataStore

    @Provides
    @Singleton
    fun provideSmartSuggestionRepository(
        impl: SmartSuggestionRepositoryImpl,
    ): SmartSuggestionRepository = impl

    @Provides
    @Singleton
    fun provideMonitoredAppsRepository(
        monitoredAppsDataStore: MonitoredAppsDataStore,
    ): MonitoredAppsRepository = monitoredAppsDataStore

    @Provides
    @Singleton
    fun provideFeaturePreferencesRepository(
        featurePreferencesDataStore: FeaturePreferencesDataStore,
    ): FeaturePreferencesRepository = featurePreferencesDataStore

    @Provides
    @Singleton
    fun provideTemplateRepository(
        featurePreferencesDataStore: FeaturePreferencesDataStore,
    ): TemplateRepository = featurePreferencesDataStore
}
