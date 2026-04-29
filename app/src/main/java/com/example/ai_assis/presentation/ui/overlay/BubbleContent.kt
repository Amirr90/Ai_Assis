package com.example.ai_assis.presentation.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import com.example.ai_assis.domain.model.appDisplayLabelFor
import com.example.ai_assis.presentation.ui.components.AppSourceIcon
import com.example.ai_assis.presentation.ui.components.SourceTab
import com.example.ai_assis.presentation.ui.components.SourceTabsRow
import com.example.ai_assis.presentation.ui.components.appIconResFor
import com.example.ai_assis.presentation.ui.components.defaultSourceTabs
import com.example.ai_assis.presentation.ui.components.sourceTabFromKey
import com.example.ai_assis.service.NotificationEventBus

data class OverlayUiState(
    val mode: NotificationEventBus.OverlayMode = NotificationEventBus.OverlayMode.HEAD,
    val unreadCount: Int = 0,
    val updatesPaused: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
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
    onDirectSend: (String, String) -> Unit,
    onRetry: () -> Boolean,
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
                onCollapse = onCollapse,
                onClear = onClear,
                onToggleUpdates = onToggleUpdates,
                onReplyClick = onReplyClick,
                onDirectSend = onDirectSend,
                onRetry = onRetry,
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
            .clickable(onClick = onClick),
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
                text = "AI",
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
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, String) -> Unit,
    onRetry: () -> Boolean,
) {
    val listState = rememberLazyListState()
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
    Column(
        modifier = Modifier
            .background(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Smart Replies",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Row {
                IconButton(onClick = onToggleUpdates) {
                    if (updatesPaused) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume updates",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Pause updates",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Close, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
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
                    text = "Generating reply…",
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
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = "Retry last request",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable { onRetry() },
                )
            }
        }
        SourceTabsRow(
            tabs = defaultSourceTabs,
            selectedTab = selectedTab,
            onTabSelected = { selectedTabKey = it.key },
            modifier = Modifier.fillMaxWidth(),
        )

        if (visibleItems.isEmpty() && !isLoading && errorMessage == null) {
            Text(
                text = if (selectedTab == SourceTab.All) {
                    "No suggestions yet. Open WhatsApp, Instagram, or LinkedIn and receive a message."
                } else {
                    "No suggestions yet for ${selectedTab.title}."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = 420.dp)
                .fillMaxWidth(),
            userScrollEnabled = true,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = visibleItems,
                key = { it.id },
                contentType = { "suggestion_entry" },
            ) { entry ->
                SuggestionCard(
                    entry = entry,
                    onReplyClick = onReplyClick,
                    onDirectSend = onDirectSend,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    entry: NotificationEventBus.ChatSuggestionItem,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, String) -> Unit,
) {
    var activeSendKey by remember(entry.id) { mutableStateOf<String?>(null) }
    var isMessageExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    var isMessageOverflowing by rememberSaveable(entry.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
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
                isMessageOverflowing = if (isMessageExpanded) {
                    true
                } else {
                    layoutResult.hasVisualOverflow
                }
            },
        )
        if (isMessageOverflowing) {
            Text(
                text = if (isMessageExpanded) "Show less" else "Show more",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable { isMessageExpanded = !isMessageExpanded },
            )
        }
        Text(
            text = "Source: ${entry.source.name}${entry.fallbackReason?.let { " • ${toFallbackLabel(it)}" } ?: ""}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
        )
        entry.replies.forEach { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                    .clickable { onReplyClick(reply) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = reply,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                if (entry.chatMessage.replyActionKey != null) {
                    Spacer(Modifier.width(6.dp))
                    val sendKey = "${entry.id}:$reply"
                    val isSending = activeSendKey == sendKey
                    SmoothSendButton(
                        isSending = isSending,
                        onClick = {
                            if (isSending) return@SmoothSendButton
                            activeSendKey = sendKey
                            onDirectSend(reply, entry.chatMessage.replyActionKey)
                        },
                    )
                    LaunchedEffect(isSending, sendKey) {
                        if (isSending) {
                            delay(700)
                            if (activeSendKey == sendKey) activeSendKey = null
                        }
                    }
                }
            }
        }
    }
}

private fun toFallbackLabel(rawReason: String): String {
    return when (rawReason) {
        "cached" -> "cached cloud response"
        "circuit_breaker_open" -> "cloud cooldown active"
        "cloud_timeout" -> "cloud timeout"
        "cloud_providers_cooldown" -> "cloud providers cooling down"
        "cloud_rate_limited" -> "cloud rate-limited"
        "cloud_empty" -> "cloud returned empty result"
        "cloud_error_both_providers" -> "all cloud providers failed"
        "cloud_error" -> "cloud request failed"
        "high_quality_mode" -> "high quality mode"
        "insufficient_count" -> "on-device count below threshold"
        "length_over_limit" -> "on-device reply too long"
        "blocked_content" -> "safety filtered"
        "low_confidence" -> "on-device confidence too low"
        "language_mismatch" -> "language mismatch"
        else -> rawReason.replace('_', ' ')
    }
}

@Composable
private fun SmoothSendButton(
    isSending: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 110),
        label = "sendButtonScale",
    )

    FilledTonalIconButton(
        onClick = onClick,
        enabled = !isSending,
        interactionSource = interactionSource,
        modifier = Modifier
            .size(28.dp)
            .scale(scale),
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send directly",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
        }
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
