package com.example.ai_assis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ai_assis.R
import com.example.ai_assis.data.local.ConversationCacheDataSource
import com.example.ai_assis.data.local.SenderStyleMemoryDataSource
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.usecase.BuildConversationContextUseCase
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.presentation.ui.overlay.BubbleContent
import com.example.ai_assis.presentation.ui.overlay.OverlayUiState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

@AndroidEntryPoint
class OverlayService : android.app.Service() {

    @Inject
    lateinit var smartSuggestionRepository: SmartSuggestionRepository

    @Inject
    lateinit var conversationCacheDataSource: ConversationCacheDataSource

    @Inject
    lateinit var senderStyleMemoryDataSource: SenderStyleMemoryDataSource

    @Inject
    lateinit var buildConversationContextUseCase: BuildConversationContextUseCase

    @Inject
    lateinit var getHybridSuggestionsUseCase: GetHybridSuggestionsUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var overlayOwner: OverlayViewTreeOwner
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var bubbleView: ComposeView? = null
    private var scrimView: View? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)
        overlayOwner = OverlayViewTreeOwner().apply {
            performCreate()
            moveToStarted()
        }
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(overlayNotificationId, buildForegroundNotification())
        NotificationEventBus.setServiceRunning(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createOverlayIfPermitted()
        }
        observeMessages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        clearScrim()
        bubbleView = null
        NotificationEventBus.setServiceRunning(false)
        overlayOwner.performDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun observeMessages() {
        serviceScope.launch {
            NotificationEventBus.events.collectLatest { event ->
                Log.d(logTag, "AI pipeline started for ${event.appSource}")
                NotificationEventBus.setLoading(true)
                runCatching {
                    conversationCacheDataSource.appendMessage(event)
                    val tone = smartSuggestionRepository.observeTone().first()
                    senderStyleMemoryDataSource.rememberStyle(event.appSource, event.sender, tone)
                    val recentMessages = conversationCacheDataSource.recentMessages(event.appSource, event.sender)
                    val styleHint = senderStyleMemoryDataSource.getStyleHint(event.appSource, event.sender)
                    val context = buildConversationContextUseCase(
                        message = event,
                        tone = tone,
                        recentMessages = recentMessages,
                        styleHint = styleHint,
                        highQualityMode = true,
                    )
                    getHybridSuggestionsUseCase(context).getOrThrow()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                }.onSuccess { hybridResult ->
                    val replies = hybridResult.suggestions.map { it.text }
                    Log.d(
                        logTag,
                        "Suggestion source=${hybridResult.source} count=${replies.size} fallback=${hybridResult.fallbackReason}",
                    )
                    if (replies.isNotEmpty()) {
                        NotificationEventBus.clearFailure()
                        NotificationEventBus.addSuggestion(
                            message = event,
                            replies = replies,
                            source = hybridResult.source,
                            fallbackReason = hybridResult.fallbackReason,
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            renderOverlay()
                        }
                    } else {
                        NotificationEventBus.setLoading(false)
                    }
                }.onFailure { throwable ->
                    if (throwable is CancellationException) {
                        Log.d(logTag, "AI pipeline cancelled for ${event.appSource}; likely superseded by a newer event.")
                        return@onFailure
                    }
                    NotificationEventBus.recordFailure(event)
                    val errorMsg = userFriendlyError(throwable.message)
                    NotificationEventBus.setError(errorMsg)
                    Log.e(logTag, "AI call failed for ${event.appSource}: $errorMsg", throwable)
                }
            }
        }
    }

    private fun createOverlayIfPermitted() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        if (bubbleView != null) return

        val metrics = resources.displayMetrics
        val marginPx = 16.dpToPx()
        val defaultX = (metrics.widthPixels - 84.dpToPx()).coerceAtLeast(marginPx)
        val defaultY = (metrics.heightPixels - 180.dpToPx()).coerceAtLeast(marginPx)

        overlayParams = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("overlay_x", defaultX)
            y = prefs.getInt("overlay_y", defaultY)
        }

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setContent {
                val meta by NotificationEventBus.metaState.collectAsState()
                val items by NotificationEventBus.chatHistory.collectAsState()
                BubbleContent(
                    uiState = OverlayUiState(
                        mode = meta.mode,
                        unreadCount = meta.unreadCount,
                        updatesPaused = meta.updatesPaused,
                        isLoading = meta.isLoading,
                        errorMessage = meta.errorMessage,
                        isBubbleVisible = meta.isBubbleVisible,
                        items = items,
                    ),
                    onHeadClick = {
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.PANEL)
                        renderOverlay()
                    },
                    onCollapse = {
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
                        clearScrim()
                        renderOverlay()
                    },
                    onClear = {
                        NotificationEventBus.clearHistory()
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
                        clearScrim()
                        renderOverlay()
                    },
                    onToggleUpdates = { NotificationEventBus.togglePaused() },
                    onReplyClick = ::copyToClipboard,
                    onDirectSend = ::sendDirectReply,
                    onRetry = { NotificationEventBus.retryLastFailedRequest() },
                )
            }
        }

        bubbleView?.let { view ->
            makeDraggable(view, overlayParams)
            windowManager.addView(view, overlayParams)
        }
    }

    private fun renderOverlay() {
        val view = bubbleView ?: return
        val meta = NotificationEventBus.metaState.value

        if (!meta.isBubbleVisible) {
            clearScrim()
            overlayParams.width = 1
            overlayParams.height = 1
            overlayParams.flags = overlayParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.updateViewLayout(view, overlayParams)
            return
        }

        overlayParams.flags = overlayParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        overlayParams.width = if (meta.mode == NotificationEventBus.OverlayMode.HEAD) {
            WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            (resources.displayMetrics.widthPixels * 0.88f).roundToInt()
        }
        overlayParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(view, overlayParams)
        updateScrimForMode(meta.mode)
        view.invalidate()
    }

    private fun updateScrimForMode(mode: NotificationEventBus.OverlayMode) {
        if (mode == NotificationEventBus.OverlayMode.PANEL) {
            if (scrimView != null) return
            scrimView = View(this).apply {
                setBackgroundColor(0x66000000)
                setOnClickListener {
                    NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
                    renderOverlay()
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT,
            )
            windowManager.addView(scrimView, params)
            bubbleView?.let { windowManager.removeView(it) }
            bubbleView?.let { windowManager.addView(it, overlayParams) }
        } else {
            scrimView?.let { runCatching { windowManager.removeView(it) } }
            scrimView = null
        }
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchDownRawX = 0f
        var touchDownRawY = 0f
        var hasMoved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val panelDragHandleHeightPx = 56.dpToPx().toFloat()
        val panelDragHandleWidthPx = 140.dpToPx().toFloat()

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val mode = NotificationEventBus.metaState.value.mode
                    val shouldStartDrag = mode == NotificationEventBus.OverlayMode.HEAD ||
                            (mode == NotificationEventBus.OverlayMode.PANEL &&
                                    event.y <= panelDragHandleHeightPx &&
                                    event.x <= panelDragHandleWidthPx)
                    if (!shouldStartDrag) return@setOnTouchListener false
                    initialX = params.x
                    initialY = params.y
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    hasMoved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchDownRawX).roundToInt()
                    val deltaY = (event.rawY - touchDownRawY).roundToInt()
                    if (!hasMoved && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        hasMoved = true
                    }
                    if (!hasMoved) return@setOnTouchListener true
                    params.x = (initialX + deltaX).coerceAtLeast(0)
                    params.y = (initialY + deltaY).coerceAtLeast(0)
                    windowManager.updateViewLayout(view, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (hasMoved) {
                        snapToNearestEdge(params, view)
                    } else if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.PANEL)
                        renderOverlay()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams, view: View) {
        val metrics = resources.displayMetrics
        val viewWidth = if (view.width > 0) view.width else 84.dpToPx()
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        params.x = if (params.x < maxX / 2) 0 else maxX
        val maxY = (metrics.heightPixels - 64.dpToPx()).coerceAtLeast(0)
        params.y = params.y.coerceIn(0, maxY)
        windowManager.updateViewLayout(view, params)
        prefs.edit().putInt("overlay_x", params.x).putInt("overlay_y", params.y).apply()
    }

    private fun copyToClipboard(text: String, feedbackMessage: String = "Copied to clipboard") {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("reply", text))
        Toast.makeText(this, feedbackMessage, Toast.LENGTH_SHORT).show()
        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
        clearScrim()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) renderOverlay()
    }

    private fun sendDirectReply(text: String, actionKey: String) {
        val action = DirectReplyRegistry.get(actionKey)
        if (action == null) {
            copyToClipboard(text, "Direct send unavailable. Copied to clipboard.")
            return
        }
        val remoteInput = action.remoteInputs?.firstOrNull()
        if (remoteInput == null) {
            copyToClipboard(text, "Direct send unavailable. Copied to clipboard.")
            return
        }
        try {
            val intent = Intent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                RemoteInput.addResultsToIntent(
                    arrayOf(remoteInput),
                    intent,
                    Bundle().apply { putCharSequence(remoteInput.resultKey, text) },
                )
            }
            action.actionIntent.send(this, 0, intent)
            Toast.makeText(this, "Reply sent!", Toast.LENGTH_SHORT).show()
            DirectReplyRegistry.remove(actionKey)
            NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
            clearScrim()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) renderOverlay()
        } catch (e: PendingIntent.CanceledException) {
            Log.e(logTag, "Direct reply failed, falling back to clipboard", e)
            copyToClipboard(text, "Send failed. Copied to clipboard.")
        }
    }

    private fun buildForegroundNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                overlayChannelId,
                "AI Assistant Service",
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, overlayChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Smart Chat Assistant")
            .setContentText("Listening for new messages")
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val logTag = "SmartAssistant"
        const val overlayChannelId = "overlay_service_channel"
        const val overlayNotificationId = 101
    }

    private fun userFriendlyError(rawMessage: String?): String {
        val message = rawMessage.orEmpty().lowercase()
        return when {
            message.contains("quota") || message.contains("rate") || message.contains("429") ->
                "Cloud provider is rate-limited right now. Retrying will use fallback logic automatically."
            message.contains("cloud_error_both_providers_cooldown") ->
                "Cloud providers are cooling down. On-device suggestions remain available."
            message.contains("cloud_error_both_providers") ->
                "Cloud providers are unavailable. On-device suggestions remain available."
            else -> rawMessage ?: "Unable to generate suggestions right now."
        }
    }

    private fun clearScrim() {
        scrimView?.let { runCatching { windowManager.removeView(it) } }
        scrimView = null
    }
}

private fun Int.dpToPx(): Int =
    (this * android.content.res.Resources.getSystem().displayMetrics.density).roundToInt()

private class OverlayViewTreeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val vmStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = vmStore

    fun performCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun moveToStarted() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun performDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        vmStore.clear()
    }
}
