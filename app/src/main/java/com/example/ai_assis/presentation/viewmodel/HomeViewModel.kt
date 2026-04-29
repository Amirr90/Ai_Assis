package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.repository.ToneRepository
import com.example.ai_assis.service.NotificationEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardUiState(
    val selectedTone: ReplyTone = ReplyTone.CASUAL,
    val chatHistory: List<NotificationEventBus.ChatSuggestionItem> = emptyList(),
    val overlayMeta: NotificationEventBus.OverlayMetaState = NotificationEventBus.OverlayMetaState(),
)

sealed interface DashboardEvent {
    data class ToneSelected(val tone: ReplyTone) : DashboardEvent
    data object ClearHistoryClicked : DashboardEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val toneRepository: ToneRepository,
) : ViewModel() {
    val selectedTone: StateFlow<ReplyTone> = toneRepository.toneFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReplyTone.CASUAL,
    )

    val toneOptions: StateFlow<List<ReplyTone>> = selectedTone.map { ReplyTone.entries }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReplyTone.entries,
    )

    val chatHistory = NotificationEventBus.chatHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val overlayMeta = NotificationEventBus.metaState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NotificationEventBus.OverlayMetaState(),
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        selectedTone,
        chatHistory,
        overlayMeta,
    ) { tone, history, meta ->
        DashboardUiState(
            selectedTone = tone,
            chatHistory = history,
            overlayMeta = meta,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(),
    )

    fun onEvent(event: DashboardEvent) {
        when (event) {
            is DashboardEvent.ToneSelected -> saveTone(event.tone)
            DashboardEvent.ClearHistoryClicked -> clearHistory()
        }
    }

    fun saveTone(tone: ReplyTone) {
        viewModelScope.launch {
            toneRepository.saveTone(tone)
        }
    }

    fun clearHistory() {
        NotificationEventBus.clearHistory()
    }
}
