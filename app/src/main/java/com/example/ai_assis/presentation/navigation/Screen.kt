package com.example.ai_assis.presentation.navigation

sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object NotificationPermission : Screen("notification_permission")
    data object OverlayPermission : Screen("overlay_permission")
    data object Login : Screen("login")
    data object Dashboard : Screen("dashboard")

    data object Home : Screen("home")
    data object AppFilter : Screen("app_filter")
    data object Suggestions : Screen("suggestions")
    data object ProUpgrade : Screen("pro_upgrade")
}
