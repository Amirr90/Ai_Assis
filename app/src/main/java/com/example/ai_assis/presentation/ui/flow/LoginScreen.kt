package com.example.ai_assis.presentation.ui.flow

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai_assis.BuildConfig
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.AnnotatedHeadline
import com.example.ai_assis.presentation.ui.flow.components.FlowBackground
import com.example.ai_assis.presentation.ui.flow.components.FlowLottieIllustration
import com.example.ai_assis.presentation.ui.flow.components.PillPrimaryButton
import com.example.ai_assis.presentation.viewmodel.LoginState
import com.example.ai_assis.presentation.viewmodel.LoginViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onContinueWithGoogle: () -> Unit,
    onSkip: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val loginState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val webClientId = stringResource(R.string.default_web_client_id)

    LaunchedEffect(loginState) {
        when (val s = loginState) {
            is LoginState.Success -> {
                viewModel.resetState()
                onContinueWithGoogle()
            }
            is LoginState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    val heroScale = remember { Animatable(0.82f) }
    val heroAlpha = remember { Animatable(0f) }
    val copyAlpha = remember { Animatable(0f) }
    val copyOffsetX = remember { Animatable(0f) }
    val ctaAlpha = remember { Animatable(0f) }
    val ctaOffsetY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        copyOffsetX.snapTo(with(density) { (-28).dp.toPx() })
        ctaOffsetY.snapTo(with(density) { 36.dp.toPx() })
        coroutineScope {
            launch {
                heroAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                )
            }
            launch {
                heroScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                )
            }
        }
        delay(95)
        copyAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing))
        copyOffsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        )
        delay(110)
        coroutineScope {
            launch { ctaAlpha.animateTo(targetValue = 1f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)) }
            launch {
                ctaOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
                )
            }
        }
    }

    val isLoading = loginState is LoginState.Loading

    Box(modifier = Modifier.fillMaxSize()) {
        FlowBackground(decorationSeed = 2, premiumLightGradient = true) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 20.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    FlowLottieIllustration(
                        lottieRes = R.raw.flow_login,
                        cacheKeySuffix = "login",
                        modifier = Modifier.graphicsLayer {
                            alpha = heroAlpha.value
                            scaleX = heroScale.value
                            scaleY = heroScale.value
                        },
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().graphicsLayer {
                            alpha = copyAlpha.value
                            translationX = copyOffsetX.value
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AnnotatedHeadline(
                            fullText = stringResource(R.string.flow_login_title),
                            accentPhrases = listOf(stringResource(R.string.flow_login_title_accent)),
                            modifier = Modifier.fillMaxWidth(),
                            titleFontSize = 22.sp,
                            letterSpacing = 0.4.sp,
                        )
                        Text(
                            text = stringResource(R.string.flow_login_subtitle),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Medium,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .graphicsLayer {
                            alpha = ctaAlpha.value
                            translationY = ctaOffsetY.value
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PillPrimaryButton(
                        text = if (isLoading) "Signing in…" else stringResource(R.string.flow_login_google),
                        onClick = {
                            if (!isLoading) {
                                scope.launch {
                                    if (webClientId.isBlank()) {
                                        // Web OAuth client not yet configured → use anonymous auth
                                        viewModel.signInAnonymously()
                                        return@launch
                                    }
                                    try {
                                        val credentialManager = CredentialManager.create(context)
                                        val googleIdOption = GetGoogleIdOption.Builder()
                                            .setFilterByAuthorizedAccounts(false)
                                            .setServerClientId(webClientId)
                                            .build()
                                        val request = GetCredentialRequest.Builder()
                                            .addCredentialOption(googleIdOption)
                                            .build()
                                        val result = credentialManager.getCredential(context, request)
                                        val googleCredential = GoogleIdTokenCredential.createFrom(result.credential.data)
                                        viewModel.signInWithGoogle(googleCredential.idToken)
                                    } catch (e: GetCredentialException) {
                                        Log.w(BuildConfig.APPLICATION_ID, "Google Sign-In unavailable: ${e.message}")
                                        viewModel.signInAnonymously()
                                    } catch (e: Exception) {
                                        Log.w(BuildConfig.APPLICATION_ID, "Credential error: ${e.message}")
                                        viewModel.signInAnonymously()
                                    }
                                }
                            }
                        },
                    )
                    TextButton(
                        onClick = { if (!isLoading) viewModel.signInAnonymously() },
                    ) {
                        Text(
                            text = stringResource(R.string.flow_login_skip),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}
