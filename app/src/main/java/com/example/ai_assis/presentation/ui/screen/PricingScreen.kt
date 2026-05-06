package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import android.util.Log
import com.example.ai_assis.R
import com.example.ai_assis.payment.RazorpayCheckoutStarter
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
    val tag = "PricingScreen"
    val plans by viewModel.plans.collectAsState()
    val activePlan by viewModel.activePricingPlan.collectAsState()
    var selectedPlan by remember { mutableStateOf(PricingPlan.Free) }
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val upgradeState by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val activity = LocalContext.current as ComponentActivity
    LaunchedEffect(activePlan) {
        selectedPlan = activePlan
    }

    LaunchedEffect(activity) {
        viewModel.checkoutSessions.collect { session ->
            Log.d(
                tag,
                "checkoutSessions emit: orderId=${session.orderId}, plan=${session.plan}, amountPaise=${session.amountPaise}, currency=${session.currency}",
            )
            RazorpayCheckoutStarter.present(activity, session)
        }
    }

    // Navigate away on successful upgrade.
    LaunchedEffect(upgradeState) {
        Log.d(tag, "upgradeState changed: $upgradeState")
        when (val s = upgradeState) {
            is UpgradeState.Success -> {
                Log.i(tag, "Upgrade success. Navigating back.")
                viewModel.resetState()
                onContinueOrUpgrade()
            }
            is UpgradeState.Error -> {
                Log.e(tag, "Upgrade error shown to user: ${s.message}")
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

                plans.forEach { planUi ->
                    PlanCard(
                        plan = planUi,
                        selected = selectedPlan == planUi.pricingPlan,
                        active = activePlan == planUi.pricingPlan,
                        onClick = { selectedPlan = planUi.pricingPlan },
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val isLoading =
                    upgradeState is UpgradeState.CreatingOrder || upgradeState is UpgradeState.ConfirmingReceipt
                val ctaText = if (selectedPlan.isPaid()) {
                    stringResource(R.string.pricing_cta_upgrade)
                } else {
                    stringResource(R.string.pricing_cta_continue)
                }

                Button(
                    onClick = {
                        if (selectedPlan.isPaid()) {
                            val credits = plans.firstOrNull { it.pricingPlan == selectedPlan }?.creditsToAdd ?: 500
                            Log.d(
                                tag,
                                "Upgrade CTA tapped: selectedPlan=$selectedPlan, resolvedCreditsToAdd=$credits, plansLoaded=${plans.size}",
                            )
                            viewModel.beginPaidCheckout(selectedPlan, creditsToAdd = credits)
                        } else {
                            Log.d(tag, "Continue CTA tapped for free plan")
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
