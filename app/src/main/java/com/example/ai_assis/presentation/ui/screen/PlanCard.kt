package com.example.ai_assis.presentation.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.ai_assis.R

private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun PlanCard(
    plan: PlanUiModel,
    selected: Boolean,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(220),
        label = "planCardBorder",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.65f),
        animationSpec = tween(220),
        label = "planCardBorderColor",
    )
    val backgroundColor by animateColorAsState(
        targetValue = when {
            selected -> scheme.primary.copy(alpha = 0.10f)
            plan.isFeatured -> scheme.primaryContainer.copy(alpha = 0.35f)
            else -> scheme.surface.copy(alpha = 0.42f)
        },
        animationSpec = tween(220),
        label = "planCardBg",
    )

    val targetScale = when {
        selected && plan.isFeatured -> 1.03f
        plan.isFeatured -> 1.02f
        selected -> 1.01f
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(220),
        label = "planCardScale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 3.dp,
                shape = CardShape,
                ambientColor = scheme.scrim.copy(alpha = 0.07f),
                spotColor = scheme.scrim.copy(alpha = 0.09f),
            )
            .clip(CardShape)
            .border(borderWidth, borderColor, CardShape)
            .background(backgroundColor)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = planTitle(plan),
                style = typography.titleMedium,
                color = scheme.onSurface,
            )
            when {
                active -> {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.tertiaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.pricing_badge_active),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = typography.labelMedium,
                            color = scheme.onTertiaryContainer,
                        )
                    }
                }
                plan.isFeatured -> {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.secondaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.pricing_badge_most_popular),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = typography.labelMedium,
                            color = scheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        PlanPriceBlock(plan = plan)

        val features = plan.features
        if (features.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                features.forEach { line ->
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = scheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = line,
                            style = typography.bodyMedium,
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun planTitle(plan: PlanUiModel): String = plan.title

@Composable
private fun PlanPriceBlock(plan: PlanUiModel) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = plan.priceText,
            style = typography.titleSmall,
            color = scheme.onSurface,
        )
        Text(
            text = plan.subtitle,
            style = typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}
