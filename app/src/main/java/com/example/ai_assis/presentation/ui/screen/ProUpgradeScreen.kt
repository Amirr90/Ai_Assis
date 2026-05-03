package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.ai_assis.R
import com.example.ai_assis.ui.theme.AI_AssisTheme

@Composable
fun ProUpgradeScreen(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            PricingScreen(
                modifier = Modifier.fillMaxSize(),
                onContinueOrUpgrade = onUpgrade,
            )
        }
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.pricing_maybe_later),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProUpgradeScreenPreview() {
    AI_AssisTheme {
        ProUpgradeScreen(onUpgrade = {}, onDismiss = {})
    }
}
