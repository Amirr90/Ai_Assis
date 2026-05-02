package com.example.ai_assis.presentation.ui.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.FlowScaffold
import com.example.ai_assis.presentation.ui.flow.components.PrimaryFlowButton

@Composable
fun DashboardScreen(
    onUpgrade: () -> Unit,
    onOpenFullApp: () -> Unit,
) {
    var aiEnabled by remember { mutableStateOf(true) }

    FlowScaffold(
        bottomBar = {},
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.flow_dashboard_ai_toggle),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Switch(
                        checked = aiEnabled,
                        onCheckedChange = { aiEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            Text(
                text = stringResource(R.string.flow_dashboard_usage),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrimaryFlowButton(
                text = stringResource(R.string.flow_dashboard_upgrade),
                onClick = onUpgrade,
            )

            Text(
                text = stringResource(R.string.flow_dashboard_settings_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            TextButton(onClick = onOpenFullApp) {
                Text(
                    text = stringResource(R.string.flow_dashboard_open_full),
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}
