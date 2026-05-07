package com.example.ai_assis

import com.example.ai_assis.data.local.MediaReplyProvider
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.domain.DailyAiLimitReachedException
import com.example.ai_assis.domain.model.AdaptiveConversationProfile
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.ConversationTurn
import com.example.ai_assis.domain.model.MessageDirection
import com.example.ai_assis.domain.model.ReplyLength
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.repository.TemplateRepository
import com.example.ai_assis.domain.usecase.GetCloudSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetLocalFallbackSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetOnDeviceSuggestionsUseCase
import com.example.ai_assis.domain.usecase.HumanizeSuggestionsUseCase
import com.example.ai_assis.domain.usecase.ScoreBelievabilityUseCase
import com.example.ai_assis.notifications.EngagementNotificationCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
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
        val useCase = createHybridUseCase(repo = repo)

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
        val usageManager = mockk<UsageManager>()
        coEvery { usageManager.canUseAI() } returns true
        coEvery { usageManager.incrementUsage() } returns Unit
        coEvery { usageManager.decrementUsage() } returns Unit
        every { usageManager.activePlanId() } returns "free"
        val useCase = createHybridUseCase(repo = repo, usageManager = usageManager)

        val result = useCase(context()).getOrThrow()

        assertEquals(SuggestionSource.CLOUD, result.source)
        assertTrue(result.fallbackReason == null || result.fallbackReason == "low_confidence")
        coVerify(exactly = 1) { usageManager.incrementUsage() }
        coVerify(exactly = 0) { usageManager.decrementUsage() }
    }

    @Test
    fun `returns on-device when cloud times out`() = runTest {
        val repo = FakeSmartSuggestionRepository(
            onDevice = listOf(suggestion("On device fallback", 0.4), suggestion("On device 2", 0.4)),
            cloud = listOf(suggestion("Cloud", 0.9, SuggestionSource.CLOUD)),
            cloudDelayMs = 8_000L,
        )
        val usageManager = mockk<UsageManager>()
        coEvery { usageManager.canUseAI() } returns true
        coEvery { usageManager.incrementUsage() } returns Unit
        coEvery { usageManager.decrementUsage() } returns Unit
        every { usageManager.activePlanId() } returns "free"
        val useCase = createHybridUseCase(repo = repo, usageManager = usageManager)

        val result = useCase(context()).getOrThrow()

        assertEquals(SuggestionSource.ON_DEVICE, result.source)
        assertEquals("cloud_timeout", result.fallbackReason)
        coVerify(exactly = 1) { usageManager.incrementUsage() }
        coVerify(exactly = 1) { usageManager.decrementUsage() }
    }

    @Test
    fun `does not call cloud when daily usage limit reached`() = runTest {
        val repo = FakeSmartSuggestionRepository(
            onDevice = listOf(suggestion("ok", 0.2), suggestion("fine", 0.2)),
            cloud = listOf(suggestion("Never returned", 0.9, SuggestionSource.CLOUD)),
        )
        val usageManager = mockk<UsageManager>()
        coEvery { usageManager.canUseAI() } returns false
        coEvery { usageManager.incrementUsage() } returns Unit
        every { usageManager.activePlanId() } returns "free"

        val useCase = createHybridUseCase(repo = repo, usageManager = usageManager)

        val result = useCase(context())

        assertTrue(result.isFailure)
        assertEquals(DailyAiLimitReachedException.DEFAULT_MESSAGE, result.exceptionOrNull()?.message)
        assertEquals(0, repo.cloudInvocationCount)
    }

    @Test
    fun `cloud cache key varies with history`() = runTest {
        val repo = FakeSmartSuggestionRepository(
            onDevice = listOf(suggestion("ok", 0.2), suggestion("fine", 0.2)),
            cloud = listOf(suggestion("I will be there soon.", 0.9, SuggestionSource.CLOUD)),
        )
        val useCase = createHybridUseCase(repo = repo)

        useCase(context(history = listOf("Friend: ping"))).getOrThrow()
        useCase(context(history = listOf("Friend: ping", "You: coming"))).getOrThrow()

        assertEquals(2, repo.cloudInvocationCount)
    }

    private fun suggestion(text: String, confidence: Double, source: SuggestionSource = SuggestionSource.ON_DEVICE) =
        Suggestion(text = text, confidence = confidence, source = source)

    private fun context(history: List<String> = listOf("Hey")) = ConversationContext(
        messageId = "mid-1",
        appPackage = "com.whatsapp",
        sender = "Alice",
        latestMessage = "Are you joining?",
        recentTurns = history.map {
            ConversationTurn(
                text = it,
                direction = if (it.startsWith("You")) MessageDirection.OUTGOING_SELF else MessageDirection.INCOMING,
            )
        },
        recentMessages = history,
        replyLength = ReplyLength.MEDIUM,
        adaptiveProfile = AdaptiveConversationProfile.NEUTRAL,
    )
}

private class FakeSmartSuggestionRepository(
    private val onDevice: List<Suggestion>,
    private val cloud: List<Suggestion>,
    private val cloudDelayMs: Long = 0L,
    var cloudInvocationCount: Int = 0,
) : SmartSuggestionRepository {
    override suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return Result.success(onDevice)
    }

    override suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        cloudInvocationCount += 1
        delay(cloudDelayMs)
        return Result.success(cloud)
    }
}

private fun createHybridUseCase(
    repo: SmartSuggestionRepository,
    usageManager: UsageManager = defaultUsageManager(),
): GetHybridSuggestionsUseCase {
    val media = MediaReplyProvider()
    val templateRepo = mockk<TemplateRepository>()
    coEvery { templateRepo.templatesFlow } returns kotlinx.coroutines.flow.flowOf(emptyList())
    val engagement = mockk<EngagementNotificationCoordinator>(relaxed = true)
    return GetHybridSuggestionsUseCase(
        getOnDeviceSuggestionsUseCase = GetOnDeviceSuggestionsUseCase(repo),
        getCloudSuggestionsUseCase = GetCloudSuggestionsUseCase(repo),
        mediaReplyProvider = media,
        getLocalFallbackSuggestionsUseCase = GetLocalFallbackSuggestionsUseCase(media, templateRepo),
        scoreBelievabilityUseCase = ScoreBelievabilityUseCase(),
        humanizeSuggestionsUseCase = HumanizeSuggestionsUseCase(),
        usageManager = usageManager,
        engagementNotificationCoordinator = engagement,
    )
}

private fun defaultUsageManager(): UsageManager {
    val m = mockk<UsageManager>(relaxed = true)
    coEvery { m.canUseAI() } returns true
    coEvery { m.incrementUsage() } returns Unit
    coEvery { m.decrementUsage() } returns Unit
    every { m.activePlanId() } returns "free"
    return m
}
