package com.example.ai_assis.presentation.ui.flow



import androidx.annotation.RawRes

import androidx.annotation.StringRes

import androidx.compose.animation.AnimatedContent

import androidx.compose.animation.core.Animatable

import androidx.compose.animation.core.FastOutSlowInEasing

import androidx.compose.animation.core.animateDpAsState

import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.animation.core.tween

import androidx.compose.animation.fadeIn

import androidx.compose.animation.fadeOut

import androidx.compose.animation.togetherWith

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.background

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.interaction.collectIsPressedAsState

import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.safeDrawingPadding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.pager.HorizontalPager

import androidx.compose.foundation.pager.rememberPagerState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.Button

import androidx.compose.material3.ButtonDefaults

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text

import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.remember

import androidx.compose.runtime.rememberCoroutineScope

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.graphics.graphicsLayer

import androidx.compose.ui.platform.LocalDensity

import androidx.compose.ui.res.stringResource

import androidx.compose.ui.semantics.contentDescription

import androidx.compose.ui.semantics.semantics

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import com.example.ai_assis.R

import com.example.ai_assis.presentation.ui.flow.components.AnnotatedHeadline

import com.example.ai_assis.presentation.ui.flow.components.FlowBackground

import com.example.ai_assis.presentation.ui.flow.components.FlowLottieIllustration

import kotlin.math.abs

import kotlinx.coroutines.coroutineScope

import kotlinx.coroutines.launch



private data class OnboardingPageContent(

    @StringRes val title: Int,

    @StringRes val subtitle: Int,

    @StringRes val accentPhrase: Int,

    @RawRes val lottieRes: Int,

)



@OptIn(ExperimentalFoundationApi::class)

@Composable

fun OnboardingScreen(

    onComplete: () -> Unit,

) {

    val pages = listOf(

        OnboardingPageContent(

            title = R.string.flow_onboarding_title_1,

            subtitle = R.string.flow_onboarding_subtitle_1,

            accentPhrase = R.string.flow_onboarding_accent_ai,

            lottieRes = R.raw.onboarding_chat,

        ),

        OnboardingPageContent(

            title = R.string.flow_onboarding_title_2,

            subtitle = R.string.flow_onboarding_subtitle_2,

            accentPhrase = R.string.flow_onboarding_accent_apps,

            lottieRes = R.raw.onboarding_apps,

        ),

        OnboardingPageContent(

            title = R.string.flow_onboarding_title_3,

            subtitle = R.string.flow_onboarding_subtitle_3,

            accentPhrase = R.string.flow_onboarding_accent_privacy,

            lottieRes = R.raw.onboarding_privacy,

        ),

    )

    val pagerState = rememberPagerState(pageCount = { pages.size })

    val scope = rememberCoroutineScope()

    val density = LocalDensity.current

    val pageDescription = stringResource(

        R.string.flow_onboarding_pager,

        pagerState.currentPage + 1,

        pages.size,

    )



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

        decorationSeed = pagerState.currentPage,

        premiumLightGradient = true,

        privacyCoolBlue = pagerState.currentPage == pages.lastIndex,

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

        ) {

            Row(

                modifier = Modifier.fillMaxWidth(),

                horizontalArrangement = Arrangement.End,

                verticalAlignment = Alignment.CenterVertically,

            ) {

                TextButton(onClick = onComplete) {

                    Text(

                        text = stringResource(R.string.flow_onboarding_skip),

                        color = MaterialTheme.colorScheme.primary,

                        style = MaterialTheme.typography.labelLarge,

                    )

                }

            }



            Spacer(modifier = Modifier.height(12.dp))



            HorizontalPager(

                state = pagerState,

                modifier = Modifier

                    .fillMaxWidth()

                    .weight(1f, fill = true),

                verticalAlignment = Alignment.CenterVertically,

            ) { page ->

                val item = pages[page]

                val pageOffset = pagerState.getOffsetDistanceInPages(page)

                val offsetAbs = abs(pageOffset).coerceIn(0f, 1f)

                val slidePx = with(density) { (40.dp * pageOffset).toPx() }

                val alphaIllustration = (1f - offsetAbs * 0.55f).coerceIn(0.35f, 1f)

                val scaleIllustration = (1f - offsetAbs * 0.12f).coerceIn(0.88f, 1f)



                Column(

                    modifier = Modifier.fillMaxSize(),

                    horizontalAlignment = Alignment.CenterHorizontally,

                    verticalArrangement = Arrangement.Center,

                ) {

                    Box(

                        modifier = Modifier

                            .graphicsLayer {

                                this.alpha = alphaIllustration

                                translationX = slidePx

                                scaleX = scaleIllustration

                                scaleY = scaleIllustration

                            },

                    ) {

                        FlowLottieIllustration(

                            lottieRes = item.lottieRes,

                            cacheKeySuffix = "onboarding",

                            privacyChrome = item.lottieRes == R.raw.onboarding_privacy,

                        )

                    }

                }

            }



            Spacer(modifier = Modifier.height(12.dp))



            val copyPage = pagerState.targetPage.coerceIn(0, pages.lastIndex)

            AnimatedContent(

                targetState = copyPage,

                transitionSpec = {

                    fadeIn(animationSpec = tween(260)) togetherWith

                        fadeOut(animationSpec = tween(220))

                },

                label = "onboardingCopy",

                modifier = Modifier.fillMaxWidth(),

            ) { textPage ->

                val item = pages[textPage]

                Column(

                    modifier = Modifier

                        .fillMaxWidth()

                        .padding(horizontal = 4.dp),

                    horizontalAlignment = Alignment.CenterHorizontally,

                ) {

                    AnnotatedHeadline(

                        fullText = stringResource(item.title),

                        accentPhrases = listOf(stringResource(item.accentPhrase)),

                        modifier = Modifier.fillMaxWidth(),

                        titleFontSize = 22.sp,

                        letterSpacing = 0.4.sp,

                    )

                    Text(

                        text = stringResource(item.subtitle),

                        style = MaterialTheme.typography.bodyLarge.copy(

                            fontWeight = FontWeight.Medium,

                            lineHeight = 24.sp,

                            color = MaterialTheme.colorScheme.onSurfaceVariant,

                        ),

                        textAlign = TextAlign.Center,

                        modifier = Modifier.padding(top = 10.dp),

                    )

                }

            }



            Spacer(modifier = Modifier.height(28.dp))



            Column(

                modifier = Modifier

                    .fillMaxWidth()

                    .padding(bottom = 16.dp),

                horizontalAlignment = Alignment.CenterHorizontally,

                verticalArrangement = Arrangement.spacedBy(20.dp),

            ) {

                OnboardingPageIndicator(

                    pageCount = pages.size,

                    currentPage = pagerState.currentPage,

                    modifier = Modifier.semantics { contentDescription = pageDescription },

                )



                val isLast = pagerState.currentPage >= pages.lastIndex

                OnboardingPrimaryButton(

                    isLast = isLast,

                    onClick = {

                        if (isLast) {

                            onComplete()

                        } else {

                            scope.launch {

                                pagerState.scrollToPage(pagerState.currentPage + 1)

                            }

                        }

                    },

                )

            }

        }

    }

}



