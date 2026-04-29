package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ai_assis.util.PRIVACY_POLICY_URL
import com.example.ai_assis.util.PermissionUtils
import com.example.ai_assis.util.openExternalUrl

@Composable
fun PermissionScreen(
    onGrantNotificationAccess: () -> Unit,
    onGrantOverlayAccess: () -> Unit,
    onContinue: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasNotification by remember { mutableStateOf(PermissionUtils.hasNotificationAccess(context)) }
    var hasOverlay by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Smart AI Chat Assistant",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "To suggest smart replies while you chat, we need two permissions below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Your Privacy is Protected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = "• We only read notification text to generate AI replies.\n" +
                        "• Your messages are never stored to disk, logged, or shared with anyone.\n" +
                        "• AI suggestions are generated in real-time and in-memory only.\n" +
                        "• You can pause or stop the assistant at any time from the bubble.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Text(
            text = "Required Permissions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        PermissionRow(
            title = "Notification Access",
            description = "Reads incoming notifications to generate smart replies. Messages are processed in memory and not stored to disk.",
            featureIcon = Icons.Default.Notifications,
            isGranted = hasNotification,
            onGrant = onGrantNotificationAccess,
        )

        PermissionRow(
            title = "Display Over Other Apps",
            description = "Shows the floating AI bubble while you are inside WhatsApp, Instagram, etc.",
            featureIcon = Icons.Default.Settings,
            isGranted = hasOverlay,
            onGrant = onGrantOverlayAccess,
        )

        if (!hasNotification || !hasOverlay) {
            Text(
                text = "Please grant both permissions to enable full functionality.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasNotification && hasOverlay,
        ) {
            Text(if (hasNotification && hasOverlay) "Continue" else "Grant Permissions Above to Continue")
        }

        if (!hasNotification || !hasOverlay) {
            TextButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Skip for now (limited functionality)")
            }
        }

        TextButton(
            onClick = { openExternalUrl(context, PRIVACY_POLICY_URL) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Privacy Policy")
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    featureIcon: androidx.compose.ui.graphics.vector.ImageVector,
    isGranted: Boolean,
    onGrant: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = featureIcon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!isGranted) {
                TextButton(onClick = onGrant) {
                    Text("Grant")
                }
            }
        }
    }
}
