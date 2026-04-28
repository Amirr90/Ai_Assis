package com.example.ai_assis.service

import com.example.ai_assis.domain.model.ChatMessage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NotificationEventBus {
    enum class OverlayMode { HEAD, PANEL }

    data class ChatSuggestionItem(
        val id: Long = System.currentTimeMillis(),
        val chatMessage: ChatMessage,
        val replies: List<String>,
        val createdAtMs: Long = System.currentTimeMillis(),
        val isRead: Boolean = false,
    )

    data class OverlayMetaState(
        val mode: OverlayMode = OverlayMode.HEAD,
        val unreadCount: Int = 0,
        val isServiceRunning: Boolean = false,
        val updatesPaused: Boolean = false,
    )

    private val _events = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16)
    val events: SharedFlow<ChatMessage> = _events

    private val _chatHistory = MutableStateFlow<List<ChatSuggestionItem>>(emptyList())
    val chatHistory: StateFlow<List<ChatSuggestionItem>> = _chatHistory.asStateFlow()
    private val _metaState = MutableStateFlow(OverlayMetaState())
    val metaState: StateFlow<OverlayMetaState> = _metaState.asStateFlow()

    fun tryEmit(message: ChatMessage) {
        _events.tryEmit(message)
    }

    fun addSuggestion(message: ChatMessage, replies: List<String>) {
        if (replies.isEmpty() || _metaState.value.updatesPaused) return
        val unreadBump = if (_metaState.value.mode == OverlayMode.HEAD) 1 else 0
        _chatHistory.value = (listOf(
            ChatSuggestionItem(chatMessage = message, replies = replies),
        ) + _chatHistory.value).take(20)
        _metaState.value = _metaState.value.copy(unreadCount = _metaState.value.unreadCount + unreadBump)
    }

    fun setMode(mode: OverlayMode) {
        _metaState.value = _metaState.value.copy(
            mode = mode,
            unreadCount = if (mode == OverlayMode.PANEL) 0 else _metaState.value.unreadCount,
        )
        if (mode == OverlayMode.PANEL) markAllAsRead()
    }

    fun togglePaused() {
        _metaState.value = _metaState.value.copy(updatesPaused = !_metaState.value.updatesPaused)
    }

    fun clearHistory() {
        _chatHistory.value = emptyList()
        _metaState.value = _metaState.value.copy(unreadCount = 0)
    }

    fun setServiceRunning(running: Boolean) {
        _metaState.value = _metaState.value.copy(isServiceRunning = running)
    }

    private fun markAllAsRead() {
        _chatHistory.value = _chatHistory.value.map { it.copy(isRead = true) }
    }
}
