package com.example.ai_assis.presentation.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ai_assis.R
import com.example.ai_assis.presentation.navigation.Screen
import com.example.ai_assis.presentation.viewmodel.HomeViewModel

@Composable
fun MainDashboardShell(
    onOpenNotificationAccess: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onStartOverlayService: () -> Unit,
    onStopOverlayService: () -> Unit,
    onOpenAppFilter: () -> Unit,
    onOpenSuggestions: () -> Unit,
    onOpenImeSettings: () -> Unit,
    onOpenProUpgrade: () -> Unit,
    initialInnerRoute: String? = null,
    onInitialInnerRouteConsumed: () -> Unit = {},
) {
    val innerNav = rememberNavController()
    val navBackStackEntry by innerNav.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.Overview) }
    val homeViewModel: HomeViewModel = hiltViewModel()

    val navigateToHome: () -> Unit = {
        innerNav.navigate(Screen.Home.route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(Screen.Home.route) {
                saveState = true
            }
        }
    }

    val navigateToAnalytics: () -> Unit = {
        innerNav.navigate(Screen.Analytics.route) {
            launchSingleTop = true
            restoreState = true
            popUpTo(Screen.Home.route) {
                saveState = true
            }
        }
    }

    LaunchedEffect(initialInnerRoute) {
        val route = initialInnerRoute ?: return@LaunchedEffect
        if (route == Screen.Analytics.route) {
            navigateToAnalytics()
        }
        onInitialInnerRouteConsumed()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == Screen.Home.route && selectedTab == DashboardTab.Overview,
                    onClick = {
                        selectedTab = DashboardTab.Overview
                        navigateToHome()
                    },
                    icon = {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = stringResource(R.string.dashboard_tab_overview),
                        )
                    },
                    label = { Text(stringResource(R.string.dashboard_tab_overview)) },
                )
                NavigationBarItem(
                    selected = currentRoute == Screen.Home.route && selectedTab == DashboardTab.Activity,
                    onClick = {
                        selectedTab = DashboardTab.Activity
                        navigateToHome()
                    },
                    icon = {
                        Icon(
                            Icons.Default.List,
                            contentDescription = stringResource(R.string.dashboard_tab_activity),
                        )
                    },
                    label = { Text(stringResource(R.string.dashboard_tab_activity)) },
                )
                NavigationBarItem(
                    selected = currentRoute == Screen.Home.route && selectedTab == DashboardTab.Settings,
                    onClick = {
                        selectedTab = DashboardTab.Settings
                        navigateToHome()
                    },
                    icon = {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.dashboard_tab_settings),
                        )
                    },
                    label = { Text(stringResource(R.string.dashboard_tab_settings)) },
                )
                NavigationBarItem(
                    selected = currentRoute == Screen.Analytics.route,
                    onClick = navigateToAnalytics,
                    icon = {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = stringResource(R.string.dashboard_tab_analytics),
                        )
                    },
                    label = { Text(stringResource(R.string.dashboard_tab_analytics)) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = innerNav,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .padding(innerPadding),
        ) {
            composable(route = Screen.Home.route) {
                HomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 20.dp),
                    selectedTab = selectedTab,
                    viewModel = homeViewModel,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                    onStartOverlayService = onStartOverlayService,
                    onStopOverlayService = onStopOverlayService,
                    onOpenAppFilter = onOpenAppFilter,
                    onOpenSuggestions = onOpenSuggestions,
                    onOpenImeSettings = onOpenImeSettings,
                    onOpenProUpgrade = onOpenProUpgrade,
                )
            }
            composable(route = Screen.Analytics.route) {
                UsageAnalyticsScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                )
            }
        }
    }
}
