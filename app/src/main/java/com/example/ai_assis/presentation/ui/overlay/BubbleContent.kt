package com.example.ai_assis.presentation.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_assis.domain.model.appLabelFor
import com.example.ai_assis.service.NotificationEventBus

@Composable
fun BubbleContent(
    mode: NotificationEventBus.OverlayMode,
    unreadCount: Int,
    updatesPaused: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    isBubbleVisible: Boolean,
    items: List<NotificationEventBus.ChatSuggestionItem>,
    onHeadClick: () -> Unit,
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, String) -> Unit,
) {
    if (!isBubbleVisible) return

    Box {
        if (mode == NotificationEventBus.OverlayMode.HEAD) {
            ChatHeadBubble(
                unreadCount = unreadCount,
                isLoading = isLoading,
                onClick = onHeadClick,
            )
        } else {
            ExpandedChatPanel(
                items = items,
                updatesPaused = updatesPaused,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onCollapse = onCollapse,
                onClear = onClear,
                onToggleUpdates = onToggleUpdates,
                onReplyClick = onReplyClick,
                onDirectSend = onDirectSend,
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
) {
    val listState = rememberLazyListState()
    val visibleItems = remember(items) { items.take(12) }
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
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    .padding(8.dp),
            )
        }
        if (items.isEmpty() && !isLoading && errorMessage == null) {
            Text(
                text = "No suggestions yet. Open WhatsApp or Instagram and receive a message.",
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
        )
        Text(
            text = "Source: ${entry.source.name}${entry.fallbackReason?.let { " • $it" } ?: ""}",
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
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send directly",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onDirectSend(reply, entry.chatMessage.replyActionKey) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSourceBadge(packageName: String) {
    val (label, bgColor) = appBadgeFor(packageName)
    Box(
        modifier = Modifier
            .background(color = bgColor, shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun appBadgeFor(packageName: String): Pair<String, Color> {
    return when (packageName) {
        "com.whatsapp" -> "WA" to Color(0xFF25D366)
        "com.instagram.android" -> "IG" to Color(0xFFE1306C)
        "com.linkedin.android" -> "LI" to Color(0xFF0A66C2)
        "org.telegram.messenger" -> "TG" to Color(0xFF2CA5E0)
        "com.twitter.android" -> "X" to Color(0xFF1DA1F2)
        "com.facebook.orca" -> "FB" to Color(0xFF0084FF)
        "com.snapchat.android" -> "SC" to Color(0xFFE3A008)
        else -> appLabelFor(packageName) to Color(0xFF6750A4)
    }
}
