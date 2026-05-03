package com.example.ai_assis.presentation.ui.screen

enum class PricingPlan {
    Free,
    Monthly,
    Yearly,
    Credits,
}

fun PricingPlan.isPaid(): Boolean = this != PricingPlan.Free
