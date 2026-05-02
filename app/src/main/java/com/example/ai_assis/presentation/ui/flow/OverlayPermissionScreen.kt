package com.example.ai_assis.presentation.ui.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.FlowScaffold
import com.example.ai_assis.presentation.ui.flow.components.PrimaryFlowButton

@Composable
fun OverlayPermissionScreen(
    onEnableOverlay: () -> Unit,
    onContinue: () -> Unit,
) {
    FlowScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                PrimaryFlowButton(
                    text = stringResource(R.string.flow_overlay_enable),
                    onClick = onEnableOverlay,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrimaryFlowButton(
                    text = stringResource(R.string.flow_overlay_next),
                    onClick = onContinue,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.Outlined.Layers,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.flow_overlay_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.flow_overlay_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
