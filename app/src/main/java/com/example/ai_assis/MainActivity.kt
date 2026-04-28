package com.example.ai_assis

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_assis.presentation.ui.screen.HomeScreen
import com.example.ai_assis.presentation.ui.screen.PermissionScreen
import com.example.ai_assis.presentation.viewmodel.HomeViewModel
import com.example.ai_assis.service.OverlayService
import com.example.ai_assis.ui.theme.AI_AssisTheme
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

    NavHost(
        navController = navController,
        startDestination = "permissions",
    ) {
        composable(route = "permissions") {
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
                onContinue = { navController.navigate("home") },
            )
        }
        composable(route = "home") {
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
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    AI_AssisTheme {
        PermissionScreen(
            onGrantNotificationAccess = {},
            onGrantOverlayAccess = {},
            onContinue = {},
        )
    }
}