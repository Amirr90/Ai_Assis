package com.example.ai_assis.presentation.ui.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.AnnotatedHeadline
import com.example.ai_assis.presentation.ui.flow.components.FlowBackground
import com.example.ai_assis.presentation.ui.flow.components.FlowLottieIllustration
import com.example.ai_assis.presentation.ui.flow.components.OutlinedFlowButton
import com.example.ai_assis.presentation.ui.flow.components.PagerIndicatorRow
import com.example.ai_assis.presentation.ui.flow.components.PermissionGrantedBanner
import com.example.ai_assis.presentation.ui.flow.components.PillPrimaryButton
import com.example.ai_assis.util.PermissionUtils

@Composable
fun OverlayPermissionScreen(
    onOpenOverlaySettings: () -> Unit,
    onContinue: () -> Unit,
    stepIndex: Int,
    stepCount: Int = 2,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(PermissionUtils.hasOverlayPermission(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = PermissionUtils.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    FlowBackground(
        decorationSeed = stepIndex + 1,
        premiumLightGradient = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp),
        ) {
            PagerIndicatorRow(
                pageCount = stepCount,
                currentPage = stepIndex,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FlowLottieIllustration(
                    lottieRes = R.raw.permission_overlay,
                    cacheKeySuffix = "perm_overlay",
                )
                if (granted) {
                    PermissionGrantedBanner(
                        message = stringResource(R.string.flow_overlay_enabled_banner),
                        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.height(24.dp))
                }
                AnnotatedHeadline(
                    fullText = stringResource(R.string.flow_overlay_title),
                    accentPhrases = listOf(stringResource(R.string.flow_overlay_title_accent)),
                    modifier = Modifier.fillMaxWidth(),
                    titleFontSize = 22.sp,
                    letterSpacing = 0.4.sp,
                )
                Text(
                    text = stringResource(
                        if (granted) {
                            R.string.flow_overlay_body_granted
                        } else {
                            R.string.flow_overlay_body
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!granted) {
                    PillPrimaryButton(
                        text = stringResource(R.string.flow_overlay_enable),
                        onClick = onOpenOverlaySettings,
                    )
                    OutlinedFlowButton(
                        text = stringResource(R.string.flow_overlay_next),
                        onClick = onContinue,
                    )
                } else {
                    PillPrimaryButton(
                        text = stringResource(R.string.flow_overlay_next),
                        onClick = onContinue,
                    )
                }
                TextButton(
                    onClick = onOpenOverlaySettings,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = stringResource(R.string.flow_permission_open_settings),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
