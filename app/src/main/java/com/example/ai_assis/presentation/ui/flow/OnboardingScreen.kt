package com.example.ai_assis.presentation.ui.flow

import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.FlowScaffold
import com.example.ai_assis.presentation.ui.flow.components.PagerIndicatorRow
import com.example.ai_assis.presentation.ui.flow.components.PrimaryFlowButton
import kotlinx.coroutines.launch

private data class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
) {
    val pages = listOf(
        OnboardingPage(R.string.flow_onboarding_title_1, R.string.flow_onboarding_subtitle_1),
        OnboardingPage(R.string.flow_onboarding_title_2, R.string.flow_onboarding_subtitle_2),
        OnboardingPage(R.string.flow_onboarding_title_3, R.string.flow_onboarding_subtitle_3),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val pageDescription = stringResource(
        R.string.flow_onboarding_pager,
        pagerState.currentPage + 1,
        pages.size,
    )

    FlowScaffold(
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PagerIndicatorRow(
                    pageCount = pages.size,
                    currentPage = pagerState.currentPage,
                    modifier = Modifier.semantics { contentDescription = pageDescription },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onComplete) {
                        Text(
                            text = stringResource(R.string.flow_onboarding_skip),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    PrimaryFlowButton(
                        text = stringResource(R.string.flow_onboarding_next),
                        onClick = {
                            if (pagerState.currentPage < pages.lastIndex) {
                                scope.launch {
                                    pagerState.scrollToPage(pagerState.currentPage + 1)
                                }
                            } else {
                                onComplete()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 8.dp),
                    )
                }
            }
        },
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
        ) { page ->
            val item = pages[page]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(item.title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(item.subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}
