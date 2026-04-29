package com.example.ai_assis.presentation.ui.screen

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.app.RemoteInput
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ai_assis.domain.model.ReplyTone
import com.example.ai_assis.domain.model.appDisplayLabelFor
import com.example.ai_assis.presentation.ui.components.AppSourceIcon
import com.example.ai_assis.presentation.ui.components.SourceTab
import com.example.ai_assis.presentation.ui.components.SourceTabsRow
import com.example.ai_assis.presentation.ui.components.appIconResFor
import com.example.ai_assis.presentation.ui.components.defaultSourceTabs
import com.example.ai_assis.presentation.ui.components.sourceTabFromKey
import com.example.ai_assis.presentation.viewmodel.DashboardEvent
import com.example.ai_assis.presentation.viewmodel.HomeViewModel
import com.example.ai_assis.service.DirectReplyRegistry
import com.example.ai_assis.service.NotificationEventBus
import com.example.ai_assis.util.PRIVACY_POLICY_URL
import com.example.ai_assis.util.PermissionUtils
import com.example.ai_assis.util.openExternalUrl

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit,
    onOpenAppFilter: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onOpenImeSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTone = uiState.selectedTone
    val chatHistory = uiState.chatHistory
    val overlayMeta = uiState.overlayMeta

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotification by remember { mutableStateOf(PermissionUtils.hasNotificationAccess(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }
    var selectedSourceTabKey by rememberSaveable { mutableStateOf(SourceTab.All.key) }
    val selectedSourceTab = remember(selectedSourceTabKey) { sourceTabFromKey(selectedSourceTabKey) }
    val filteredHistory = remember(chatHistory, selectedSourceTab) {
        val selectedPackage = selectedSourceTab.packageName
        if (selectedPackage == null) {
            chatHistory
        } else {
            chatHistory.filter { it.chatMessage.appSource == selectedPackage }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotification = PermissionUtils.hasNotificationAccess(context)
                hasOverlay = PermissionUtils.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            AssistantStatusCard(
                isRunning = overlayMeta.isServiceRunning,
                mode = overlayMeta.mode,
                unreadCount = overlayMeta.unreadCount,
                onStart = onStartOverlayService,
                onStop = onStopOverlayService,
            )
        }

        item {
            PermissionStatusCard(
                hasNotification = hasNotification,
                hasOverlay = hasOverlay,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenOverlayPermission = onOpenOverlayPermission,
            )
        }

        item {
            ToneSelectorCard(
                selectedTone = selectedTone,
                onToneSelected = { viewModel.onEvent(DashboardEvent.ToneSelected(it)) },
            )
        }

        item {
            OutlinedButton(onClick = onOpenAppFilter, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = "Monitored apps")
                Text("  Monitored Apps")
            }
        }

        item {
            OutlinedButton(
                onClick = onOpenSuggestions,
                modifier = Modifier.fillMaxWidth(),
                enabled = overlayMeta.isServiceRunning,
            ) {
                Text("AI Suggestions")
            }
        }

        item {
            OutlinedButton(onClick = onOpenImeSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = "Enable AI keyboard")
                Text("  Enable AI Keyboard (Beta)")
            }
        }

        item {
            TextButton(
                onClick = { openExternalUrl(context, PRIVACY_POLICY_URL) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Privacy Policy")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chat Window", style = MaterialTheme.typography.titleLarge)
                if (chatHistory.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onEvent(DashboardEvent.ClearHistoryClicked) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Clear history",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Text(
                text = "Each incoming message shows its own suggested replies.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SourceTabsRow(
                tabs = defaultSourceTabs,
                selectedTab = selectedSourceTab,
                onTabSelected = { selectedSourceTabKey = it.key },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
            )
        }

        if (filteredHistory.isEmpty()) {
            item {
                if (overlayMeta.errorMessage != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = overlayMeta.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = "Retry last request",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { NotificationEventBus.retryLastFailedRequest() },
                        )
                    }
                } else {
                    Text(
                        text = if (overlayMeta.isLoading) {
                            "Generating suggestions..."
                        } else if (selectedSourceTab == SourceTab.All) {
                            "No suggestions yet. Enable the assistant, then receive a new message from a monitored app."
                        } else {
                            "No suggestions yet for ${selectedSourceTab.title}."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(filteredHistory, key = { it.createdAtMs }) { item ->
                MessageSuggestionCard(
                    item = item,
                    onReplyClick = { reply -> copyReply(context, reply) },
                    onSendReply = { reply, actionKey -> sendDirectReply(context, reply, actionKey) },
                )
            }
        }
    }
}

@Composable
private fun AssistantStatusCard(
    isRunning: Boolean,
    mode: NotificationEventBus.OverlayMode,
    unreadCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val successColor = MaterialTheme.colorScheme.primary
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) successColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isRunning) successColor else MaterialTheme.colorScheme.error,
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = if (isRunning) "  Assistant: Running" else "  Assistant: Stopped",
                    color = if (isRunning) successColor else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isRunning) {
                Text(
                    text = "Mode: ${mode.name} ? Unread: $unreadCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = !isRunning, modifier = Modifier.weight(1f)) {
                    Text("Enable")
                }
                Button(
                    onClick = onStop,
                    enabled = isRunning,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Disable")
                }
            }
        }
    }
}