@Composable

private fun OnboardingPageIndicator(

    pageCount: Int,

    currentPage: Int,

    modifier: Modifier = Modifier,

) {

    Row(

        modifier = modifier,

        horizontalArrangement = Arrangement.spacedBy(8.dp),

        verticalAlignment = Alignment.CenterVertically,

    ) {

        repeat(pageCount) { index ->

            val selected = index == currentPage

            val width by animateDpAsState(

                targetValue = if (selected) 16.dp else 6.dp,

                animationSpec = tween(durationMillis = 280),

                label = "onboardingDotWidth",

            )

            val scheme = MaterialTheme.colorScheme

            Box(

                modifier = Modifier

                    .width(width)

                    .height(6.dp)

                    .clip(RoundedCornerShape(percent = 50))

                    .background(

                        if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.75f),

                    ),

            )

        }

    }

}



@Composable

private fun OnboardingPrimaryButton(

    isLast: Boolean,

    onClick: () -> Unit,

    modifier: Modifier = Modifier,

) {

    val interactionSource = remember { MutableInteractionSource() }

    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(

        targetValue = if (pressed) 0.96f else 1f,

        label = "onboardingCtaScale",

    )

    val shape = RoundedCornerShape(16.dp)

    val scheme = MaterialTheme.colorScheme



    Box(

        modifier = modifier

            .fillMaxWidth()

            .graphicsLayer {

                scaleX = scale

                scaleY = scale

            },

    ) {

        Button(

            onClick = onClick,

            modifier = Modifier

                .fillMaxWidth()

                .shadow(

                    elevation = 6.dp,

                    shape = shape,

                    ambientColor = scheme.primary.copy(alpha = 0.28f),

                    spotColor = scheme.primary.copy(alpha = 0.4f),

                ),

            shape = shape,

            interactionSource = interactionSource,

            colors = ButtonDefaults.buttonColors(

                containerColor = scheme.primary,

                contentColor = scheme.onPrimary,

            ),

        ) {

            AnimatedContent(

                targetState = isLast,

                transitionSpec = {

                    fadeIn(tween(200)) togetherWith fadeOut(tween(180))

                },

                label = "onboardingCtaLabel",

            ) { last ->

                Text(

                    text = stringResource(

                        if (last) {

                            R.string.flow_onboarding_get_started

                        } else {

                            R.string.flow_onboarding_next

                        },

                    ),

                    style = MaterialTheme.typography.labelLarge,

                )

            }

        }

    }

}

