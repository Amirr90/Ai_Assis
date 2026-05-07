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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
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
    onUpgradeSuccess: (planId: String, amountPaise: Long, currency: String, creditsToAdd: Int, orderId: String, paymentId: String) -> Unit,
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
    var failedDialogMessage by remember { mutableStateOf<String?>(null) }

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
                Log.i(tag, "Upgrade success. Opening success screen.")
                viewModel.resetState()
                onUpgradeSuccess(
                    s.purchase.planId,
                    s.purchase.amountPaise,
                    s.purchase.currency,
                    s.purchase.creditsToAdd,
                    s.purchase.orderId,
                    s.purchase.paymentId,
                )
            }
            is UpgradeState.Error -> {
                Log.e(tag, "Upgrade error shown to user: ${s.message}")
                failedDialogMessage = s.message
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

                val displayPlans = plans
                    .filter { it.pricingPlan != PricingPlan.Free }
                    .sortedBy { it.sortOrder }

                displayPlans.forEach { planUi ->
                    PlanCard(
                        plan = planUi,
                        selected = selectedPlan == planUi.pricingPlan,
                        active = activePlan == planUi.pricingPlan,
                        isLoading = upgradeState is UpgradeState.CreatingOrder || upgradeState is UpgradeState.ConfirmingReceipt,
                        onSelect = { selectedPlan = planUi.pricingPlan },
                        onBuyClick = {
                            selectedPlan = planUi.pricingPlan
                            if (planUi.pricingPlan.isPaid()) {
                                val credits = plans.firstOrNull { it.pricingPlan == planUi.pricingPlan }?.creditsToAdd ?: 500
                                Log.d(
                                    tag,
                                    "Card CTA tapped: selectedPlan=${planUi.pricingPlan}, resolvedCreditsToAdd=$credits, plansLoaded=${plans.size}",
                                )
                                viewModel.beginPaidCheckout(planUi.pricingPlan, creditsToAdd = credits)
                            } else {
                                Log.d(tag, "Card CTA tapped for free plan")
                                onContinueOrUpgrade()
                            }
                        },
                    )
                }

                TextButton(onClick = onContinueOrUpgrade) {
                    Text(
                        text = stringResource(R.string.pricing_cta_continue),
                        style = typography.labelLarge,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        val errorMessage = failedDialogMessage
        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = { failedDialogMessage = null },
                title = { Text(text = stringResource(R.string.payment_failed_title)) },
                text = { Text(text = errorMessage) },
                confirmButton = {
                    TextButton(onClick = { failedDialogMessage = null }) {
                        Text(text = stringResource(R.string.payment_failed_retry))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { failedDialogMessage = null }) {
                        Text(text = stringResource(R.string.payment_failed_close))
                    }
                },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PricingScreenPreview() {
    AI_AssisTheme {
        PricingScreen(onContinueOrUpgrade = {}, onUpgradeSuccess = { _, _, _, _, _, _ -> })
    }
}
