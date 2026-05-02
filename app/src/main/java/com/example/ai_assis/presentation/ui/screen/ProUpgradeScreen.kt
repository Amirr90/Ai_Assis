package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.ai_assis.ui.theme.AI_AssisTheme

@Composable
fun ProUpgradeScreen(
    onUpgrade: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Surface(
        modifier = modifier.fillMaxSize(),
        color = colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 32.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Never get stuck typing again ⚡",
                style = typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Unlock unlimited smart replies",
                style = typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(20.dp),
                        spotColor = colorScheme.scrim.copy(alpha = 0.08f),
                        ambientColor = colorScheme.scrim.copy(alpha = 0.06f),
                    ),
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = "🔥 Most popular",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            style = typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onPrimaryContainer,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    ProBenefitRow(
                        icon = Icons.Filled.Chat,
                        label = "Never run out of replies",
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ProBenefitRow(
                        icon = Icons.Filled.FlashOn,
                        label = "Reply instantly without thinking",
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ProBenefitRow(
                        icon = Icons.Filled.Tune,
                        label = "Sound smarter in every chat",
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ProBenefitRow(
                        icon = Icons.Filled.TrendingUp,
                        label = "Save time every day",
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    PricingBlock()
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onUpgrade,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Unlock Pro 🚀",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TextButton(onClick = onDismiss) {
                Text(
                    text = "Maybe later",
                    style = typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProBenefitRow(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PricingBlock(modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Only ₹3/day",
            style = typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Billed ₹99/month",
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Cancel anytime • No hidden charges",
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ProUpgradeScreenPreview() {
    AI_AssisTheme {
        ProUpgradeScreen(onUpgrade = {}, onDismiss = {})
    }
}
