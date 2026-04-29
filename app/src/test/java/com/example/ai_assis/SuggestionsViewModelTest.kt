package com.example.ai_assis

import com.example.ai_assis.data.local.ConversationCacheDataSource
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.ConversationContext
import com.example.ai_assis.domain.model.Suggestion
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.model.SuggestionTone
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.usecase.BuildConversationContextUseCase
import com.example.ai_assis.domain.usecase.GetCloudSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.domain.usecase.GetOnDeviceSuggestionsUseCase
import com.example.ai_assis.presentation.suggestions.SuggestionsEvent
import com.example.ai_assis.presentation.suggestions.SuggestionsViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
        val vm = SuggestionsViewModel(
            repository = repo,
            cacheDataSource = ConversationCacheDataSource(),
            buildConversationContextUseCase = BuildConversationContextUseCase(),
            getHybridSuggestionsUseCase = GetHybridSuggestionsUseCase(
                getOnDeviceSuggestionsUseCase = GetOnDeviceSuggestionsUseCase(repo),
                getCloudSuggestionsUseCase = GetCloudSuggestionsUseCase(repo),
            ),
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

    override suspend fun saveTone(tone: SuggestionTone) = Unit

    override fun observeTone(): Flow<SuggestionTone> = flowOf(SuggestionTone.CASUAL)
}
