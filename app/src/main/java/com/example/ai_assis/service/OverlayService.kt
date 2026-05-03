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
import android.widget.FrameLayout
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
import com.example.ai_assis.MainActivity
import com.example.ai_assis.R
import com.example.ai_assis.data.local.ConversationCacheDataSource
import com.example.ai_assis.domain.DailyAiLimitReachedException
import com.example.ai_assis.data.local.SenderStyleMemoryDataSource
import com.example.ai_assis.domain.model.MessageDirection
import com.example.ai_assis.domain.repository.FeaturePreferencesRepository
import com.example.ai_assis.domain.repository.SmartSuggestionRepository
import com.example.ai_assis.domain.usecase.BuildConversationContextUseCase
import com.example.ai_assis.domain.usecase.BuildContextMemoryUseCase
import com.example.ai_assis.domain.usecase.GetHybridSuggestionsUseCase
import com.example.ai_assis.domain.usecase.ResolveReplyPolicyUseCase
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.presentation.ui.overlay.BubbleHeadOverlayContent
import com.example.ai_assis.presentation.ui.overlay.BubblePanelOverlayContent
import com.example.ai_assis.presentation.ui.overlay.OverlayUiState
import com.example.ai_assis.ui.theme.AI_AssisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject
import androidx.core.content.edit

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
    /** Small draggable window for the chat head only; never [MATCH_PARENT]. */
    private lateinit var headParams: WindowManager.LayoutParams
    /** Full-screen panel window; added lazily on first [NotificationEventBus.OverlayMode.PANEL]. */
    private var panelParams: WindowManager.LayoutParams? = null
    /** Root added to [WindowManager]; wraps head [ComposeView] for HEAD touch interception. */
    private var headBubbleView: View? = null
    private var panelOverlayRoot: FrameLayout? = null
    private var peekExpiryJob: Job? = null
    /** Runs before Compose touch dispatch so HEAD drag/tap work ([HeadInterceptFrameLayout]). */
    private var bubbleDragListener: View.OnTouchListener? = null

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
        observeMetaStateForLayout()
    }

    private fun observeMetaStateForLayout() {
        serviceScope.launch {
            NotificationEventBus.metaState.collect {
                NotificationEventBus.refreshPeekExpiredIfNeeded()
                renderOverlay()
                schedulePeekExpiryIfNeeded()
            }
        }
    }

    private fun schedulePeekExpiryIfNeeded() {
        peekExpiryJob?.cancel()
        val until = NotificationEventBus.metaState.value.peekBubbleOverOwnAppUntilMs
        val delayMs = until - System.currentTimeMillis()
        if (delayMs <= 0L) return
        peekExpiryJob = serviceScope.launch {
            delay(delayMs)
            NotificationEventBus.refreshPeekExpiredIfNeeded()
            renderOverlay()
        }
    }

    private fun observeMainAppForegroundForOverlayLayout() {
        serviceScope.launch {
            MainAppForegroundTracker.mainAppForeground.collect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && headBubbleView != null) {
                    renderOverlay()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        headBubbleView?.let { runCatching { windowManager.removeView(it) } }
        panelOverlayRoot?.let { runCatching { windowManager.removeView(it) } }
        panelOverlayRoot = null
        headBubbleView = null
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
                    val errorMsg = userFriendlyError(throwable)
                    val errorKind = if (throwable is DailyAiLimitReachedException) {
                        NotificationEventBus.ErrorKind.DAILY_AI_LIMIT
                    } else {
                        NotificationEventBus.ErrorKind.GENERIC
                    }
                    NotificationEventBus.setError(errorMsg, errorKind)
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
        if (headBubbleView != null) return

        val metrics = resources.displayMetrics
        val marginPx = 16.dpToPx()
        val defaultX = (metrics.widthPixels - 84.dpToPx()).coerceAtLeast(marginPx)
        val defaultY = (metrics.heightPixels - 180.dpToPx()).coerceAtLeast(marginPx)

        bubbleX = prefs.getInt("overlay_x", defaultX)
        bubbleY = prefs.getInt("overlay_y", defaultY)

        headParams = WindowManager.LayoutParams(
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

        val composeHost = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setContent {
                AI_AssisTheme {
                    val meta by NotificationEventBus.metaState.collectAsState()
                    if (meta.mode == NotificationEventBus.OverlayMode.HEAD) {
                        BubbleHeadOverlayContent(
                            unreadCount = meta.unreadCount,
                            isLoading = meta.isLoading,
                            isBubbleVisible = meta.isBubbleVisible,
                            onHeadClick = {
                                NotificationEventBus.setMode(NotificationEventBus.OverlayMode.PANEL)
                                headBubbleView?.post { renderOverlay() } ?: renderOverlay()
                            },
                        )
                    }
                }
            }
        }
        headBubbleView = HeadInterceptFrameLayout(this, composeHost).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            addView(
                composeHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        headBubbleView?.let { view ->
            makeDraggable(view, headParams)
            windowManager.addView(view, headParams)
        }
        NotificationEventBus.setBubbleScreenPosition(bubbleX, bubbleY)
    }

    /** Panel layer is above the head in z-order ([WindowManager.addView] order). Dimming stays in Compose. */
    private fun ensurePanelOverlayCreated() {
        if (panelOverlayRoot != null) return

        val composeHost = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setContent {
                AI_AssisTheme {
                    val meta by NotificationEventBus.metaState.collectAsState()
                    val items by NotificationEventBus.chatHistory.collectAsState()
                    BubblePanelOverlayContent(
                        uiState = OverlayUiState(
                            mode = meta.mode,
                            unreadCount = meta.unreadCount,
                            updatesPaused = meta.updatesPaused,
                            isLoading = meta.isLoading,
                            errorMessage = meta.errorMessage,
                            errorKind = meta.errorKind,
                            isBubbleVisible = meta.isBubbleVisible,
                            bubbleAnchorXPx = meta.bubbleAnchorXPx,
                            bubbleAnchorYPx = meta.bubbleAnchorYPx,
                            items = items,
                        ),
                        onCollapse = {
                            NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
                            renderOverlay()
                        },
                        onClear = {
                            NotificationEventBus.clearHistory()
                            NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
                            renderOverlay()
                        },
                        onToggleUpdates = { NotificationEventBus.togglePaused() },
                        onReplyClick = ::copyToClipboard,
                        onDirectSend = ::sendDirectReply,
                        onRegenerateSuggestion = { message -> NotificationEventBus.regenerateForMessage(message) },
                        onRetry = { NotificationEventBus.retryLastFailedRequest() },
                        onOpenProUpgrade = ::openMainActivityForProUpgrade,
                    )
                }
            }
        }
        panelOverlayRoot = FrameLayout(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            addView(
                composeHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            visibility = View.GONE
        }
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        panelOverlayRoot?.let { root ->
            windowManager.addView(root, panelParams!!)
        }
    }

    private fun renderOverlay() {
        val headView = headBubbleView ?: return
        val meta = NotificationEventBus.metaState.value
        val peekActive = meta.peekBubbleOverOwnAppUntilMs > System.currentTimeMillis()
        val hideHeadOverOwnApp =
            meta.mode == NotificationEventBus.OverlayMode.HEAD &&
                MainAppForegroundTracker.isMainAppInForeground() &&
                !peekActive
        val showBubble = meta.isBubbleVisible && !hideHeadOverOwnApp

        if (!showBubble) {
            headParams.width = 1
            headParams.height = 1
            headParams.flags = headParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            headView.visibility = View.VISIBLE
            windowManager.updateViewLayout(headView, headParams)
            panelOverlayRoot?.apply {
                visibility = View.GONE
                val p = panelParams ?: return@apply
                p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                windowManager.updateViewLayout(this, p)
            }
            return
        }

        when (meta.mode) {
            NotificationEventBus.OverlayMode.HEAD -> {
                panelOverlayRoot?.apply {
                    visibility = View.GONE
                    val p = panelParams ?: return@apply
                    p.flags = p.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    windowManager.updateViewLayout(this, p)
                }
                headView.visibility = View.VISIBLE
                val baseHead = headParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                headParams.flags = baseHead or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                headParams.width = WindowManager.LayoutParams.WRAP_CONTENT
                headParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                headParams.x = bubbleX
                headParams.y = bubbleY
                windowManager.updateViewLayout(headView, headParams)
                syncHeadComposeLayoutParams()
            }
            NotificationEventBus.OverlayMode.PANEL -> {
                ensurePanelOverlayCreated()
                val panel = panelOverlayRoot ?: return
                val p = panelParams ?: return
                headView.visibility = View.GONE
                val basePanel = p.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                p.flags = basePanel and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                panel.visibility = View.VISIBLE
                windowManager.updateViewLayout(panel, p)
            }
        }
        NotificationEventBus.setBubbleScreenPosition(bubbleX, bubbleY)
        headView.invalidate()
        panelOverlayRoot?.invalidate()
    }

    private fun syncHeadComposeLayoutParams() {
        val root = headBubbleView as? HeadInterceptFrameLayout ?: return
        val lp = root.composeHost.layoutParams as FrameLayout.LayoutParams
        lp.width = FrameLayout.LayoutParams.WRAP_CONTENT
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        root.composeHost.layoutParams = lp
    }

    private fun makeDraggable(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchDownRawX = 0f
        var touchDownRawY = 0f
        var hasMoved = false
        var isTrackingDrag = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        bubbleDragListener = View.OnTouchListener drag@{ _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Dragging is only allowed when the chat head is collapsed.
                    // In PANEL mode the panel is a fixed centered modal: any
                    // touch is forwarded to children so the scrollable list and its
                    // CTAs receive their own gesture events.
                    val mode = NotificationEventBus.metaState.value.mode
                    Log.d(
                        logTag,
                        "overlay_touch_down x=${event.x} y=${event.y} raw=(${event.rawX},${event.rawY}) mode=$mode",
                    )
                    if (mode != NotificationEventBus.OverlayMode.HEAD) {
                        isTrackingDrag = false
                        return@drag false
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
                    if (!isTrackingDrag) return@drag false
                    val deltaX = (event.rawX - touchDownRawX).roundToInt()
                    val deltaY = (event.rawY - touchDownRawY).roundToInt()
                    if (!hasMoved && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        hasMoved = true
                    }
                    if (!hasMoved) return@drag true
                    val metrics = resources.displayMetrics
                    val vw = if (view.width > 0) view.width else 64.dpToPx()
                    val vh = if (view.height > 0) view.height else 64.dpToPx()
                    val maxX = (metrics.widthPixels - vw).coerceAtLeast(0)
                    val maxY = (metrics.heightPixels - vh).coerceAtLeast(0)
                    bubbleX = (initialX + deltaX).coerceIn(0, maxX)
                    bubbleY = (initialY + deltaY).coerceIn(0, maxY)
                    if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
                        params.x = bubbleX
                        params.y = bubbleY
                        windowManager.updateViewLayout(view, params)
                        NotificationEventBus.setBubbleScreenPosition(bubbleX, bubbleY)
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isTrackingDrag) return@drag false
                    isTrackingDrag = false
                    if (hasMoved) {
                        clampBubbleToScreenAndPersist(params, view)
                    } else if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.PANEL)
                        headBubbleView?.post { renderOverlay() } ?: renderOverlay()
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

    private fun clampBubbleToScreenAndPersist(params: WindowManager.LayoutParams, view: View) {
        val metrics = resources.displayMetrics
        val viewWidth = if (view.width > 0) view.width else 64.dpToPx()
        val viewHeight = if (view.height > 0) view.height else 64.dpToPx()
        val maxX = (metrics.widthPixels - viewWidth).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - viewHeight).coerceAtLeast(0)
        bubbleX = bubbleX.coerceIn(0, maxX)
        bubbleY = bubbleY.coerceIn(0, maxY)
        if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
            params.x = bubbleX
            params.y = bubbleY
            windowManager.updateViewLayout(view, params)
        }
        NotificationEventBus.setBubbleScreenPosition(bubbleX, bubbleY)
        prefs.edit { putInt("overlay_x", bubbleX).putInt("overlay_y", bubbleY) }
    }

    private fun copyToClipboard(text: String, feedbackMessage: String = getString(R.string.dashboard_copied_to_clipboard)) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("reply", text))
        Toast.makeText(this, feedbackMessage, Toast.LENGTH_SHORT).show()
    }

    private fun sendDirectReply(text: String, chatMessage: ChatMessage) {
        fun copyPlainQuiet() {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("reply", text))
        }

        val action = DirectReplyRegistry.resolveForChat(
            key = chatMessage.replyActionKey,
            packageName = chatMessage.appSource,
            sender = chatMessage.sender,
        )
        if (action == null) {
            copyPlainQuiet()
            Toast.makeText(this, getString(R.string.dashboard_reply_not_sent), Toast.LENGTH_SHORT).show()
            return
        }
        val remoteInput = action.remoteInputs?.firstOrNull { it.allowFreeFormInput }
            ?: action.remoteInputs?.firstOrNull()
        if (remoteInput == null) {
            copyPlainQuiet()
            Toast.makeText(this, getString(R.string.dashboard_reply_not_sent), Toast.LENGTH_SHORT).show()
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
            copyPlainQuiet()
            Toast.makeText(this, getString(R.string.dashboard_reply_not_sent_copied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun openMainActivityForProUpgrade() {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra(MainActivity.EXTRA_OPEN_PRO_UPGRADE, true)
            },
        )
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

    private fun userFriendlyError(throwable: Throwable): String {
        if (throwable is DailyAiLimitReachedException) {
            return throwable.message ?: DailyAiLimitReachedException.DEFAULT_MESSAGE
        }
        val rawMessage = throwable.message
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

    /**
     * [ComposeView] is final (cannot subclass). This [FrameLayout] runs [bubbleDragListener]
     * before child dispatch so HEAD drag/tap win over Compose.
     */
    private inner class HeadInterceptFrameLayout(
        ctx: Context,
        val composeHost: ComposeView,
    ) : FrameLayout(ctx) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.HEAD) {
                bubbleDragListener?.let { listener ->
                    if (listener.onTouch(composeHost, ev)) return true
                }
            }
            return super.dispatchTouchEvent(ev)
        }
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
