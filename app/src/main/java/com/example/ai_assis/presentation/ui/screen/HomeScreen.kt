package com.example.ai_assis.presentation.ui.screen

import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.os.Build
import android.os.Bundle
import android.app.RemoteInput
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
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
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ai_assis.R
import com.example.ai_assis.domain.model.ReplyLength
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
import com.example.ai_assis.service.OutgoingMessageSuppressor
import com.example.ai_assis.util.DATA_USE_DISCLOSURE_URL
import com.example.ai_assis.util.PRIVACY_POLICY_URL
import com.example.ai_assis.util.PermissionUtils
import com.example.ai_assis.util.openExternalUrl
import androidx.compose.ui.res.stringResource
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ai_assis.worker.ReplyReminderWorker

private enum class DashboardTab {
    Overview,
    Activity,
    Settings,
}

private val DashboardCardShape = RoundedCornerShape(16.dp)

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
    val context = LocalContext.current
    val selectedTone = uiState.selectedTone
    val selectedLength = uiState.replyLength
    val aiEnabled = uiState.aiEnabled
    val chatHistory = uiState.chatHistory
    val overlayMeta = uiState.overlayMeta
    var templateInput by rememberSaveable { mutableStateOf("") }
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            .orEmpty()
        if (spoken.isNotBlank()) {
            viewModel.onEvent(DashboardEvent.AddTemplate(spoken))
            Toast.makeText(context, context.getString(R.string.dashboard_voice_template_saved), Toast.LENGTH_SHORT).show()
        }
    }

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

    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.Overview) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.Overview,
                    onClick = { selectedTab = DashboardTab.Overview },
                    icon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = stringResource(R.string.dashboard_tab_overview),
                        )
                    },
                    label = { Text(stringResource(R.string.dashboard_tab_overview)) },
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.Activity,
                    onClick = { selectedTab = DashboardTab.Activity },
                    icon = {
                        Icon(
                            Icons.Default.List,
                            contentDescription = stringResource(R.string.dashboard_tab_activity),
                        )
                    },
                    label = { Text(stringResource(R.string.dashboard_tab_activity)) },
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.Settings,
                    onClick = { selectedTab = DashboardTab.Settings },
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.dashboard_tab_settings),
                        )
                    },
                    label = { Text(stringResource(R.string.dashboard_tab_settings)) },
                )
            }
        },
    ) { innerPadding ->
        val tabModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp)

        when (selectedTab) {
            DashboardTab.Overview -> HomeOverviewTab(
                modifier = tabModifier,
                overlayMeta = overlayMeta,
                hasNotification = hasNotification,
                hasOverlay = hasOverlay,
                onOpenNotificationAccess = onOpenNotificationAccess,
                onOpenOverlayPermission = onOpenOverlayPermission,
                onStartOverlayService = onStartOverlayService,
                onStopOverlayService = onStopOverlayService,
                onOpenAppFilter = onOpenAppFilter,
                onOpenSuggestions = onOpenSuggestions,
                onOpenImeSettings = onOpenImeSettings,
            )

            DashboardTab.Activity -> HomeActivityTab(
                modifier = tabModifier,
                overlayMeta = overlayMeta,
                chatHistory = chatHistory,
                filteredHistory = filteredHistory,
                selectedSourceTab = selectedSourceTab,
                onSourceTabSelected = { tab -> selectedSourceTabKey = tab.key },
                viewModel = viewModel,
                context = context,
            )

            DashboardTab.Settings -> HomeSettingsTab(
                modifier = tabModifier,
                selectedTone = selectedTone,
                onToneSelected = { viewModel.onEvent(DashboardEvent.ToneSelected(it)) },
                aiEnabled = aiEnabled,
                selectedLength = selectedLength,
                onAiToggle = { viewModel.onEvent(DashboardEvent.AiToggled(it)) },
                onLengthSelect = { viewModel.onEvent(DashboardEvent.ReplyLengthSelected(it)) },
                templateInput = templateInput,
                onTemplateInputChange = { templateInput = it },
                templates = uiState.templates,
                onAddTemplate = {
                    viewModel.onEvent(DashboardEvent.AddTemplate(templateInput))
                    templateInput = ""
                },
                onDeleteTemplate = { id -> viewModel.onEvent(DashboardEvent.RemoveTemplate(id)) },
                onVoiceClick = {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    }
                    voiceLauncher.launch(intent)
                },
                context = context,
            )
        }
    }
}

