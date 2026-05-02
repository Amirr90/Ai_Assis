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
        /** Incremented on every in-place suggestion update so UI can detect completion without relying on `replies` equality. */
        val contentRevision: Long = 0L,
        /** Regenerate taps recorded for this row; survives overlay UI restarts (max 3 attempts). */
        val regenerateAttemptCount: Int = 0,
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
        /** While system time is before this value, HEAD mode may show over the main app (post-enable peek). */
        val peekBubbleOverOwnAppUntilMs: Long = 0L,
        /** Screen coordinates (px): top-left of the draggable chat head; drives Compose placement when WM uses full-screen. */
        val bubbleAnchorXPx: Int = 0,
        val bubbleAnchorYPx: Int = 0,
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

    fun setBubbleScreenPosition(xPx: Int, yPx: Int) {
        _metaState.value = _metaState.value.copy(
            bubbleAnchorXPx = xPx,
            bubbleAnchorYPx = yPx,
        )
    }

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
        val list = _chatHistory.value
        val matchIndex = list.indexOfFirst { sameConversation(it.chatMessage, message) }

        if (matchIndex >= 0) {
            val existing = list[matchIndex]
            val updated = existing.copy(
                replies = replies,
                source = source,
                fallbackReason = fallbackReason,
                createdAtMs = now,
                contentRevision = existing.contentRevision + 1L,
            )
            val newList = list.toMutableList()
            newList[matchIndex] = updated
            _chatHistory.value = newList
            _metaState.value = _metaState.value.copy(
                isLoading = false,
                errorMessage = null,
                errorKind = ErrorKind.NONE,
                isBubbleVisible = true,
            )
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
            contentRevision = 0L,
            regenerateAttemptCount = 0,
        )
        _chatHistory.value = (listOf(newItem) + list).take(20)
        _metaState.value = _metaState.value.copy(
            unreadCount = _metaState.value.unreadCount + unreadBump,
            isLoading = false,
            errorMessage = null,
            errorKind = ErrorKind.NONE,
            isBubbleVisible = true,
        )
    }

    /** Strict identity for matching an overlay row to a [ChatMessage] (regenerate / in-place updates). */
    fun sameConversation(a: ChatMessage, b: ChatMessage): Boolean =
        a.sender == b.sender &&
            a.message == b.message &&
            a.appSource == b.appSource &&
            a.isSummaryNotification == b.isSummaryNotification &&
            a.replyActionKey == b.replyActionKey &&
            a.messageType == b.messageType &&
            a.direction == b.direction

    fun setMode(mode: OverlayMode) {
        _metaState.value = _metaState.value.copy(
            mode = mode,
            unreadCount = if (mode == OverlayMode.PANEL) 0 else _metaState.value.unreadCount,
            peekBubbleOverOwnAppUntilMs = if (mode == OverlayMode.PANEL) {
                0L
            } else {
                _metaState.value.peekBubbleOverOwnAppUntilMs
            },
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
            peekBubbleOverOwnAppUntilMs = 0L,
        )
    }

    fun setServiceRunning(running: Boolean) {
        _metaState.value = _metaState.value.copy(
            isServiceRunning = running,
            isBubbleVisible = if (!running) false else _metaState.value.isBubbleVisible,
            peekBubbleOverOwnAppUntilMs = if (!running) 0L else _metaState.value.peekBubbleOverOwnAppUntilMs,
        )
    }

    /**
     * Call when the user enables the assistant from the in-app dashboard so the chat head appears
     * immediately (briefly over the app) without waiting for a notification.
     */
    fun onAssistantEnabledFromApp() {
        val now = System.currentTimeMillis()
        _metaState.value = _metaState.value.copy(
            isBubbleVisible = true,
            peekBubbleOverOwnAppUntilMs = now + peekBubbleOverOwnAppDurationMs,
        )
    }

    /**
     * Clears expired peek and hides the bubble if there is nothing else to show.
     * Safe to call often (e.g. before each overlay layout pass).
     */
    fun refreshPeekExpiredIfNeeded() {
        val m = _metaState.value
        if (m.peekBubbleOverOwnAppUntilMs <= 0L) return
        if (System.currentTimeMillis() < m.peekBubbleOverOwnAppUntilMs) return
        val stillNeedBubble =
            m.isLoading || m.errorMessage != null || _chatHistory.value.isNotEmpty()
        _metaState.value = m.copy(
            peekBubbleOverOwnAppUntilMs = 0L,
            isBubbleVisible = stillNeedBubble,
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

        val list = _chatHistory.value
        val idx = list.indexOfFirst { sameConversation(it.chatMessage, message) }
        if (idx >= 0) {
            val item = list[idx]
            if (item.regenerateAttemptCount >= 3) return false
            val next = list.toMutableList()
            next[idx] = item.copy(regenerateAttemptCount = item.regenerateAttemptCount + 1)
            _chatHistory.value = next
        }

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
    private const val regenerateCooldownMs = 1_500L
    private const val peekBubbleOverOwnAppDurationMs = 12_000L
}
