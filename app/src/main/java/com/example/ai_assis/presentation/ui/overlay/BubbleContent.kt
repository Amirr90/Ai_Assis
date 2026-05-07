package com.example.ai_assis.presentation.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ai_assis.R
import com.example.ai_assis.domain.model.appDisplayLabelFor
import com.example.ai_assis.presentation.ui.components.DailyLimitPricingCallout
import com.example.ai_assis.presentation.ui.components.OverlayProPricingTeaser
import com.example.ai_assis.presentation.ui.components.AppSourceIcon
import com.example.ai_assis.presentation.ui.components.SourceTab
import com.example.ai_assis.presentation.ui.components.appIconResFor
import com.example.ai_assis.presentation.ui.components.defaultSourceTabs
import com.example.ai_assis.presentation.ui.components.sourceTabFromKey
import com.example.ai_assis.domain.model.ChatMessage
import com.example.ai_assis.service.NotificationEventBus

private const val PANEL_DISMISS_WAIT_MS = 380L
private val ChatHeadSizeDp = 64.dp

data class OverlayUiState(
    val mode: NotificationEventBus.OverlayMode = NotificationEventBus.OverlayMode.HEAD,
    val unreadCount: Int = 0,
    val updatesPaused: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val errorKind: NotificationEventBus.ErrorKind = NotificationEventBus.ErrorKind.NONE,
    val isBubbleVisible: Boolean = false,
    val canUpgrade: Boolean = true,
    /** Last known chat-head top-left in screen px (from overlay service); used as expand pivot for PANEL. */
    val bubbleAnchorXPx: Int = 0,
    val bubbleAnchorYPx: Int = 0,
    val items: List<NotificationEventBus.ChatSuggestionItem> = emptyList(),
)

/** Chat head only; used by the small draggable overlay window ([OverlayService] head layer). */
@Composable
fun BubbleHeadOverlayContent(
    unreadCount: Int,
    isLoading: Boolean,
    isBubbleVisible: Boolean,
    onHeadClick: () -> Unit,
) {
    if (!isBubbleVisible) return
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            initialScale = 0.88f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        ) + fadeIn(animationSpec = tween(260, easing = FastOutSlowInEasing)),
    ) {
        ChatHeadBubble(
            unreadCount = unreadCount,
            isLoading = isLoading,
            onClick = onHeadClick,
        )
    }
}

/** Full-screen panel; used by the dedicated panel overlay window ([OverlayService] panel layer). */
@Composable
fun BubblePanelOverlayContent(
    uiState: OverlayUiState,
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, ChatMessage) -> Unit,
    onRegenerateSuggestion: (ChatMessage) -> Boolean,
    onRetry: () -> Boolean,
    onOpenProUpgrade: () -> Unit,
) {
    if (!uiState.isBubbleVisible) return
    if (uiState.mode != NotificationEventBus.OverlayMode.PANEL) return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val bubbleSizePx = with(density) { ChatHeadSizeDp.toPx() }
    val pivotXFraction =
        ((uiState.bubbleAnchorXPx + bubbleSizePx / 2f) / screenWidthPx).coerceIn(0.02f, 0.98f)
    val pivotYFraction =
        ((uiState.bubbleAnchorYPx + bubbleSizePx / 2f) / screenHeightPx).coerceIn(0.02f, 0.98f)
    var panelOpenFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        panelOpenFraction = 1f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = panelOpenFraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "panelOpenFraction",
    )
    val panelScale = lerp(0.24f, 1f, animatedFraction)
    val scrimAlphaValue = animatedFraction

    fun dismissToHead() {
        scope.launch {
            panelOpenFraction = 0f
            delay(PANEL_DISMISS_WAIT_MS)
            onCollapse()
        }
    }

    fun clearWithAnimation() {
        scope.launch {
            panelOpenFraction = 0f
            delay(PANEL_DISMISS_WAIT_MS)
            onClear()
        }
    }

    val backdropInteraction = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .alpha(scrimAlphaValue)
                .background(Color(0x52000000))
                .clickable(
                    indication = null,
                    interactionSource = backdropInteraction,
                    onClickLabel = stringResource(R.string.dashboard_overlay_collapse),
                    onClick = { dismissToHead() },
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = panelScale
                    scaleY = panelScale
                    transformOrigin = TransformOrigin(pivotXFraction, pivotYFraction)
                },
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.88f)
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 8.dp),
            ) {
                ExpandedChatPanel(
                    items = uiState.items,
                    canUpgrade = uiState.canUpgrade,
                    updatesPaused = uiState.updatesPaused,
                    isLoading = uiState.isLoading,
                    errorMessage = uiState.errorMessage,
                    errorKind = uiState.errorKind,
                    onCollapse = { dismissToHead() },
                    onClear = { clearWithAnimation() },
                    onToggleUpdates = onToggleUpdates,
                    onReplyClick = onReplyClick,
                    onDirectSend = onDirectSend,
                    onRegenerateSuggestion = onRegenerateSuggestion,
                    onRetry = onRetry,
                    onOpenProUpgrade = onOpenProUpgrade,
                )
            }
        }
    }
}

