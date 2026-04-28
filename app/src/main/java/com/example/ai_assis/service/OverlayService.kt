package com.example.ai_assis.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
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
import com.example.ai_assis.domain.usecase.GetAiRepliesUseCase
import com.example.ai_assis.presentation.ui.overlay.BubbleContent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@AndroidEntryPoint
class OverlayService : Service() {
    @Inject
    lateinit var getAiRepliesUseCase: GetAiRepliesUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var overlayOwner: OverlayViewTreeOwner
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var overlayParams: WindowManager.LayoutParams
    private var bubbleView: ComposeView? = null
    private var scrimView: View? = null
    private var latestOverlayX = 0
    private var latestOverlayY = 0

    @RequiresApi(Build.VERSION_CODES.O)
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
        createOverlayIfPermitted()
        observeMessages()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bubbleView?.let { windowManager.removeView(it) }
        scrimView?.let { windowManager.removeView(it) }
        bubbleView = null
        scrimView = null
        NotificationEventBus.setServiceRunning(false)
        overlayOwner.performDestroy()
        serviceScope.cancel()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun observeMessages() {
        serviceScope.launch {
            NotificationEventBus.events.collectLatest { event ->
                Log.d(logTag, "AI pipeline started for ${event.appSource}")
                getAiRepliesUseCase(event.message).collect { result ->
                    result.onSuccess { replies ->
                        Log.d(logTag, "AI call success for ${event.appSource} with ${replies.size} replies")
                        if (replies.isNotEmpty()) {
                            NotificationEventBus.addSuggestion(message = event, replies = replies)
                            Log.d(logTag, "Suggestion added for ${event.appSource}")
                            renderOverlay()
                        } else {
                            Log.w(logTag, "AI returned empty replies for ${event.appSource}")
                        }
                    }
                    result.onFailure { throwable ->
                        Log.e(
                            logTag,
                            "AI call failed for ${event.appSource}: ${throwable.message.orEmpty()}",
                            throwable,
                        )
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createOverlayIfPermitted() {
        if (!android.provider.Settings.canDrawOverlays(this)) return
        if (bubbleView != null) return

        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val metrics = resources.displayMetrics
            val marginPx = 16.dpToPx()
            val defaultX = (metrics.widthPixels - 84.dpToPx()).coerceAtLeast(marginPx)
            val defaultY = (metrics.heightPixels - 180.dpToPx()).coerceAtLeast(marginPx)
            x = prefs.getInt("overlay_x", defaultX)
            y = prefs.getInt("overlay_y", defaultY)
            latestOverlayX = x
            latestOverlayY = y
        }

        bubbleView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            setContent {
                val meta by NotificationEventBus.metaState.collectAsState()
                val items by NotificationEventBus.chatHistory.collectAsState()
                BubbleContent(
                    mode = meta.mode,
                    unreadCount = meta.unreadCount,
                    updatesPaused = meta.updatesPaused,
                    items = items,
                    onHeadClick = {
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.PANEL)
                        renderOverlay()
                    },
                    onCollapse = {
                        NotificationEventBus.setMode(NotificationEventBus.OverlayMode.HEAD)
                        renderOverlay()
                    },
                    onClear = { NotificationEventBus.clearHistory() },
                    onToggleUpdates = { NotificationEventBus.togglePaused() },
                    onReplyClick = ::copyToClipboard,
                )
            }
        }

        bubbleView?.let { view ->
            makeDraggable(view, overlayParams)
            windowManager.addView(view, overlayParams)
        }
        renderOverlay()
    }

    private fun renderOverlay() {
        val view = bubbleView ?: return
        val mode = NotificationEventBus.metaState.value.mode
        overlayParams.width = if (mode == NotificationEventBus.OverlayMode.HEAD) {
            WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            (resources.displayMetrics.widthPixels * 0.88f).roundToInt()
        }
        overlayParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        windowManager.updateViewLayout(view, overlayParams)
        updateScrimForMode(mode)
        view.invalidate()
    }

    private fun updateScrimForMode(mode: NotificationEventBus.OverlayMode) {
        if (mode == NotificationEventBus.OverlayMode.PANEL) {
            if (scrimView != null) return
            scrimView = View(this).apply {
                setBackgroundColor(0x00000000)
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
            scrimView?.let { windowManager.removeView(it) }
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

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (NotificationEventBus.metaState.value.mode == NotificationEventBus.OverlayMode.PANEL) {
                        return@setOnTouchListener false
                    }
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
        latestOverlayX = params.x
        latestOverlayY = params.y
        prefs.edit().putInt("overlay_x", latestOverlayX).putInt("overlay_y", latestOverlayY).apply()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("reply", text))
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
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
}

private fun Int.dpToPx(): Int = (this * android.content.res.Resources.getSystem().displayMetrics.density).roundToInt()

private class OverlayViewTreeOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val vmStore = ViewModelStore()

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = vmStore

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
