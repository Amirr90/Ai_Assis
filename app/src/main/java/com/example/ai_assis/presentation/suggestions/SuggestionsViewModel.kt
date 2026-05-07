package com.example.ai_assis.presentation.suggestions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.SuggestionSource
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.domain.usecase.PrepareAdaptiveConversationContextUseCase
import com.example.ai_assis.service.NotificationEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class SuggestionsViewModel @Inject constructor(
    private val repository: SmartSuggestionRepository,
    private val prepareAdaptiveConversationContextUseCase: PrepareAdaptiveConversationContextUseCase,
    private val getHybridSuggestionsUseCase: GetHybridSuggestionsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SuggestionsUiState())
    val uiState: StateFlow<SuggestionsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SuggestionsEffect>()
    val effects: SharedFlow<SuggestionsEffect> = _effects.asSharedFlow()

    private var lastMessage: ChatMessage? = null
    private var lastMessageAt = 0L

    init {
        viewModelScope.launch {
            NotificationEventBus.events.collect { message ->
                onEvent(SuggestionsEvent.NewMessageArrived(message))
            }
        }
    }

    fun onEvent(event: SuggestionsEvent) {
        when (event) {
            is SuggestionsEvent.NewMessageArrived -> handleNewMessage(event.chatMessage)
            is SuggestionsEvent.SuggestionClicked -> emitCopyEffect(event.text)
            SuggestionsEvent.RefreshSuggestions -> refreshLastMessage()
            SuggestionsEvent.DismissError -> _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    private fun handleNewMessage(message: ChatMessage) {
        val now = System.currentTimeMillis()
        val previous = lastMessage
        if (previous != null &&
            previous.sender == message.sender &&
            previous.message == message.message &&
            previous.appSource == message.appSource &&
            previous.isSummaryNotification == message.isSummaryNotification &&
            (now - lastMessageAt) <= duplicateDebounceMs
        ) {
            return
        }

        lastMessage = message
        lastMessageAt = now
        fetchSuggestions(message)
    }

    private fun refreshLastMessage() {
        lastMessage?.let { fetchSuggestions(it) }
    }

    private fun fetchSuggestions(message: ChatMessage) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                latestMessage = message.message,
                senderName = message.sender,
                mediaTypeLabel = message.messageType.toMediaTypeLabel(),
                isLoading = true,
                errorMessage = null,
            )

            val context = runCatching {
                prepareAdaptiveConversationContextUseCase(
                    message = message,
                    highQualityMode = false,
                )
            }.getOrElse { throwable ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.message ?: "Unable to build context",
                )
                return@launch
            }

            getHybridSuggestionsUseCase(context).fold(
                onSuccess = { result ->
                    _uiState.value = _uiState.value.copy(
                        suggestions = result.suggestions.map { SuggestionUiModel(it.text, it.confidence) },
                        source = if (result.source == SuggestionSource.CLOUD) {
                            SuggestionSourceUi.CLOUD
                        } else {
                            SuggestionSourceUi.ON_DEVICE
                        },
                        isLoading = false,
                        errorMessage = null,
                    )
                },
                onFailure = { throwable ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Unable to generate suggestions",
                    )
                },
            )
        }
    }

    private fun emitCopyEffect(text: String) {
        viewModelScope.launch {
            _effects.emit(SuggestionsEffect.CopyToClipboard(text))
        }
    }

    private companion object {
        const val duplicateDebounceMs = 2_500L
    }
}

private fun com.example.ai_assis.domain.model.MessageType.toMediaTypeLabel(): String? {
    return when (this) {
        com.example.ai_assis.domain.model.MessageType.TEXT -> null
        com.example.ai_assis.domain.model.MessageType.REEL -> "Reel received - quick replies only"
        com.example.ai_assis.domain.model.MessageType.IMAGE -> "Photo received - quick replies only"
        com.example.ai_assis.domain.model.MessageType.VIDEO -> "Video received - quick replies only"
        com.example.ai_assis.domain.model.MessageType.AUDIO -> "Audio received - quick replies only"
        com.example.ai_assis.domain.model.MessageType.STICKER -> "Sticker received - quick replies only"
    }
}
