package com.example.ai_assis

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_assis.presentation.navigation.Screen
import com.example.ai_assis.presentation.suggestions.SuggestionsEffect
import com.example.ai_assis.presentation.suggestions.SuggestionsScreen
import com.example.ai_assis.presentation.suggestions.SuggestionsViewModel
import com.example.ai_assis.presentation.ui.screen.AppFilterScreen
import com.example.ai_assis.presentation.ui.screen.HomeScreen
import com.example.ai_assis.presentation.ui.screen.PermissionScreen
import com.example.ai_assis.presentation.viewmodel.AppFilterViewModel
import com.example.ai_assis.presentation.viewmodel.HomeViewModel
import com.example.ai_assis.service.OverlayService
import com.example.ai_assis.ui.theme.AI_AssisTheme
import com.example.ai_assis.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AI_AssisTheme {
                AppNav()
            }
        }
    }
}

@Composable
private fun AppNav() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val allPermissionsGranted = PermissionUtils.hasNotificationAccess(context) &&
        PermissionUtils.hasOverlayPermission(context)
    val startDestination = if (allPermissionsGranted) Screen.Home.route else Screen.Permissions.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
    ) {
        composable(route = Screen.Permissions.route) {
            PermissionScreen(
                onGrantNotificationAccess = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onGrantOverlayAccess = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                onContinue = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Permissions.route) { inclusive = true }
                    }
                },
            )
        }

        composable(route = Screen.Home.route) {
            val homeViewModel: HomeViewModel = hiltViewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onOpenNotificationAccess = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onOpenOverlayPermission = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
                onStartOverlayService = {
                    context.startService(Intent(context, OverlayService::class.java))
                },
                onStopOverlayService = {
                    context.stopService(Intent(context, OverlayService::class.java))
                },
                onOpenAppFilter = {
                    navController.navigate(Screen.AppFilter.route)
                },
                onOpenSuggestions = {
                    navController.navigate(Screen.Suggestions.route)
                },
                onOpenImeSettings = {
                    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                },
            )
        }

        composable(route = Screen.AppFilter.route) {
            val appFilterViewModel: AppFilterViewModel = hiltViewModel()
            AppFilterScreen(
                viewModel = appFilterViewModel,
                onBack = { navController.popBackStack() },
            )
        }

        composable(route = Screen.Suggestions.route) {
            val suggestionsViewModel: SuggestionsViewModel = hiltViewModel()
            val uiState by suggestionsViewModel.uiState.collectAsState()
            val context = LocalContext.current
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

            LaunchedEffect(Unit) {
                suggestionsViewModel.effects.collect { effect ->
                    when (effect) {
                        is SuggestionsEffect.CopyToClipboard -> {
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("suggestion", effect.text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                        is SuggestionsEffect.ShowToast -> {
                            Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }

            SuggestionsScreen(
                uiState = uiState,
                onBack = { navController.popBackStack() },
                onEvent = suggestionsViewModel::onEvent,
            )
        }
    }
}
