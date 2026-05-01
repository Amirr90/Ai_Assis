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
import com.example.ai_assis.domain.model.MessageDirection
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.usecase.BuildConversationContextUseCase
import com.example.ai_assis.domain.usecase.BuildContextMemoryUseCase
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.domain.usecase.ResolveReplyPolicyUseCase
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.presentation.ui.overlay.BubbleContent
import com.example.ai_assis.presentation.ui.overlay.OverlayUiState
import com.example.ai_assis.ui.theme.AI_AssisTheme
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

    @Inject
    lateinit var featurePreferencesRepository: FeaturePreferencesRepository

    @Inject
    lateinit var resolveReplyPolicyUseCase: ResolveReplyPolicyUseCase

    @Inject
    lateinit var buildContextMemoryUseCase: BuildContextMemoryUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var overlayOwner: OverlayViewTreeOwner
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var bubbleView: ComposeView? = null
    private var scrimView: View? = null

    // Position of the draggable chat-head bubble in HEAD mode. Kept separate from
    // overlayParams.x/y because the expanded PANEL is anchored independently
    // (centered, top-aligned) so its content does not overflow off-screen and
    // accidental drags during list-scroll cannot push it out of the touchable area.
    private var bubbleX: Int = 0
    private var bubbleY: Int = 0

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
        observeMainAppForegroundForOverlayLayout()
    }

    private fun observeMainAppForegroundForOverlayLayout() {
        serviceScope.launch {
            MainAppForegroundTracker.mainAppForeground.collect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bubbleView != null) {
                    renderOverlay()
                }
            }
        }
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
                if (event.direction != MessageDirection.INCOMING) {
                    Log.d(logTag, "Skipping suggestion fetch for non-incoming message direction=${event.direction}")
                    return@collectLatest
                }
                val requestId = "${event.appSource}:${event.sender}:${System.currentTimeMillis()}"
                val requestStartMs = System.currentTimeMillis()
                Log.d(
                    logTag,
                    "Suggestion fetch start requestId=$requestId app=${event.appSource} sender=${event.sender}",
                )
                NotificationEventBus.setLoading(true)
                runCatching {
                    Log.d(logTag, "Suggestion fetch stage=request_context requestId=$requestId")
                    conversationCacheDataSource.appendMessage(event)
                    val tone = smartSuggestionRepository.observeTone().first()
                    val aiEnabled = featurePreferencesRepository.aiEnabledFlow.first()
                    val globalLength = featurePreferencesRepository.replyLengthFlow.first()
                    val effectiveTone = resolveReplyPolicyUseCase.resolveTone(event.appSource, tone.toReplyTone())
                    val effectiveLength = resolveReplyPolicyUseCase.resolveLength(event.appSource, globalLength)
                    senderStyleMemoryDataSource.rememberStyle(event.appSource, event.sender, tone)
                    val recentMessages = conversationCacheDataSource.recentMessages(event.appSource, event.sender)
                    val contextMemory = buildContextMemoryUseCase(event, recentMessages)
                    val styleHint = senderStyleMemoryDataSource.getStyleHint(event.appSource, event.sender)
                    Log.d(
                        logTag,
                        "Suggestion fetch stage=context_ready requestId=$requestId tone=$tone recentCount=${recentMessages.size} hasStyleHint=${!styleHint.isNullOrBlank()}",
                    )
                    val context = buildConversationContextUseCase(
                        message = event,
                        tone = effectiveTone.toSuggestionTone(),
                        recentMessages = contextMemory,
                        styleHint = styleHint,
                        highQualityMode = true,
                        replyLength = effectiveLength,
                        aiEnabled = aiEnabled,
                    )
                    Log.d(logTag, "Suggestion fetch stage=hybrid_invoke requestId=$requestId")
                    getHybridSuggestionsUseCase(context).getOrThrow()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                }.onSuccess { hybridResult ->
                    val replies = hybridResult.suggestions.map { it.text }
                    val elapsedMs = System.currentTimeMillis() - requestStartMs
                    Log.d(
                        logTag,
                        "Suggestion fetch success requestId=$requestId source=${hybridResult.source} count=${replies.size} fallback=${hybridResult.fallbackReason} durationMs=$elapsedMs",
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
                        Log.d(logTag, "Suggestion fetch empty requestId=$requestId durationMs=$elapsedMs")
                        NotificationEventBus.setLoading(false)
                    }
                }.onFailure { throwable ->
                    val elapsedMs = System.currentTimeMillis() - requestStartMs
                    if (throwable is CancellationException) {
                        Log.d(
                            logTag,
                            "Suggestion fetch cancelled requestId=$requestId app=${event.appSource} durationMs=$elapsedMs (superseded by newer event)",
                        )
                        return@onFailure
                    }
                    NotificationEventBus.recordFailure(event)
                    val errorMsg = userFriendlyError(throwable.message)
                    NotificationEventBus.setError(errorMsg)
                    Log.e(
                        logTag,
                        "Suggestion fetch failed requestId=$requestId app=${event.appSource} durationMs=$elapsedMs error=$errorMsg",
                        throwable,
                    )
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

        bubbleX = prefs.getInt("overlay_x", defaultX)
        bubbleY = prefs.getInt("overlay_y", defaultY)

        overlayParams = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bubbleX
            y = bubbleY
        }

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setContent {
                AI_AssisTheme {
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
                        onRegenerateSuggestion = { message -> NotificationEventBus.regenerateForMessage(message) },
                        onRetry = { NotificationEventBus.retryLastFailedRequest() },
                    )
                }
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
        val hideHeadOverOwnApp =
            meta.mode == NotificationEventBus.OverlayMode.HEAD && MainAppForegroundTracker.isMainAppInForeground()
        val showBubble = meta.isBubbleVisible && !hideHeadOverOwnApp

        if (!showBubble) {
            clearScrim()
            overlayParams.width = 1
            overlayParams.height = 1
            overlayParams.flags = overlayParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            windowManager.updateViewLayout(view, overlayParams)
            return
        }

        val baseFlags = overlayParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        overlayParams.flags = if (meta.mode == NotificationEventBus.OverlayMode.PANEL) {
            // Allow input focus in panel mode so editable reply fields can open IME.
            baseFlags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            baseFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val metrics = resources.displayMetrics
        when (meta.mode) {
            NotificationEventBus.OverlayMode.HEAD -> {
                overlayParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                overlayParams.x = bubbleX
                overlayParams.y = bubbleY
            }
            NotificationEventBus.OverlayMode.PANEL -> {
                val panelWidth = (metrics.widthPixels * 0.88f).roundToInt()
                overlayParams.width = panelWidth
                // Center horizontally and anchor near the top so the LazyColumn
                // remains fully on-screen and every card's Edit/Send is touchable.
                overlayParams.x = ((metrics.widthPixels - panelWidth) / 2).coerceAtLeast(0)
                overlayParams.y = panelTopInsetPx()
            }
        }
        overlayParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(view, overlayParams)
        updateScrimForMode(meta.mode)
        view.invalidate()
    }

    private fun panelTopInsetPx(): Int {
        val statusBarHeight = runCatching {
            val resId = resources.getIdentifier("status_bar_height", "dimen", "android")
            if (resId > 0) resources.getDimensionPixelSize(resId) else 0
        }.getOrDefault(0)
        return statusBarHeight + 8.dpToPx()
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
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
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
        var isTrackingDrag = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Dragging is only allowed when the chat head is collapsed.
                    // In PANEL mode the panel is a fixed centered modal: any
                    // touch is forwarded to children so the LazyColumn and its
                    // CTAs receive their own gesture events.
                    val mode = NotificationEventBus.metaState.value.mode
                    if (mode != NotificationEventBus.OverlayMode.HEAD) {
                        isTrackingDrag = false
                        return@setOnTouchListener false
                    }
                    isTrackingDrag = true
                    initialX = bubbleX
                    initialY = bubbleY
                    touchDownRawX = event.rawX
                    touchDownRawY = event.rawY
                    hasMoved = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isTrackingDrag) return@setOnTouchListener false
                    val deltaX = (event.rawX - touchDownRawX).roundToInt()
                    val deltaY = (event.rawY - touchDownRawY).roundToInt()
                    if (!hasMoved && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        hasMoved = true
                    }
                    if (!hasMoved) return@setOnTouchListener true
                    bubbleX = (initialX + deltaX).coerceAtLeast(0)
                    bubbleY = (initialY + deltaY).coerceAtLeast(0)
                    if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
                        params.x = bubbleX
                        params.y = bubbleY
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isTrackingDrag) return@setOnTouchListener false
                    isTrackingDrag = false
                    if (hasMoved) {
                        snapToNearestEdge(params, view)
                    } else if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.PANEL)
                        renderOverlay()
                    }
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    isTrackingDrag = false
                    false
                }

                else -> false
            }
        }
    }

    private fun snapToNearestEdge(params: WindowManager.LayoutParams, view: View) {
        val metrics = resources.displayMetrics
        val viewWidth = if (view.width > 0) view.width else 84.dpToPx()
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        bubbleX = if (bubbleX < maxX / 2) 0 else maxX
        val maxY = (metrics.heightPixels - 64.dpToPx()).coerceAtLeast(0)
        bubbleY = bubbleY.coerceIn(0, maxY)
        if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
            params.x = bubbleX
            params.y = bubbleY
            windowManager.updateViewLayout(view, params)
        }
        prefs.edit().putInt("overlay_x", bubbleX).putInt("overlay_y", bubbleY).apply()
    }

    private fun copyToClipboard(text: String, feedbackMessage: String = getString(R.string.dashboard_copied_to_clipboard)) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("reply", text))
        Toast.makeText(this, feedbackMessage, Toast.LENGTH_SHORT).show()
    }

    private fun sendDirectReply(text: String, chatMessage: ChatMessage) {
        val action = DirectReplyRegistry.resolveForChat(
            key = chatMessage.replyActionKey,
            packageName = chatMessage.appSource,
            sender = chatMessage.sender,
        )
        if (action == null) {
            copyToClipboard(text, getString(R.string.dashboard_direct_send_unavailable))
            return
        }
        val remoteInput = action.remoteInputs?.firstOrNull { it.allowFreeFormInput }
            ?: action.remoteInputs?.firstOrNull()
        if (remoteInput == null) {
            copyToClipboard(text, getString(R.string.dashboard_direct_send_unavailable))
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
            OutgoingMessageSuppressor.registerOutgoing(
                packageName = chatMessage.appSource,
                text = text,
            )
            Toast.makeText(this, getString(R.string.dashboard_reply_sent), Toast.LENGTH_SHORT).show()
        } catch (e: PendingIntent.CanceledException) {
            Log.e(logTag, "Direct reply failed, falling back to clipboard", e)
            copyToClipboard(text, getString(R.string.dashboard_send_failed_copied))
        }
    }

    private fun buildForegroundNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                overlayChannelId,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, overlayChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_listener_description))
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
                getString(R.string.dashboard_user_safe_error_rate_limited)
            message.contains("cloud_error_both_providers_cooldown") ->
                getString(R.string.dashboard_user_safe_error_cooldown)
            message.contains("cloud_error_both_providers") ->
                getString(R.string.dashboard_user_safe_error_cloud_unavailable)
            else -> getString(R.string.dashboard_user_safe_error_default)
        }
    }

    private fun clearScrim() {
        scrimView?.let { runCatching { windowManager.removeView(it) } }
        scrimView = null
    }
}

