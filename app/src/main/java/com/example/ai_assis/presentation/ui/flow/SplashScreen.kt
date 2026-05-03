package com.example.ai_assis.presentation.ui.flow

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.AnnotatedHeadline
import com.example.ai_assis.presentation.ui.flow.components.FlowBackground
import com.example.ai_assis.presentation.ui.flow.components.FlowLottieIllustration
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateNext: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(1_000)
        onNavigateNext()
    }

    val density = LocalDensity.current
    val entryAlpha = remember { Animatable(0f) }
    val entryOffsetY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entryOffsetY.snapTo(with(density) { 16.dp.toPx() })
        coroutineScope {
            launch {
                entryAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                )
            }
            launch {
                entryOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    FlowBackground(
        decorationSeed = 0,
        premiumLightGradient = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp)
                .graphicsLayer {
                    alpha = entryAlpha.value
                    translationY = entryOffsetY.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FlowLottieIllustration(
                    lottieRes = R.raw.onboarding_chat,
                    cacheKeySuffix = "splash",
                )
                Spacer(modifier = Modifier.height(24.dp))
                AnnotatedHeadline(
                    fullText = stringResource(R.string.flow_splash_brand),
                    accentPhrases = listOf(stringResource(R.string.flow_splash_brand_accent)),
                    modifier = Modifier.fillMaxWidth(),
                    titleFontSize = 22.sp,
                    letterSpacing = 0.4.sp,
                )
                Text(
                    text = stringResource(R.string.flow_splash_subtitle),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.padding(bottom = 48.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
