package com.example.ai_assis.presentation.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ai_assis.service.NotificationEventBus

@Composable
fun BubbleContent(
    mode: NotificationEventBus.OverlayMode,
    unreadCount: Int,
    updatesPaused: Boolean,
    items: List<NotificationEventBus.ChatSuggestionItem>,
    onHeadClick: () -> Unit,
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
) {
    Box {
        AnimatedVisibility(
            visible = mode == NotificationEventBus.OverlayMode.HEAD,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            ChatHeadBubble(unreadCount = unreadCount, onClick = onHeadClick)
        }

        AnimatedVisibility(
            visible = mode == NotificationEventBus.OverlayMode.PANEL,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            ExpandedChatPanel(
                items = items,
                updatesPaused = updatesPaused,
                onCollapse = onCollapse,
                onClear = onClear,
                onToggleUpdates = onToggleUpdates,
                onReplyClick = onReplyClick,
            )
        }
    }
}

@Composable
private fun ChatHeadBubble(unreadCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .background(Color(0xFF6750A4), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "AI",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .background(Color(0xFFE53935), CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = unreadCount.toString(),
                    color = Color.White,
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
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .background(color = Color(0xEE1C1B1F), shape = RoundedCornerShape(16.dp))
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
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
            )
            Row {
                IconButton(onClick = onToggleUpdates) {
                    if (updatesPaused) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Resume updates",
                            tint = Color.White,
                        )
                    } else {
                        Text(text = "II", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                }
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear", tint = Color.White)
                }
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.Close, contentDescription = "Collapse", tint = Color.White)
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .heightIn(max = 420.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x332A2A2A), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "${entry.chatMessage.sender} • ${entry.chatMessage.appSource}",
                        color = Color(0xFFD0BCFF),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = entry.chatMessage.message,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    entry.replies.forEach { reply ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF6750A4),
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { onReplyClick(reply) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = reply,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }
}