private fun com.example.ai_assis.domain.model.ReplyTone.toSuggestionTone(): com.example.ai_assis.domain.model.SuggestionTone {
    return when (this) {
        com.example.ai_assis.domain.model.ReplyTone.CASUAL -> com.example.ai_assis.domain.model.SuggestionTone.CASUAL
        com.example.ai_assis.domain.model.ReplyTone.PROFESSIONAL -> com.example.ai_assis.domain.model.SuggestionTone.PROFESSIONAL
        com.example.ai_assis.domain.model.ReplyTone.FLIRTY -> com.example.ai_assis.domain.model.SuggestionTone.CASUAL
        com.example.ai_assis.domain.model.ReplyTone.ANGRY -> com.example.ai_assis.domain.model.SuggestionTone.PROFESSIONAL
        com.example.ai_assis.domain.model.ReplyTone.FUNNY -> com.example.ai_assis.domain.model.SuggestionTone.HUMOROUS
        com.example.ai_assis.domain.model.ReplyTone.SHORT -> com.example.ai_assis.domain.model.SuggestionTone.SHORT
    }
}

private fun com.example.ai_assis.domain.model.SuggestionTone.toReplyTone(): com.example.ai_assis.domain.model.ReplyTone {
    return when (this) {
        com.example.ai_assis.domain.model.SuggestionTone.CASUAL -> com.example.ai_assis.domain.model.ReplyTone.CASUAL
        com.example.ai_assis.domain.model.SuggestionTone.PROFESSIONAL -> com.example.ai_assis.domain.model.ReplyTone.PROFESSIONAL
        com.example.ai_assis.domain.model.SuggestionTone.HUMOROUS -> com.example.ai_assis.domain.model.ReplyTone.FUNNY
        com.example.ai_assis.domain.model.SuggestionTone.SHORT -> com.example.ai_assis.domain.model.ReplyTone.SHORT
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
