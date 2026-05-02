package com.example.ai_assis.presentation.ui.overlay

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import kotlinx.coroutines.delay
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

data class OverlayUiState(
    val mode: NotificationEventBus.OverlayMode = NotificationEventBus.OverlayMode.HEAD,
    val unreadCount: Int = 0,
    val updatesPaused: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val errorKind: NotificationEventBus.ErrorKind = NotificationEventBus.ErrorKind.NONE,
    val isBubbleVisible: Boolean = false,
    val items: List<NotificationEventBus.ChatSuggestionItem> = emptyList(),
)

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

    Box {
        if (uiState.mode == NotificationEventBus.OverlayMode.HEAD) {
            ChatHeadBubble(
                unreadCount = uiState.unreadCount,
                isLoading = uiState.isLoading,
                onClick = onHeadClick,
            )
        } else {
            ExpandedChatPanel(
                items = uiState.items,
                updatesPaused = uiState.updatesPaused,
                isLoading = uiState.isLoading,
                errorMessage = uiState.errorMessage,
                errorKind = uiState.errorKind,
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
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .clickable(onClickLabel = stringResource(R.string.dashboard_assistant_head_action), onClick = onClick),
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
    // Cap the suggestions list at the smaller of 420dp and 60% of screen height
    // so the panel fits within the device viewport even on short screens or
    // when the IME is open.
    val listMaxHeight = remember(configuration.screenHeightDp) {
        val sixtyPercent = (configuration.screenHeightDp * 0.6f).dp
        if (sixtyPercent < 420.dp) sixtyPercent else 420.dp
    }
    val panelShape = RoundedCornerShape(22.dp)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = panelShape,
        color = MaterialTheme.colorScheme.surface,
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
            OverlayProPricingTeaser(onOpenProUpgrade = onOpenProUpgrade)

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
                        style = MaterialTheme.typography.bodySmall,
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
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (errorKind == NotificationEventBus.ErrorKind.DAILY_AI_LIMIT) {
                        DailyLimitPricingCallout(
                            onUpgrade = onOpenProUpgrade,
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
                    style = MaterialTheme.typography.bodySmall,
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
                            isLoading = isLoading,
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

@Composable
private fun SuggestionCard(
    modifier: Modifier = Modifier,
    entry: NotificationEventBus.ChatSuggestionItem,
    isLoading: Boolean,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, ChatMessage) -> Unit,
    onRegenerateSuggestion: (ChatMessage) -> Boolean,
) {
    var activeSendKey by remember(entry.id) { mutableStateOf<String?>(null) }
    var expandedReplyKey by remember(entry.id) { mutableStateOf<String?>(null) }
    var isMessageExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    var isMessageOverflowing by rememberSaveable(entry.id) { mutableStateOf(false) }
    var editableReply by remember(entry.id) { mutableStateOf<String?>(null) }
    var editedReplyText by remember(entry.id) { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), cardShape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
        Text(
            text = entry.chatMessage.message,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
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
            Text(
                text = if (isMessageExpanded) stringResource(R.string.dashboard_show_less) else stringResource(R.string.dashboard_show_more),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable { isMessageExpanded = !isMessageExpanded },
            )
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
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            if (entry.fallbackReason != null) {
                SmallOverlayIconButton(
                    onClick = { onRegenerateSuggestion(entry.chatMessage) },
                    enabled = !isLoading,
                    buttonSize = 28,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.dashboard_retry_last_request),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        entry.replies.forEachIndexed { index, reply ->
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
                            style = MaterialTheme.typography.bodySmall,
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
                        if (entry.chatMessage.replyActionKey != null) {
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
                } else {
                    // Open/expanded: same padding as collapsed so row height stays consistent; only Edit
                    // (Send stays on the collapsed row so quick-send is one tap when folded).
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
                            style = MaterialTheme.typography.bodySmall,
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
                    }
                }
                if (entry.chatMessage.replyActionKey != null) {
                    LaunchedEffect(isSendingDirect, sendKey) {
                        if (isSendingDirect) {
                            delay(700)
                            if (activeSendKey == sendKey) activeSendKey = null
                        }
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
        enabled = !isSending,
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
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
