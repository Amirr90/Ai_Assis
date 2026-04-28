package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.service.NotificationEventBus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class BubbleUiState(
    val mode: NotificationEventBus.OverlayMode = NotificationEventBus.OverlayMode.HEAD,
    val unreadCount: Int = 0,
    val items: List<NotificationEventBus.ChatSuggestionItem> = emptyList(),
    val updatesPaused: Boolean = false,
)

@HiltViewModel
class BubbleViewModel @Inject constructor(
) : ViewModel() {
    private val _uiState = MutableStateFlow(BubbleUiState())
    val uiState: StateFlow<BubbleUiState> = _uiState.asStateFlow()

    init {
        observeNotifications()
    }

    private fun observeNotifications() {
        viewModelScope.launch {
            combine(NotificationEventBus.chatHistory, NotificationEventBus.metaState) { history, meta ->
                BubbleUiState(
                    mode = meta.mode,
                    unreadCount = meta.unreadCount,
                    items = history,
                    updatesPaused = meta.updatesPaused,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun openPanel() = NotificationEventBus.setMode(NotificationEventBus.OverlayMode.PANEL)

    fun collapseToHead() = NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)

    fun clearHistory() = NotificationEventBus.clearHistory()

    fun toggleUpdates() = NotificationEventBus.togglePaused()
}