@Composable
private fun PermissionStatusCard(
    hasNotification: Boolean,
    hasOverlay: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Permissions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            PermissionStatusRow("Notification Access", hasNotification, onOpenNotificationAccess)
            PermissionStatusRow("Overlay Permission", hasOverlay, onOpenOverlayPermission)
        }
    }
}

@Composable
private fun PermissionStatusRow(label: String, isGranted: Boolean, onFix: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
        }
        if (!isGranted) {
            TextButton(onClick = onFix) { Text("Fix", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ToneSelectorCard(selectedTone: ReplyTone, onToneSelected: (ReplyTone) -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reply Tone", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReplyTone.entries.forEach { tone ->
                    val isSelected = tone == selectedTone
                    OutlinedButton(
                        onClick = { onToneSelected(tone) },
                        colors = if (isSelected) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (isSelected) "? ${tone.displayName}" else tone.displayName,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageSuggestionCard(
    item: NotificationEventBus.ChatSuggestionItem,
    onReplyClick: (String) -> Unit,
    onSendReply: (String, String?) -> Unit,
) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppSourceIcon(
                packageName = item.chatMessage.appSource,
                iconRes = appIconResFor(item.chatMessage.appSource),
                fallbackLabel = appDisplayLabelFor(item.chatMessage.appSource).take(1),
            )
            Text(
                text = item.chatMessage.sender,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "- ${appDisplayLabelFor(item.chatMessage.appSource)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = item.chatMessage.message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Source: ${item.source.name}${item.fallbackReason?.let { " ? ${toFallbackLabel(it)}" } ?: ""}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        item.replies.forEach { reply ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { onReplyClick(reply) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = reply,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TextButton(onClick = { onSendReply(reply, item.chatMessage.replyActionKey) }) {
                    Text("Send")
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

private fun copyReply(context: Context, reply: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("reply", reply))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun sendDirectReply(context: Context, reply: String, actionKey: String?) {
    if (actionKey == null) {
        copyReply(context, reply)
        return
    }
    val action = DirectReplyRegistry.get(actionKey)
    if (action == null) {
        copyReply(context, reply)
        return
    }
    val remoteInput = action.remoteInputs?.firstOrNull()
    if (remoteInput == null) {
        copyReply(context, reply)
        return
    }
    try {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RemoteInput.addResultsToIntent(
                arrayOf(remoteInput),
                intent,
                Bundle().apply { putCharSequence(remoteInput.resultKey, reply) },
            )
        }
        action.actionIntent.send(context, 0, intent)
        Toast.makeText(context, "Reply sent!", Toast.LENGTH_SHORT).show()
        DirectReplyRegistry.remove(actionKey)
    } catch (_: PendingIntent.CanceledException) {
        copyReply(context, reply)
    }
}
