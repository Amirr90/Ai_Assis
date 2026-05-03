package com.example.ai_assis.presentation.ui.flow.components

import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty

/**
 * Circular Lottie hero matching onboarding / permission flow styling.
 */
@Composable
fun FlowLottieIllustration(
    @RawRes lottieRes: Int,
    modifier: Modifier = Modifier,
    circleSize: Dp = 200.dp,
    lottieSize: Dp = 176.dp,
    cacheKeySuffix: String = "flow",
    /** Light blue–white disc + primary ring for the privacy onboarding slide. */
    privacyChrome: Boolean = false,
) {
    val composition by rememberLottieComposition(
        spec = LottieCompositionSpec.RawRes(lottieRes),
        cacheKey = "${cacheKeySuffix}_${lottieRes}_pc$privacyChrome",
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = composition != null,
        iterations = LottieConstants.IterateForever,
        speed = 1f,
    )
    val scheme = MaterialTheme.colorScheme
    val dynamicProperties = if (privacyChrome) {
        rememberLottieDynamicProperties(
            rememberLottieDynamicProperty(
                property = LottieProperty.COLOR,
                value = scheme.primary.toArgb(),
                "**",
            ),
        )
    } else {
        null
    }
    Box(
        modifier = modifier
            .size(circleSize)
            .clip(CircleShape)
            .background(
                if (privacyChrome) {
                    scheme.surface.copy(alpha = 0.94f)
                } else {
                    scheme.surfaceContainerLow.copy(alpha = 0.35f)
                },
            )
            .border(
                width = 2.dp,
                color = if (privacyChrome) {
                    scheme.primary.copy(alpha = 0.52f)
                } else {
                    scheme.primary.copy(alpha = 0.42f)
                },
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(lottieSize),
            contentScale = ContentScale.Fit,
            alignment = Alignment.Center,
            dynamicProperties = dynamicProperties,
        )
    }
}
