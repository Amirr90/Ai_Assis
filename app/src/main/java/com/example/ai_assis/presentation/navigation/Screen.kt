package com.example.ai_assis.presentation.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object NotificationPermission : Screen("notification_permission")
    data object OverlayPermission : Screen("overlay_permission")
    data object Login : Screen("login")

    data object Home : Screen("home")
    data object MainDashboard : Screen("main")
    data object Analytics : Screen("analytics")
    data object AppFilter : Screen("app_filter")
    data object Suggestions : Screen("suggestions")
    data object ProUpgrade : Screen("pro_upgrade")
    data object PaymentSuccess : Screen("payment_success/{planId}/{amountPaise}/{currency}/{creditsToAdd}/{orderId}/{paymentId}") {
        const val PLAN_ID = "planId"
        const val AMOUNT_PAISE = "amountPaise"
        const val CURRENCY = "currency"
        const val CREDITS_TO_ADD = "creditsToAdd"
        const val ORDER_ID = "orderId"
        const val PAYMENT_ID = "paymentId"

        fun createRoute(
            planId: String,
            amountPaise: Long,
            currency: String,
            creditsToAdd: Int,
            orderId: String,
            paymentId: String,
        ): String = "payment_success/$planId/$amountPaise/$currency/$creditsToAdd/$orderId/$paymentId"
    }
}
