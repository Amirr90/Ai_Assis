package com.example.ai_assis.presentation.ui.flow.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity

@Composable
fun FlowBackground(
    modifier: Modifier = Modifier,
    decorationSeed: Int = 0,
    premiumLightGradient: Boolean = false,
    /** Cool blue–white gradient for privacy-style onboarding (when [premiumLightGradient] is true). */
    privacyCoolBlue: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val gradient = if (premiumLightGradient) {
        val infiniteTransition = rememberInfiniteTransition(label = "premiumFlowBg")
        val phase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(10_000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "premiumPhase",
        )
        val top: Color
        val bottom: Color
        val midWeight: Float
        if (privacyCoolBlue) {
            top = Color(0xFFFDFEFF)
            bottom = Color(0xFFD7E8FF)
            midWeight = 0.40f + 0.12f * phase
        } else {
            top = Color(0xFFF8FAFC)
            bottom = Color(0xFFEEF2FF)
            midWeight = 0.42f + 0.14f * phase
        }
        val mid = lerp(top, bottom, midWeight)
        Brush.verticalGradient(colors = listOf(top, mid, bottom))
    } else {
        Brush.verticalGradient(
            colors = listOf(
                scheme.surfaceContainerLowest,
                scheme.primaryContainer.copy(alpha = 0.14f),
                scheme.surfaceContainerLow.copy(alpha = 0.35f),
                scheme.surfaceContainerLowest,
            ),
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(gradient),
        )
        FlowAmbientDecoration(seed = decorationSeed)
        Box(Modifier.fillMaxSize(), content = content)
    }
}

@Composable
private fun FlowAmbientDecoration(seed: Int) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val accent = when (seed % 3) {
        0 -> scheme.primary
        1 -> scheme.tertiary
        else -> scheme.secondary
    }
    val outlineSoft = scheme.outline.copy(alpha = 0.14f)
    val dash = Stroke(
        width = 1.2f * density.density,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), phase = seed * 7f),
    )

    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Soft focal blobs (page-tinted)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.11f), Color.Transparent),
                center = Offset(w * 0.82f, h * 0.14f),
                radius = w * 0.42f,
            ),
            radius = w * 0.42f,
            center = Offset(w * 0.82f, h * 0.14f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(scheme.secondary.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(w * 0.12f, h * 0.72f),
                radius = h * 0.38f,
            ),
            radius = h * 0.38f,
            center = Offset(w * 0.12f, h * 0.72f),
        )

        // Corner arc (abstract “interface” curve)
        drawArc(
            color = outlineSoft,
            startAngle = 200f,
            sweepAngle = 110f,
            useCenter = false,
            topLeft = Offset(-w * 0.08f, h * 0.38f),
            size = Size(w * 0.55f, w * 0.55f),
            style = dash,
        )

        // Bottom rhythm dots (light grid feel)
        val step = (w / 14f).coerceIn(28f, 48f)
        var x = w * 0.06f
        val y = h * 0.92f
        while (x < w * 0.94f) {
            drawCircle(
                color = scheme.outlineVariant.copy(alpha = 0.18f),
                radius = 2f,
                center = Offset(x, y),
            )
            x += step
        }

        // Subtle wave stroke (single Bezier)
        val wave = Path().apply {
            moveTo(w * -0.02f, h * 0.42f)
            quadraticBezierTo(w * 0.35f, h * 0.36f, w * 0.72f, h * 0.48f)
            quadraticBezierTo(w * 0.92f, h * 0.55f, w * 1.04f, h * 0.44f)
        }
        drawPath(
            path = wave,
            color = accent.copy(alpha = 0.12f),
            style = Stroke(width = 1.4f * density.density),
        )

        // Tiny rounded frame hint (top area)
        drawRoundRect(
            color = scheme.primary.copy(alpha = 0.06f),
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.22f),
            cornerRadius = CornerRadius(48f, 48f),
            style = Stroke(width = 1f * density.density),
        )
    }
}
