package com.example.ai_assis.service

import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.domain.model.SuggestionSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

object NotificationEventBus {
    enum class OverlayMode { HEAD, PANEL }

    enum class ErrorKind {
        NONE,
        DAILY_AI_LIMIT,
        GENERIC,
    }

    data class ChatSuggestionItem(
        val id: Long,
        val chatMessage: ChatMessage,
        val replies: List<String>,
        val source: SuggestionSource = SuggestionSource.ON_DEVICE,
        val fallbackReason: String? = null,
        val createdAtMs: Long = System.currentTimeMillis(),
        val isRead: Boolean = false,
    )

    data class OverlayMetaState(
        val mode: OverlayMode = OverlayMode.HEAD,
        val unreadCount: Int = 0,
        val isServiceRunning: Boolean = false,
        val updatesPaused: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
        val errorKind: ErrorKind = ErrorKind.NONE,
        val isBubbleVisible: Boolean = false,
    )

    private val _events = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16)
    val events: SharedFlow<ChatMessage> = _events

    private val _chatHistory = MutableStateFlow<List<ChatSuggestionItem>>(emptyList())
    val chatHistory: StateFlow<List<ChatSuggestionItem>> = _chatHistory.asStateFlow()

    private val _metaState = MutableStateFlow(OverlayMetaState())
    val metaState: StateFlow<OverlayMetaState> = _metaState.asStateFlow()
    private var lastFailedMessage: ChatMessage? = null
    private var lastRequest: ChatMessage? = null
    private var lastRegenerateAtMs: Long = 0L

    fun tryEmit(message: ChatMessage) {
        lastRequest = message
        _events.tryEmit(message)
    }

    fun addSuggestion(
        message: ChatMessage,
        replies: List<String>,
        source: SuggestionSource = SuggestionSource.ON_DEVICE,
        fallbackReason: String? = null,
    ) {
        if (replies.isEmpty() || _metaState.value.updatesPaused) return
        val now = System.currentTimeMillis()
        val topItem = _chatHistory.value.firstOrNull()
        if (
            topItem != null &&
            topItem.chatMessage.sender == message.sender &&
            topItem.chatMessage.message == message.message &&
            topItem.chatMessage.appSource == message.appSource &&
            topItem.chatMessage.isSummaryNotification == message.isSummaryNotification &&
            topItem.replies == replies &&
            (now - topItem.createdAtMs) <= duplicateSuggestionWindowMs
        ) {
            return
        }

        val unreadBump = if (_metaState.value.mode == OverlayMode.HEAD) 1 else 0
        val newItem = ChatSuggestionItem(
            id = nextItemId.getAndIncrement(),
            chatMessage = message,
            replies = replies,
            source = source,
            fallbackReason = fallbackReason,
            createdAtMs = now,
        )
        _chatHistory.value = (listOf(newItem) + _chatHistory.value).take(20)
        _metaState.value = _metaState.value.copy(
            unreadCount = _metaState.value.unreadCount + unreadBump,
            isLoading = false,
            errorMessage = null,
            errorKind = ErrorKind.NONE,
            isBubbleVisible = true,
        )
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
        _metaState.value = _metaState.value.copy(
            unreadCount = 0,
            isBubbleVisible = false,
            errorMessage = null,
            errorKind = ErrorKind.NONE,
        )
    }

    fun setServiceRunning(running: Boolean) {
        _metaState.value = _metaState.value.copy(
            isServiceRunning = running,
            isBubbleVisible = if (!running) false else _metaState.value.isBubbleVisible,
        )
    }

    fun setLoading(loading: Boolean) {
        _metaState.value = _metaState.value.copy(
            isLoading = loading,
            errorMessage = if (loading) null else _metaState.value.errorMessage,
            errorKind = if (loading) ErrorKind.NONE else _metaState.value.errorKind,
            isBubbleVisible = if (loading) true else _metaState.value.isBubbleVisible,
        )
    }

    fun setError(message: String?, kind: ErrorKind = ErrorKind.GENERIC) {
        _metaState.value = _metaState.value.copy(
            isLoading = false,
            errorMessage = message,
            errorKind = if (message == null) ErrorKind.NONE else kind,
            isBubbleVisible = message != null || _metaState.value.isBubbleVisible,
        )
    }

    fun recordFailure(message: ChatMessage) {
        lastFailedMessage = message
    }

    fun clearFailure() {
        lastFailedMessage = null
    }

    fun retryLastFailedRequest(): Boolean {
        val message = lastFailedMessage ?: return false
        _metaState.value = _metaState.value.copy(
            isLoading = true,
            errorMessage = null,
            errorKind = ErrorKind.NONE,
            isBubbleVisible = true,
        )
        _events.tryEmit(message)
        return true
    }

    fun regenerateLastRequest(): Boolean {
        val message = lastRequest ?: return false
        return regenerateForMessage(message)
    }

    fun regenerateForMessage(message: ChatMessage): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastRegenerateAtMs < regenerateCooldownMs) return false
        lastRegenerateAtMs = now
        lastRequest = message
        _metaState.value = _metaState.value.copy(
            isLoading = true,
            errorMessage = null,
            errorKind = ErrorKind.NONE,
            isBubbleVisible = true,
        )
        _events.tryEmit(message)
        return true
    }

    private fun markAllAsRead() {
        _chatHistory.value = _chatHistory.value.map { it.copy(isRead = true) }
    }

    private val nextItemId = AtomicLong(1L)
    private const val duplicateSuggestionWindowMs = 4_000L
    private const val regenerateCooldownMs = 1_500L
}