@Composable
fun BubbleContent(
    uiState: OverlayUiState,
    onHeadClick: () -> Unit,
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, ChatMessage) -> Unit,
    onRegenerateSuggestion: (ChatMessage) -> Boolean,
    onRetry: () -> Boolean,
    onOpenProUpgrade: () -> Unit,
) {
    if (!uiState.isBubbleVisible) return

    when (uiState.mode) {
        NotificationEventBus.OverlayMode.HEAD -> {
            BubbleHeadOverlayContent(
                unreadCount = uiState.unreadCount,
                isLoading = uiState.isLoading,
                isBubbleVisible = true,
                onHeadClick = onHeadClick,
            )
        }
        NotificationEventBus.OverlayMode.PANEL -> {
            BubblePanelOverlayContent(
                uiState = uiState,
                onCollapse = onCollapse,
                onClear = onClear,
                onToggleUpdates = onToggleUpdates,
                onReplyClick = onReplyClick,
                onDirectSend = onDirectSend,
                onRegenerateSuggestion = onRegenerateSuggestion,
                onRetry = onRetry,
                onOpenProUpgrade = onOpenProUpgrade,
            )
        }
    }
}

@Composable
private fun ChatHeadBubble(
    unreadCount: Int,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val headActionLabel = stringResource(R.string.dashboard_assistant_head_action)
    val headScale by animateFloatAsState(
        targetValue = if (isLoading) 0.94f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "chatHeadLoadPulse",
    )
    Box(
        modifier = Modifier
            .size(ChatHeadSizeDp)
            .scale(headScale)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .semantics {
                role = Role.Button
                onClick(action = { onClick(); true })
                contentDescription = headActionLabel
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 3.dp,
            )
        } else {
            Text(
                text = stringResource(R.string.dashboard_ai_short_label),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (unreadCount > 0 && !isLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = unreadCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ExpandedChatPanel(
    items: List<NotificationEventBus.ChatSuggestionItem>,
    canUpgrade: Boolean,
    updatesPaused: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    errorKind: NotificationEventBus.ErrorKind,
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, ChatMessage) -> Unit,
    onRegenerateSuggestion: (ChatMessage) -> Boolean,
    onRetry: () -> Boolean,
    onOpenProUpgrade: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var selectedTabKey by rememberSaveable { mutableStateOf(SourceTab.All.key) }
    val selectedTab = remember(selectedTabKey) { sourceTabFromKey(selectedTabKey) }
    val filteredItems = remember(items, selectedTab) {
        val selectedPackage = selectedTab.packageName
        if (selectedPackage == null) {
            items
        } else {
            items.filter { it.chatMessage.appSource == selectedPackage }
        }
    }
    val visibleItems = remember(filteredItems) { filteredItems.take(12) }
    val configuration = LocalConfiguration.current
    // Cap list height (slightly shorter than before) so the panel feels lighter on screen.
    val listMaxHeight = remember(configuration.screenHeightDp) {
        val fiftyPercent = (configuration.screenHeightDp * 0.5f).dp
        if (fiftyPercent < 340.dp) fiftyPercent else 340.dp
    }
    val panelShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = panelShape,
        // Slightly higher surface tier so body text meets contrast on all devices.
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_overlay_title),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onToggleUpdates) {
                        if (updatesPaused) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.dashboard_overlay_resume_updates),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = stringResource(R.string.dashboard_overlay_pause_updates),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    IconButton(onClick = onClear) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.dashboard_overlay_clear),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = onCollapse) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.dashboard_overlay_collapse),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            // Max visibility for pricing until subscription / isPro gating exists.
            // Suppressed while the daily-limit error block shows its own DailyLimitPricingCallout
            // to avoid duplicate Upgrade CTAs stacked in the panel.
            val showProTeaser = errorKind != NotificationEventBus.ErrorKind.DAILY_AI_LIMIT
            if (showProTeaser) {
                OverlayProPricingTeaser(
                    onOpenProUpgrade = onOpenProUpgrade,
                    canUpgrade = canUpgrade,
                )
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dashboard_overlay_generating_reply),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            if (errorMessage != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = userSafeErrorText(errorMessage),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (errorKind == NotificationEventBus.ErrorKind.DAILY_AI_LIMIT) {
                        DailyLimitPricingCallout(
                            onUpgrade = onOpenProUpgrade,
                            canUpgrade = canUpgrade,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.dashboard_retry_last_request),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.clickable { onRetry() },
                        )
                    }
                }
            }
            OverlaySourceTabs(
                tabs = defaultSourceTabs,
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTabKey = tab.key
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (visibleItems.isEmpty() && !isLoading && errorMessage == null) {
                Text(
                    text = if (selectedTab == SourceTab.All) {
                        stringResource(R.string.dashboard_overlay_no_suggestions_all)
                    } else {
                        stringResource(R.string.dashboard_no_suggestions_for_source, selectedTab.title)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listMaxHeight)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                visibleItems.forEach { entry ->
                    key(entry.id) {
                        SuggestionCard(
                            entry = entry,
                            isGlobalLoading = isLoading,
                            errorMessage = errorMessage,
                            onReplyClick = onReplyClick,
                            onDirectSend = onDirectSend,
                            onRegenerateSuggestion = onRegenerateSuggestion,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlaySourceTabs(
    tabs: List<SourceTab>,
    selectedTab: SourceTab,
    onTabSelected: (SourceTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        items(
            items = tabs,
            key = { it.key },
        ) { tab ->
            val selected = tab.key == selectedTab.key
            FilterChip(
                selected = selected,
                onClick = {
                    if (!selected) {
                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                    onTabSelected(tab)
                },
                label = {
                    Text(
                        text = tab.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    AppSourceIcon(
                        packageName = tab.packageName,
                        iconRes = tab.iconRes,
                        fallbackLabel = tab.title.take(1),
                    )
                },
            )
        }
    }
}

private fun chatMessageStableKey(msg: ChatMessage): String = listOf(
    msg.appSource,
    msg.sender,
    msg.message,
    msg.isSummaryNotification.toString(),
    msg.replyActionKey ?: "",
    msg.messageType.name,
    msg.direction.name,
).joinToString("\u0001")

@Composable
private fun SuggestionCard(
    modifier: Modifier = Modifier,
    entry: NotificationEventBus.ChatSuggestionItem,
    isGlobalLoading: Boolean,
    errorMessage: String?,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, ChatMessage) -> Unit,
    onRegenerateSuggestion: (ChatMessage) -> Boolean,
) {
    val stableKey = chatMessageStableKey(entry.chatMessage)
    val regenInFlight = remember(stableKey) { AtomicBoolean(false) }
    var isRegenerating by remember(stableKey) { mutableStateOf(false) }
    var regenStartRevision by remember(stableKey) { mutableStateOf(0L) }
    var canUndo by remember(stableKey) { mutableStateOf(false) }
    var showNewLabel by remember(stableKey) { mutableStateOf(false) }
    var displayOverride by remember(stableKey) { mutableStateOf<List<String>?>(null) }
    var previousSuggestions by remember(stableKey) { mutableStateOf<List<String>?>(null) }
    var undoOfferSession by remember(stableKey) { mutableIntStateOf(0) }
    var showRegenLimitError by remember(stableKey) { mutableStateOf(false) }

    fun invalidateUndoSession() {
        canUndo = false
        showNewLabel = false
        undoOfferSession++
    }

    fun runRegenerate() {
        if (entry.regenerateAttemptCount >= 3) {
            isRegenerating = false
            regenInFlight.set(false)
            showRegenLimitError = true
            return
        }
        if (isRegenerating) return
        showRegenLimitError = false
        invalidateUndoSession()
        previousSuggestions = (displayOverride ?: entry.replies).toList()
        if (!regenInFlight.compareAndSet(false, true)) return
        isRegenerating = true
        regenStartRevision = entry.contentRevision
        val accepted = onRegenerateSuggestion(entry.chatMessage)
        if (!accepted) {
            isRegenerating = false
            regenInFlight.set(false)
            if (entry.regenerateAttemptCount >= 3) {
                showRegenLimitError = true
            }
            return
        }
    }

    val visibleReplies = displayOverride ?: entry.replies
    val regenButtonEnabled = !isRegenerating

    LaunchedEffect(entry.contentRevision, isRegenerating, regenStartRevision) {
        if (!isRegenerating) return@LaunchedEffect
        if (entry.contentRevision > regenStartRevision) {
            isRegenerating = false
            regenInFlight.set(false)
            showNewLabel = true
            canUndo = true
            displayOverride = null
            undoOfferSession++
        }
    }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null && isRegenerating) {
            isRegenerating = false
            regenInFlight.set(false)
        }
    }

    LaunchedEffect(isGlobalLoading, errorMessage, entry.contentRevision, regenStartRevision, isRegenerating) {
        if (!isRegenerating) return@LaunchedEffect
        if (errorMessage != null) return@LaunchedEffect
        if (isGlobalLoading) return@LaunchedEffect
        if (entry.contentRevision > regenStartRevision) return@LaunchedEffect
        delay(100)
        if (!isRegenerating) return@LaunchedEffect
        if (entry.contentRevision > regenStartRevision) return@LaunchedEffect
        isRegenerating = false
        regenInFlight.set(false)
    }

    LaunchedEffect(canUndo, undoOfferSession) {
        if (!canUndo) return@LaunchedEffect
        val session = undoOfferSession
        delay(5_000)
        if (!canUndo || undoOfferSession != session) return@LaunchedEffect
        previousSuggestions = null
        canUndo = false
        showNewLabel = false
        undoOfferSession++
    }

    LaunchedEffect(showRegenLimitError) {
        if (!showRegenLimitError) return@LaunchedEffect
        delay(4_000)
        showRegenLimitError = false
    }

    var activeSendKey by remember(entry.id) { mutableStateOf<String?>(null) }
    var expandedReplyKey by remember(entry.id) { mutableStateOf<String?>(null) }
    var isMessageExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    var isMessageOverflowing by rememberSaveable(entry.id) { mutableStateOf(false) }
    var editableReply by remember(entry.id) { mutableStateOf<String?>(null) }
    var editedReplyText by remember(entry.id) { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val regenerateContentDescription = stringResource(R.string.dashboard_overlay_regenerate)
    val regenLimitExceededText = stringResource(R.string.dashboard_overlay_regenerate_limit_exceeded)
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            // Solid container (no heavy wash-out) for reliable on-surface contrast.
            .background(MaterialTheme.colorScheme.surfaceContainer, cardShape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppSourceBadge(packageName = entry.chatMessage.appSource)
            Text(
                text = entry.chatMessage.sender,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = 280,
                        easing = FastOutSlowInEasing,
                    ),
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.chatMessage.message,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (isMessageExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutResult ->
                    val next = if (isMessageExpanded) true else layoutResult.hasVisualOverflow
                    if (next != isMessageOverflowing) {
                        isMessageOverflowing = next
                    }
                },
            )
            if (isMessageOverflowing) {
                AnimatedContent(
                    targetState = isMessageExpanded,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                    },
                    label = "messageExpandCta",
                ) { expanded ->
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.dashboard_show_less)
                        } else {
                            stringResource(R.string.dashboard_show_more)
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            isMessageExpanded = !isMessageExpanded
                        },
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    R.string.dashboard_source_metadata,
                    entry.source.name,
                    buildMetadataSuffix(
                        fallbackReason = entry.fallbackReason,
                        isSummaryNotification = entry.chatMessage.isSummaryNotification,
                    ),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            SmallOverlayIconButton(
                onClick = { runRegenerate() },
                enabled = regenButtonEnabled,
                buttonSize = 28,
                modifier = Modifier.semantics {
                    contentDescription = regenerateContentDescription
                },
            ) {
                if (isRegenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    Text(
                        text = "🔄",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
        if (showRegenLimitError) {
            Text(
                text = regenLimitExceededText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (showNewLabel || canUndo) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showNewLabel) {
                    Text(
                        text = stringResource(R.string.dashboard_overlay_new_suggestions),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (canUndo) {
                    TextButton(
                        onClick = {
                            displayOverride = previousSuggestions
                            canUndo = false
                            showNewLabel = false
                            undoOfferSession++
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.dashboard_overlay_undo),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
        AnimatedContent(
            targetState = entry.contentRevision,
            transitionSpec = {
                (
                    fadeIn(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    ) + slideInVertically(
                        initialOffsetY = { h -> h / 10 },
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                    )
                ) togetherWith (
                    fadeOut(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        slideOutVertically(
                            targetOffsetY = { h -> -h / 12 },
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                        )
                )
            },
            label = "suggestionReplies",
        ) { _ ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
        visibleReplies.forEachIndexed { index, reply ->
            val replyKey = "${entry.id}:$index"
            val isEditingThisReply = editableReply == reply
            val isExpanded = expandedReplyKey == replyKey || isEditingThisReply
            val replyRowShape = RoundedCornerShape(10.dp)
            val collapseReplyRow = {
                expandedReplyKey = null
                editableReply = null
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            }
            val sendKey = "${entry.id}:$reply"
            val isSendingDirect = activeSendKey == sendKey

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(replyRowShape)
                    .background(MaterialTheme.colorScheme.primaryContainer, replyRowShape)
                    .animateContentSize(
                        animationSpec = tween(
                            durationMillis = 220,
                            easing = FastOutSlowInEasing,
                        ),
                    ),
            ) {
                if (!isExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = reply,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = { expandedReplyKey = replyKey },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        onReplyClick(reply)
                                    },
                                ),
                        )
                        SmoothSendButton(
                            isSending = isSendingDirect,
                            buttonSize = 28,
                            iconSize = 14.dp,
                            onClick = {
                                if (isSendingDirect) return@SmoothSendButton
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                activeSendKey = sendKey
                                onDirectSend(reply, entry.chatMessage)
                            },
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = reply,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        collapseReplyRow()
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        onReplyClick(reply)
                                    },
                                ),
                        )
                        SmallOverlayIconButton(
                            onClick = {
                                editableReply = reply
                                editedReplyText = reply
                            },
                            buttonSize = 28,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.dashboard_overlay_edit_suggestion),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        SmoothSendButton(
                            isSending = isSendingDirect,
                            buttonSize = 28,
                            iconSize = 14.dp,
                            onClick = {
                                if (isSendingDirect) return@SmoothSendButton
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                activeSendKey = sendKey
                                onDirectSend(reply, entry.chatMessage)
                            },
                        )
                    }
                }
                LaunchedEffect(isSendingDirect, sendKey) {
                    if (isSendingDirect) {
                        delay(700)
                        if (activeSendKey == sendKey) activeSendKey = null
                    }
                }
                if (isEditingThisReply) {
                val canSubmit = editedReplyText.trim().isNotEmpty()
                val focusRequester = remember(reply) { FocusRequester() }
                LaunchedEffect(reply) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
                val editorShape = RoundedCornerShape(10.dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(editorShape)
                        .background(MaterialTheme.colorScheme.surface, editorShape)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedTextField(
                        value = editedReplyText,
                        onValueChange = { editedReplyText = it },
                        singleLine = false,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                editableReply = null
                            },
                        ) {
                            Text(text = stringResource(R.string.dashboard_cancel))
                        }
                        TextButton(
                            onClick = {
                                val finalText = editedReplyText.trim()
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                onReplyClick(finalText)
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                editableReply = null
                            },
                            enabled = canSubmit,
                        ) {
                            Text(text = stringResource(R.string.dashboard_overlay_copy_edited))
                        }
                        if (entry.chatMessage.replyActionKey != null) {
                            val sendEditedKey = "${entry.id}:edited:$reply"
                            val isSendingEdited = activeSendKey == sendEditedKey
                            TextButton(
                                onClick = {
                                    if (isSendingEdited) return@TextButton
                                    val finalText = editedReplyText.trim()
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    activeSendKey = sendEditedKey
                                    onDirectSend(finalText, entry.chatMessage)
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    editableReply = null
                                },
                                enabled = canSubmit && !isSendingEdited,
                            ) {
                                if (isSendingEdited) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text(text = stringResource(R.string.dashboard_overlay_send_edited))
                                }
                            }
                            LaunchedEffect(isSendingEdited, sendEditedKey) {
                                if (isSendingEdited) {
                                    delay(700)
                                    if (activeSendKey == sendEditedKey) activeSendKey = null
                                }
                            }
                        }
                    }
                }
                }
            }
        }
            }
        }
    }
}

@Composable
private fun fallbackLabel(rawReason: String): String {
    return when (rawReason) {
        "cached" -> stringResource(R.string.dashboard_fallback_cached)
        "circuit_breaker_open" -> stringResource(R.string.dashboard_fallback_circuit_open)
        "cloud_timeout" -> stringResource(R.string.dashboard_fallback_timeout)
        "cloud_providers_cooldown" -> stringResource(R.string.dashboard_fallback_providers_cooldown)
        "cloud_rate_limited" -> stringResource(R.string.dashboard_fallback_rate_limited)
        "cloud_empty" -> stringResource(R.string.dashboard_fallback_empty)
        "cloud_error_both_providers" -> stringResource(R.string.dashboard_fallback_both_failed)
        "cloud_error" -> stringResource(R.string.dashboard_fallback_cloud_error)
        "high_quality_mode" -> stringResource(R.string.dashboard_fallback_high_quality)
        "insufficient_count" -> stringResource(R.string.dashboard_fallback_insufficient_count)
        "length_over_limit" -> stringResource(R.string.dashboard_fallback_length_over_limit)
        "blocked_content" -> stringResource(R.string.dashboard_fallback_blocked)
        "low_confidence" -> stringResource(R.string.dashboard_fallback_low_confidence)
        "language_mismatch" -> stringResource(R.string.dashboard_fallback_language_mismatch)
        else -> stringResource(R.string.dashboard_fallback_applied)
    }
}

@Composable
private fun buildMetadataSuffix(
    fallbackReason: String?,
    isSummaryNotification: Boolean,
): String {
    val parts = buildList {
        fallbackReason?.let { add(fallbackLabel(it)) }
        if (isSummaryNotification) add(stringResource(R.string.dashboard_summary_tag))
    }
    return if (parts.isEmpty()) "" else stringResource(R.string.dashboard_fallback_suffix, parts.joinToString(" | "))
}

@Composable
private fun userSafeErrorText(error: String?): String {
    if (error.isNullOrBlank()) return stringResource(R.string.dashboard_user_safe_error_default)
    val lowered = error.lowercase()
    return when {
        lowered.contains("provider") || lowered.contains("http") || lowered.contains("exception") || lowered.contains("timeout") ->
            stringResource(R.string.dashboard_user_safe_error_default)
        else -> error
    }
}

@Composable
private fun SmoothSendButton(
    isSending: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    buttonSize: Int = 28,
    iconSize: Dp = 14.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "sendButtonScale",
    )

    SmallOverlayIconButton(
        onClick = onClick,
        enabled = enabled && !isSending,
        buttonSize = buttonSize,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale),
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(iconSize),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = stringResource(R.string.dashboard_overlay_send_directly),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun SmallOverlayIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Int = 40,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(buttonSize.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun AppSourceBadge(packageName: String) {
    Row(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AppSourceIcon(
            packageName = packageName,
            iconRes = appIconResFor(packageName),
            fallbackLabel = appDisplayLabelFor(packageName).take(1),
        )
        Text(
            text = appDisplayLabelFor(packageName),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
