package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ai_assis.R
import com.example.ai_assis.presentation.ui.flow.components.FlowBackground
import com.example.ai_assis.presentation.viewmodel.UpgradeState
import com.example.ai_assis.presentation.viewmodel.UpgradeViewModel
import com.example.ai_assis.ui.theme.AI_AssisTheme

@Composable
fun PricingScreen(
    onContinueOrUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpgradeViewModel = hiltViewModel(),
) {
    var selectedPlan by remember { mutableStateOf(PricingPlan.Free) }
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val upgradeState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigate away on successful upgrade
    LaunchedEffect(upgradeState) {
        when (val s = upgradeState) {
            is UpgradeState.Success -> {
                viewModel.resetState()
                onContinueOrUpgrade()
            }
            is UpgradeState.Error -> {
                snackbarHostState.showSnackbar(s.message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        FlowBackground(
            modifier = Modifier.fillMaxSize(),
            decorationSeed = 0,
            premiumLightGradient = true,
            privacyCoolBlue = false,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.pricing_screen_title),
                    style = typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.pricing_screen_subtitle),
                    style = typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                PlanCard(
                    plan = PricingPlan.Free,
                    selected = selectedPlan == PricingPlan.Free,
                    onClick = { selectedPlan = PricingPlan.Free },
                    featured = false,
                )
                PlanCard(
                    plan = PricingPlan.Monthly,
                    selected = selectedPlan == PricingPlan.Monthly,
                    onClick = { selectedPlan = PricingPlan.Monthly },
                    featured = false,
                )
                PlanCard(
                    plan = PricingPlan.Yearly,
                    selected = selectedPlan == PricingPlan.Yearly,
                    onClick = { selectedPlan = PricingPlan.Yearly },
                    featured = true,
                )
                PlanCard(
                    plan = PricingPlan.Credits,
                    selected = selectedPlan == PricingPlan.Credits,
                    onClick = { selectedPlan = PricingPlan.Credits },
                    featured = false,
                )

                Spacer(modifier = Modifier.height(12.dp))

                val isLoading = upgradeState is UpgradeState.Loading
                val ctaText = if (selectedPlan.isPaid()) {
                    stringResource(R.string.pricing_cta_upgrade)
                } else {
                    stringResource(R.string.pricing_cta_continue)
                }

                Button(
                    onClick = {
                        if (selectedPlan.isPaid()) {
                            viewModel.selectPlan(selectedPlan, creditsToAdd = 50)
                        } else {
                            onContinueOrUpgrade()
                        }
                    },
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = scheme.primary,
                        contentColor = scheme.onPrimary,
                    ),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = scheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = ctaText,
                            style = typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
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

@Preview(showBackground = true)
@Composable
private fun PricingScreenPreview() {
    AI_AssisTheme {
        PricingScreen(onContinueOrUpgrade = {})
    }
}
