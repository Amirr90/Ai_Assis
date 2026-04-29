package com.example.ai_assis

import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.model.SuggestionTone
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.usecase.GetCloudSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetOnDeviceSuggestionsUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetHybridSuggestionsUseCaseTest {

    @Test
    fun `uses on-device result when quality is enough`() = runTest {
        val repo = FakeSmartSuggestionRepository(
            onDevice = listOf(
                suggestion("Sure, sounds good.", 0.8),
                suggestion("Yes, I can do that.", 0.75),
            ),
            cloud = listOf(suggestion("Cloud one", 0.9, SuggestionSource.CLOUD)),
        )
        val useCase = GetHybridSuggestionsUseCase(
            getOnDeviceSuggestionsUseCase = GetOnDeviceSuggestionsUseCase(repo),
            getCloudSuggestionsUseCase = GetCloudSuggestionsUseCase(repo),
        )

        val result = useCase(context()).getOrThrow()

        assertEquals(SuggestionSource.ON_DEVICE, result.source)
        assertEquals(2, result.suggestions.size)
    }

    @Test
    fun `falls back to cloud when on-device confidence is low`() = runTest {
        val repo = FakeSmartSuggestionRepository(
            onDevice = listOf(
                suggestion("ok", 0.2),
                suggestion("fine", 0.2),
            ),
            cloud = listOf(suggestion("I will be there soon.", 0.9, SuggestionSource.CLOUD)),
        )
        val useCase = GetHybridSuggestionsUseCase(
            getOnDeviceSuggestionsUseCase = GetOnDeviceSuggestionsUseCase(repo),
            getCloudSuggestionsUseCase = GetCloudSuggestionsUseCase(repo),
        )

        val result = useCase(context()).getOrThrow()

        assertEquals(SuggestionSource.CLOUD, result.source)
        assertTrue(result.fallbackReason == "low_confidence")
    }

    @Test
    fun `returns on-device when cloud times out`() = runTest {
        val repo = FakeSmartSuggestionRepository(
            onDevice = listOf(suggestion("On device fallback", 0.4), suggestion("On device 2", 0.4)),
            cloud = listOf(suggestion("Cloud", 0.9, SuggestionSource.CLOUD)),
            cloudDelayMs = 2_100L,
        )
        val useCase = GetHybridSuggestionsUseCase(
            getOnDeviceSuggestionsUseCase = GetOnDeviceSuggestionsUseCase(repo),
            getCloudSuggestionsUseCase = GetCloudSuggestionsUseCase(repo),
        )

        val result = useCase(context()).getOrThrow()

        assertEquals(SuggestionSource.ON_DEVICE, result.source)
        assertEquals("cloud_timeout", result.fallbackReason)
    }

    private fun suggestion(text: String, confidence: Double, source: SuggestionSource = SuggestionSource.ON_DEVICE) =
        Suggestion(text = text, confidence = confidence, source = source)

    private fun context() = ConversationContext(
        messageId = "mid-1",
        appPackage = "com.whatsapp",
        sender = "Alice",
        latestMessage = "Are you joining?",
        recentMessages = listOf("Hey"),
        tone = SuggestionTone.CASUAL,
    )
}

private class FakeSmartSuggestionRepository(
    private val onDevice: List<Suggestion>,
    private val cloud: List<Suggestion>,
    private val cloudDelayMs: Long = 0L,
) : SmartSuggestionRepository {
    override suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return Result.success(onDevice)
    }

    override suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        delay(cloudDelayMs)
        return Result.success(cloud)
    }

    override suspend fun saveTone(tone: SuggestionTone) = Unit

    override fun observeTone(): Flow<SuggestionTone> = flowOf(SuggestionTone.CASUAL)
}