@Composable
private fun HomeOverviewTab(
    modifier: Modifier,
    overlayMeta: NotificationEventBus.OverlayMetaState,
    hasNotification: Boolean,
    hasOverlay: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit,
    onOpenAppFilter: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onOpenImeSettings: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onOpenAppFilter, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.dashboard_monitored_apps))
                    Text("  ${stringResource(R.string.dashboard_monitored_apps)}")
                }
                OutlinedButton(
                    onClick = onOpenSuggestions,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = overlayMeta.isServiceRunning,
                ) {
                    Text(stringResource(R.string.dashboard_ai_suggestions))
                }
                OutlinedButton(onClick = onOpenImeSettings, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.dashboard_enable_ai_keyboard_beta))
                    Text("  ${stringResource(R.string.dashboard_enable_ai_keyboard_beta)}")
                }
                Text(
                    text = stringResource(R.string.dashboard_enable_ai_keyboard_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HomeActivityTab(
    modifier: Modifier,
    overlayMeta: NotificationEventBus.OverlayMetaState,
    chatHistory: List<NotificationEventBus.ChatSuggestionItem>,
    filteredHistory: List<NotificationEventBus.ChatSuggestionItem>,
    selectedSourceTab: SourceTab,
    onSourceTabSelected: (SourceTab) -> Unit,
    viewModel: HomeViewModel,
    context: Context,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.dashboard_chat_window), style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { NotificationEventBus.regenerateLastRequest() },
                        enabled = overlayMeta.isServiceRunning,
                    ) {
                        Text(stringResource(R.string.dashboard_more_options))
                    }
                    if (chatHistory.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(DashboardEvent.ClearHistoryClicked) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.dashboard_clear_history),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            Text(
                text = stringResource(R.string.dashboard_chat_window_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SourceTabsRow(
                tabs = defaultSourceTabs,
                selectedTab = selectedSourceTab,
                onTabSelected = onSourceTabSelected,
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
                            .clip(DashboardCardShape)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = userSafeErrorText(overlayMeta.errorMessage),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = stringResource(R.string.dashboard_retry_last_request),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { NotificationEventBus.retryLastFailedRequest() },
                        )
                    }
                } else {
                    Text(
                        text = if (overlayMeta.isLoading) {
                            stringResource(R.string.dashboard_generating_suggestions)
                        } else if (selectedSourceTab == SourceTab.All) {
                            stringResource(R.string.dashboard_no_suggestions_all)
                        } else {
                            stringResource(R.string.dashboard_no_suggestions_for_source, selectedSourceTab.title)
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
                    onReplyLater = {
                        scheduleReplyReminder(context, item.chatMessage.sender, appDisplayLabelFor(item.chatMessage.appSource))
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeSettingsTab(
    modifier: Modifier,
    selectedTone: ReplyTone,
    onToneSelected: (ReplyTone) -> Unit,
    aiEnabled: Boolean,
    selectedLength: ReplyLength,
    onAiToggle: (Boolean) -> Unit,
    onLengthSelect: (ReplyLength) -> Unit,
    templateInput: String,
    onTemplateInputChange: (String) -> Unit,
    templates: List<com.example.ai_assis.domain.model.CustomTemplate>,
    onAddTemplate: () -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onVoiceClick: () -> Unit,
    context: Context,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ToneSelectorCard(selectedTone = selectedTone, onToneSelected = onToneSelected)
        }
        item {
            ReplyBehaviorCard(
                aiEnabled = aiEnabled,
                selectedLength = selectedLength,
                onAiToggle = onAiToggle,
                onLengthSelect = onLengthSelect,
            )
        }
        item {
            VoiceAndTemplateCard(
                templateInput = templateInput,
                onTemplateInputChange = onTemplateInputChange,
                templates = templates,
                onAddTemplate = onAddTemplate,
                onDeleteTemplate = onDeleteTemplate,
                onVoiceClick = onVoiceClick,
            )
        }
        item {
            Card(
                shape = DashboardCardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_coming_soon),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    DashboardBacklogLine(stringResource(R.string.dashboard_feature_voice_input))
                    DashboardBacklogLine(stringResource(R.string.dashboard_feature_summarization))
                    DashboardBacklogLine(stringResource(R.string.dashboard_feature_personalization))
                    DashboardBacklogLine(stringResource(R.string.dashboard_feature_reply_later))
                    DashboardBacklogLine(stringResource(R.string.dashboard_feature_notification_actions))
                    DashboardBacklogLine(stringResource(R.string.dashboard_feature_widget))
                    Text(
                        text = stringResource(R.string.dashboard_backlog_cta),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { openExternalUrl(context, PRIVACY_POLICY_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_privacy_policy))
                }
                TextButton(onClick = { openExternalUrl(context, DATA_USE_DISCLOSURE_URL) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.dashboard_data_use_disclosure))
                }
                Text(
                    text = stringResource(R.string.dashboard_data_use_disclosure_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReplyBehaviorCard(
    aiEnabled: Boolean,
    selectedLength: ReplyLength,
    onAiToggle: (Boolean) -> Unit,
    onLengthSelect: (ReplyLength) -> Unit,
) {
    Card(
        shape = DashboardCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.dashboard_ai_suggestions_toggle), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Switch(checked = aiEnabled, onCheckedChange = onAiToggle)
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplyLength.entries.forEach { length ->
                    OutlinedButton(onClick = { onLengthSelect(length) }) { Text(length.displayName) }
                }
            }
        }
    }
}

@Composable
private fun VoiceAndTemplateCard(
    templateInput: String,
    onTemplateInputChange: (String) -> Unit,
    templates: List<com.example.ai_assis.domain.model.CustomTemplate>,
    onAddTemplate: () -> Unit,
    onDeleteTemplate: (String) -> Unit,
    onVoiceClick: () -> Unit,
) {
    Card(
        shape = DashboardCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.dashboard_custom_templates), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = templateInput,
                onValueChange = onTemplateInputChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.dashboard_template_placeholder)) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAddTemplate) { Text(stringResource(R.string.dashboard_save_template)) }
                OutlinedButton(onClick = onVoiceClick) { Text(stringResource(R.string.dashboard_voice_to_template)) }
            }
            templates.take(5).forEach { template ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(template.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onDeleteTemplate(template.id) }) { Text(stringResource(R.string.dashboard_remove)) }
                }
            }
        }
    }
}

