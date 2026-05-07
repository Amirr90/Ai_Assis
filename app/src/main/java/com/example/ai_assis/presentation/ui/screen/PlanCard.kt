package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai_assis.R

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun PlanCard(
    plan: PlanUiModel,
    selected: Boolean,
    active: Boolean,
    isLoading: Boolean,
    onSelect: () -> Unit,
    onBuyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val style = planCardStyleFor(plan.pricingPlan)
    val ctaText = if (plan.pricingPlan.isPaid()) "BUY NOW" else stringResource(R.string.pricing_cta_continue)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            ),
        shape = CardShape,
        color = Color.White,
        tonalElevation = if (selected) 8.dp else 3.dp,
        shadowElevation = if (selected) 10.dp else 4.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(brush = style.headerBrush)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = plan.title.uppercase(),
                            style = typography.labelLarge,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (active) {
                            Text(
                                text = stringResource(R.string.pricing_badge_active),
                                style = typography.labelSmall,
                                color = Color.White.copy(alpha = 0.96f),
                            )
                        }
                    }
                    Text(
                        text = plan.priceText,
                        style = typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = plan.subtitle,
                        style = typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val features = if (plan.features.isNotEmpty()) {
                    plan.features
                } else {
                    listOf("Flexible usage", "No hidden fees", "Works instantly")
                }
                features.forEach { line ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(scheme.primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = scheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = line,
                            style = typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Button(
                    onClick = onBuyClick,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = if (selected) 1.dp else 0.dp,
                            color = if (selected) scheme.primary.copy(alpha = 0.45f) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp),
                        ),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = style.buttonColor,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(text = ctaText, style = typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private data class PlanCardStyle(
    val headerBrush: Brush,
    val buttonColor: Color,
)

@Composable
private fun planCardStyleFor(plan: PricingPlan): PlanCardStyle = when (plan) {
    PricingPlan.Free -> PlanCardStyle(
        headerBrush = Brush.linearGradient(colors = listOf(Color(0xFFFF7A59), Color(0xFFFFC837))),
        buttonColor = Color(0xFFFF8A3D),
    )
    PricingPlan.Monthly -> PlanCardStyle(
        headerBrush = Brush.linearGradient(colors = listOf(Color(0xFF13B0F5), Color(0xFF00DFA2))),
        buttonColor = Color(0xFF00B894),
    )
    PricingPlan.Yearly -> PlanCardStyle(
        headerBrush = Brush.linearGradient(colors = listOf(Color(0xFF5F27CD), Color(0xFFB337F2))),
        buttonColor = Color(0xFF6C5CE7),
    )
    PricingPlan.Credits -> PlanCardStyle(
        headerBrush = Brush.linearGradient(colors = listOf(Color(0xFF0ABDE3), Color(0xFF48DBFB))),
        buttonColor = Color(0xFF0984E3),
    )
    PricingPlan.Test -> PlanCardStyle(
        headerBrush = Brush.linearGradient(colors = listOf(Color(0xFF2D3436), Color(0xFF636E72))),
        buttonColor = Color(0xFF2D3436),
    )
}
