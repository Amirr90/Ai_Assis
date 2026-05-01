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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import com.example.ai_assis.R
import com.example.ai_assis.domain.model.appDisplayLabelFor
import com.example.ai_assis.presentation.ui.components.AppSourceIcon
import com.example.ai_assis.presentation.ui.components.SourceTab
import com.example.ai_assis.presentation.ui.components.SourceTabsRow
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
                onRegenerateSuggestion = onRegenerateSuggestion,
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
    onCollapse: () -> Unit,
    onClear: () -> Unit,
    onToggleUpdates: () -> Unit,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, ChatMessage) -> Unit,
    onRegenerateSuggestion: (ChatMessage) -> Boolean,
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
    val configuration = LocalConfiguration.current
    // Cap the suggestions list at the smaller of 420dp and 60% of screen height
    // so the panel fits within the device viewport even on short screens or
    // when the IME is open.
    val listMaxHeight = remember(configuration.screenHeightDp) {
        val sixtyPercent = (configuration.screenHeightDp * 0.6f).dp
        if (sixtyPercent < 420.dp) sixtyPercent else 420.dp
    }
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
                text = stringResource(R.string.dashboard_overlay_title),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
            )
            Row {
                IconButton(onClick = onToggleUpdates) {
                    if (updatesPaused) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.dashboard_overlay_resume_updates),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
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
                Text(
                    text = stringResource(R.string.dashboard_retry_last_request),
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .heightIn(max = listMaxHeight)
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
                    isLoading = isLoading,
                    onReplyClick = onReplyClick,
                    onDirectSend = onDirectSend,
                    onRegenerateSuggestion = onRegenerateSuggestion,
                )
            }
        }
    }
}

@Composable
private fun SuggestionCard(
    entry: NotificationEventBus.ChatSuggestionItem,
    isLoading: Boolean,
    onReplyClick: (String) -> Unit,
    onDirectSend: (String, ChatMessage) -> Unit,
    onRegenerateSuggestion: (ChatMessage) -> Boolean,
) {
    var activeSendKey by remember(entry.id) { mutableStateOf<String?>(null) }
    var isMessageExpanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    var isMessageOverflowing by rememberSaveable(entry.id) { mutableStateOf(false) }
    var editableReply by remember(entry.id) { mutableStateOf<String?>(null) }
    var editedReplyText by remember(entry.id) { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val cardShape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
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
        entry.replies.forEach { reply ->
            val isEditingThisReply = editableReply == reply
            val replyRowShape = RoundedCornerShape(10.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(replyRowShape)
                    .background(MaterialTheme.colorScheme.primaryContainer, replyRowShape)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = reply,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onReplyClick(reply) },
                )
                Spacer(Modifier.width(6.dp))
                SmallOverlayIconButton(
                    onClick = {
                        editableReply = reply
                        editedReplyText = reply
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.dashboard_overlay_edit_suggestion),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                if (entry.chatMessage.replyActionKey != null) {
                    val sendKey = "${entry.id}:$reply"
                    val isSending = activeSendKey == sendKey
                    SmoothSendButton(
                        isSending = isSending,
                        onClick = {
                            if (isSending) return@SmoothSendButton
                            activeSendKey = sendKey
                            onDirectSend(reply, entry.chatMessage)
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
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale),
    ) {
        if (isSending) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = stringResource(R.string.dashboard_overlay_send_directly),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
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