@Composable
private fun DashboardBacklogLine(label: String) {
    Text(
        text = "- $label (${stringResource(R.string.dashboard_feature_coming_soon_suffix)})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun AnimatedAssistantToggle(
    isRunning: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val pad = 4.dp
        val thumbW = (maxWidth - pad * 3) / 2
        val thumbTravel = maxWidth - thumbW - pad * 2
        val thumbX by animateDpAsState(
            targetValue = if (isRunning) thumbTravel else pad,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioMediumBouncy,
            ),
            label = "assistant_thumb",
        )
        val thumbVertical = 44.dp
        Box(
            modifier = Modifier
                .offset(x = thumbX, y = pad)
                .width(thumbW)
                .height(thumbVertical)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        )
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = !isRunning) { onEnable() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_enable),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(enabled = isRunning) { onDisable() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_disable),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        shape = DashboardCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (isRunning) {
            BorderStroke(1.dp, successColor.copy(alpha = 0.38f))
        } else {
            null
        },
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    text = if (isRunning) {
                        "  ${stringResource(R.string.dashboard_assistant_running)}"
                    } else {
                        "  ${stringResource(R.string.dashboard_assistant_stopped)}"
                    },
                    color = if (isRunning) successColor else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isRunning) {
                Text(
                    text = stringResource(R.string.dashboard_mode_unread, mode.name, unreadCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedAssistantToggle(
                isRunning = isRunning,
                onEnable = onStart,
                onDisable = onStop,
            )
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
        shape = DashboardCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.dashboard_permissions),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            PermissionStatusRow(stringResource(R.string.dashboard_permission_notification_access), hasNotification, onOpenNotificationAccess)
            PermissionStatusRow(stringResource(R.string.dashboard_permission_overlay), hasOverlay, onOpenOverlayPermission)
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
            TextButton(onClick = onFix) { Text(stringResource(R.string.dashboard_fix), style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun ToneSelectorCard(selectedTone: ReplyTone, onToneSelected: (ReplyTone) -> Unit) {
    Card(
        shape = DashboardCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.dashboard_reply_tone),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
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
                            text = if (isSelected) {
                                stringResource(R.string.dashboard_tone_selected, tone.displayName)
                            } else {
                                tone.displayName
                            },
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
    onReplyLater: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardCardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.chatMessage.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = appDisplayLabelFor(item.chatMessage.appSource),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            Text(
                text = item.chatMessage.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    R.string.dashboard_source_metadata,
                    item.source.name,
                    buildMetadataSuffix(
                        fallbackReason = item.fallbackReason,
                        isSummaryNotification = item.chatMessage.isSummaryNotification,
                    ),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.replies.forEach { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { onReplyClick(reply) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = reply,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        TextButton(
                            onClick = { onSendReply(reply, item.chatMessage.replyActionKey) },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(
                                stringResource(R.string.dashboard_reply_send),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = onReplyLater,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.secondary,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            ) {
                Text(stringResource(R.string.dashboard_reply_after_10m))
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
        "ai_disabled" -> stringResource(R.string.dashboard_fallback_ai_disabled)
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

private fun copyReply(context: Context, reply: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("reply", reply))
    Toast.makeText(context, context.getString(R.string.dashboard_copied_to_clipboard), Toast.LENGTH_SHORT).show()
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
    val remoteInput = action.remoteInputs?.firstOrNull { it.allowFreeFormInput }
        ?: action.remoteInputs?.firstOrNull()
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
        actionKey.substringBefore('_', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.let { packageName ->
                OutgoingMessageSuppressor.registerOutgoing(packageName = packageName, text = reply)
            }
        Toast.makeText(context, context.getString(R.string.dashboard_reply_sent), Toast.LENGTH_SHORT).show()
        DirectReplyRegistry.remove(actionKey)
    } catch (_: PendingIntent.CanceledException) {
        copyReply(context, reply)
    }
}

private fun scheduleReplyReminder(context: Context, sender: String, appLabel: String) {
    val work = OneTimeWorkRequestBuilder<ReplyReminderWorker>()
        .setInitialDelay(10, TimeUnit.MINUTES)
        .setInputData(
            Data.Builder()
                .putString("sender", sender)
                .putString("app_label", appLabel)
                .build(),
        )
        .build()
    WorkManager.getInstance(context).enqueue(work)
    Toast.makeText(context, context.getString(R.string.dashboard_reminder_set), Toast.LENGTH_SHORT).show()
}
