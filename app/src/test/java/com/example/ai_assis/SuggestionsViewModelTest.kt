package com.example.ai_assis

import com.example.ai_assis.data.local.ConversationMemoryStore
import com.example.ai_assis.data.local.MediaReplyProvider
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.domain.model.AdaptiveConversationProfile
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.ConversationTurn
import com.example.ai_assis.domain.model.MemoryDepth
import com.example.ai_assis.domain.model.MessageDirection
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.repository.TemplateRepository
import com.example.ai_assis.domain.usecase.GetCloudSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetLocalFallbackSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetOnDeviceSuggestionsUseCase
import com.example.ai_assis.domain.usecase.PrepareAdaptiveConversationContextUseCase
import com.example.ai_assis.notifications.EngagementNotificationCoordinator
import com.example.ai_assis.presentation.suggestions.SuggestionsEvent
import com.example.ai_assis.presentation.suggestions.SuggestionsViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuggestionsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    @Test
    fun `new message updates suggestion state`() = runTest {
        val repo = VmTestRepository()
        val memoryStore = mockk<ConversationMemoryStore>(relaxed = true)
        coEvery { memoryStore.load(any()) } returns emptyList()
        val prepare = mockk<PrepareAdaptiveConversationContextUseCase>()
        coEvery { prepare(any(), any()) } answers {
            val msg = firstArg<ChatMessage>()
            ConversationContext(
                messageId = "t",
                appPackage = msg.appSource,
                sender = msg.sender,
                latestMessage = msg.message,
                messageType = msg.messageType,
                recentTurns = listOf(
                    ConversationTurn(text = msg.message, direction = MessageDirection.INCOMING),
                ),
                recentMessages = listOf(msg.message),
                memoryDepth = MemoryDepth.BALANCED,
                adaptiveProfile = AdaptiveConversationProfile.NEUTRAL,
            )
        }
        val vm = SuggestionsViewModel(
            repository = repo,
            prepareAdaptiveConversationContextUseCase = prepare,
            getHybridSuggestionsUseCase = vmHybridUseCase(repo),
        )

        vm.onEvent(
            SuggestionsEvent.NewMessageArrived(
                ChatMessage(
                    sender = "Alice",
                    message = "Where are you?",
                    appSource = "com.whatsapp",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("Alice", vm.uiState.value.senderName)
        assertEquals(3, vm.uiState.value.suggestions.size)
    }
}

private fun vmHybridUseCase(repo: SmartSuggestionRepository): GetHybridSuggestionsUseCase {
    val media = MediaReplyProvider()
    val templateRepo = mockk<TemplateRepository>()
    coEvery { templateRepo.templatesFlow } returns kotlinx.coroutines.flow.flowOf(emptyList())
    val usageManager = mockk<UsageManager>(relaxed = true)
    coEvery { usageManager.canUseAI() } returns true
    coEvery { usageManager.incrementUsage() } returns Unit
    val engagement = mockk<EngagementNotificationCoordinator>(relaxed = true)
    return GetHybridSuggestionsUseCase(
        getOnDeviceSuggestionsUseCase = GetOnDeviceSuggestionsUseCase(repo),
        getCloudSuggestionsUseCase = GetCloudSuggestionsUseCase(repo),
        mediaReplyProvider = media,
        getLocalFallbackSuggestionsUseCase = GetLocalFallbackSuggestionsUseCase(media, templateRepo),
        usageManager = usageManager,
        engagementNotificationCoordinator = engagement,
    )
}

private class VmTestRepository : SmartSuggestionRepository {
    override suspend fun getOnDeviceSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return Result.success(
            listOf(
                Suggestion("On my way.", 0.8, SuggestionSource.ON_DEVICE),
                Suggestion("I will reach in 10 mins.", 0.8, SuggestionSource.ON_DEVICE),
                Suggestion("Almost there.", 0.8, SuggestionSource.ON_DEVICE),
            ),
        )
    }

    override suspend fun getCloudSuggestions(context: ConversationContext): Result<List<Suggestion>> {
        return Result.success(emptyList())
    }
}
