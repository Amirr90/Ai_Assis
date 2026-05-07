package com.example.ai_assis.presentation.ui.screen

enum class PricingPlan {
    Free,
    Monthly,
    Yearly,
    Credits,
    Test,
}

fun PricingPlan.isPaid(): Boolean = this != PricingPlan.Free

fun PricingPlan.planId(): String = when (this) {
    PricingPlan.Free -> "free"
    PricingPlan.Monthly -> "monthly"
    PricingPlan.Yearly -> "yearly"
    PricingPlan.Credits -> "credits"
    PricingPlan.Test -> "test"
}

fun pricingPlanFromId(planId: String): PricingPlan? = when (planId.lowercase()) {
    "free" -> PricingPlan.Free
    "monthly" -> PricingPlan.Monthly
    "yearly" -> PricingPlan.Yearly
    "credits" -> PricingPlan.Credits
    "test" -> PricingPlan.Test
    else -> null
}

/** Payload to open Razorpay Checkout after [com.example.ai_assis.payment.SubscriptionPaymentRepository.createSubscriptionOrder]. */
data class RazorpayOrderSession(
    val keyId: String,
    val orderId: String,
    val amountPaise: Long,
    val currency: String,
    val plan: PricingPlan,
    val creditsToAdd: Int,
)
