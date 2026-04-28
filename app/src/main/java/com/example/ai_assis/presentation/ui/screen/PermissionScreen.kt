package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionScreen(
    onGrantNotificationAccess: () -> Unit,
    onGrantOverlayAccess: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Smart AI Chat Assistant", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "We read notifications only to suggest replies. We do not store your chats.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onGrantNotificationAccess) {
            Text("Enable notification access")
        }
        Button(onClick = onGrantOverlayAccess) {
            Text("Enable overlay permission")
        }
        Button(onClick = onContinue) {
            Text("Continue")
        }
    }
}
