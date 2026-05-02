package com.example.ai_assis.presentation.ui.flow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.FlowScaffold
import com.example.ai_assis.presentation.ui.flow.components.OutlinedFlowButton
import com.example.ai_assis.presentation.ui.flow.components.TextFlowButton

@Composable
fun LoginScreen(
    onContinueWithGoogle: () -> Unit,
    onSkip: () -> Unit,
) {
    FlowScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                OutlinedFlowButton(
                    text = stringResource(R.string.flow_login_google),
                    onClick = onContinueWithGoogle,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextFlowButton(
                    text = stringResource(R.string.flow_login_skip),
                    onClick = onSkip,
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
                imageVector = Icons.Outlined.AccountCircle,
                contentDescription = null,
                modifier = Modifier.padding(bottom = 24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.flow_login_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
        }
    }
}
