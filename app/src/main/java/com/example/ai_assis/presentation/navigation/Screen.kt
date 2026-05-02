package com.example.ai_assis.presentation.navigation

sealed class Screen(val route: String) {
    data object Permissions : Screen("permissions")
    data object Home : Screen("home")
    data object AppFilter : Screen("app_filter")
    data object Suggestions : Screen("suggestions")
    data object ProUpgrade : Screen("pro_upgrade")
}
