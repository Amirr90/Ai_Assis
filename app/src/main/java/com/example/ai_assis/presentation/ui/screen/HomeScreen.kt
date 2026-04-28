package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.presentation.viewmodel.HomeViewModel
import com.example.ai_assis.service.NotificationEventBus

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit,
) {
    val selectedTone by viewModel.selectedTone.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val overlayMeta by viewModel.overlayMeta.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            val statusText = if (overlayMeta.isServiceRunning) "Assistant: Running" else "Assistant: Stopped"
            val statusColor = if (overlayMeta.isServiceRunning) Color(0xFF2E7D32) else Color(0xFFB00020)
            Text(statusText, color = statusColor, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Mode: ${overlayMeta.mode.name} • Unread: ${overlayMeta.unreadCount}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Text("Tone Selector", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplyTone.entries.forEach { tone ->
                    Button(onClick = { viewModel.saveTone(tone) }) {
                        Text(if (tone == selectedTone) "✓ ${tone.displayName}" else tone.displayName)
                    }
                }
            }
        }
        item {
            Button(onClick = onOpenNotificationAccess) {
                Text("Notification Access Settings")
            }
        }
        item {
            Button(onClick = onOpenOverlayPermission) {
                Text("Overlay Permission Settings")
            }
        }
        item {
            Button(onClick = onStartOverlayService) {
                Text("Enable Assistant")
            }
        }
        item {
            Button(onClick = onStopOverlayService) {
                Text("Disable Assistant")
            }
        }
        item {
            Text("Chat Window", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Each incoming message shows its own suggested replies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (chatHistory.isEmpty()) {
            item {
                Text(
                    text = "No suggestions yet. Keep \"Enable Assistant\" on, then receive a new WhatsApp/Instagram message. If still empty, check logcat with tag SmartAssistant.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(chatHistory, key = { it.createdAtMs }) { item ->
                MessageSuggestionCard(item = item)
            }
        }
    }
}

@Composable
private fun MessageSuggestionCard(item: NotificationEventBus.ChatSuggestionItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "${item.chatMessage.sender} • ${item.chatMessage.appSource}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = item.chatMessage.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        item.replies.forEach { reply ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x226750A4), RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Text(text = reply, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
