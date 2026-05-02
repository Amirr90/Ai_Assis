package com.example.ai_assis

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.example.ai_assis.presentation.ui.screen.ProUpgradeScreen
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_assis.data.local.setIntroFlowCompleted
import com.example.ai_assis.data.local.isIntroFlowCompleted
import com.example.ai_assis.presentation.navigation.Screen
import com.example.ai_assis.presentation.suggestions.SuggestionsEffect
import com.example.ai_assis.presentation.suggestions.SuggestionsScreen
import com.example.ai_assis.presentation.suggestions.SuggestionsViewModel
import com.example.ai_assis.presentation.ui.flow.DashboardScreen
import com.example.ai_assis.presentation.ui.flow.LoginScreen
import com.example.ai_assis.presentation.ui.flow.NotificationPermissionScreen
import com.example.ai_assis.presentation.ui.flow.OnboardingScreen
import com.example.ai_assis.presentation.ui.flow.OverlayPermissionScreen
import com.example.ai_assis.presentation.ui.flow.SplashScreen
import com.example.ai_assis.presentation.ui.screen.AppFilterScreen
import com.example.ai_assis.presentation.ui.screen.HomeScreen
import com.example.ai_assis.presentation.viewmodel.AppFilterViewModel
import com.example.ai_assis.presentation.viewmodel.HomeViewModel
import com.example.ai_assis.service.MainAppForegroundTracker
import com.example.ai_assis.service.NotificationEventBus
import com.example.ai_assis.service.OverlayService
import com.example.ai_assis.ui.theme.AI_AssisTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color(0xFFF2F2F2).toArgb(),
                darkScrim = Color(0xFF1F1F1F).toArgb(),
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color(0xFFF2F2F2).toArgb(),
                darkScrim = Color(0xFF1F1F1F).toArgb(),
            ),
        )
        setContent {
            AI_AssisTheme {
                AppNav()
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_PRO_UPGRADE: String = "com.example.ai_assis.extra.OPEN_PRO_UPGRADE"
    }
}

@Composable
private fun AppNav() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> MainAppForegroundTracker.setMainAppInForeground(true)
                Lifecycle.Event.ON_STOP -> MainAppForegroundTracker.setMainAppInForeground(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activity = context as ComponentActivity
    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (activity.intent.getBooleanExtra(MainActivity.EXTRA_OPEN_PRO_UPGRADE, false)) {
                    navController.navigate(Screen.ProUpgrade.route) {
                        launchSingleTop = true
                    }
                    activity.intent.removeExtra(MainActivity.EXTRA_OPEN_PRO_UPGRADE)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var initialRoute: String? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        initialRoute = if (context.isIntroFlowCompleted()) {
            Screen.Dashboard.route
        } else {
            Screen.Splash.route
        }
    }

    if (initialRoute == null) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }

    val startDestination = initialRoute!!

    key(startDestination) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable(route = Screen.Splash.route) {
                SplashScreen(
                    onNavigateNext = {
                        navController.navigate(Screen.Onboarding.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                )
            }

            composable(route = Screen.Onboarding.route) {
                OnboardingScreen(
                    onComplete = {
                        navController.navigate(Screen.NotificationPermission.route)
                    },
                )
            }

            composable(route = Screen.NotificationPermission.route) {
                NotificationPermissionScreen(
                    onAllowNotificationAccess = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onContinue = {
                        navController.navigate(Screen.OverlayPermission.route)
                    },
                )
            }

            composable(route = Screen.OverlayPermission.route) {
                OverlayPermissionScreen(
                    onEnableOverlay = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    onContinue = {
                        navController.navigate(Screen.Login.route)
                    },
                )
            }

            composable(route = Screen.Login.route) {
                val currentContext by rememberUpdatedState(context)
                val finishLogin = remember(scope, navController) {
                    {
                        scope.launch {
                            currentContext.setIntroFlowCompleted(true)
                            navController.navigate(Screen.Dashboard.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                        Unit
                    }
                }
                LoginScreen(
                    onContinueWithGoogle = finishLogin,
                    onSkip = finishLogin,
                )
            }

            composable(route = Screen.Dashboard.route) {
                DashboardScreen(
                    onUpgrade = {
                        navController.navigate(Screen.ProUpgrade.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenFullApp = {
                        navController.navigate(Screen.Home.route)
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
                        NotificationEventBus.onAssistantEnabledFromApp()
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
                    onOpenProUpgrade = {
                        navController.navigate(Screen.ProUpgrade.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(route = Screen.ProUpgrade.route) {
                val proContext = LocalContext.current
                ProUpgradeScreen(
                    onUpgrade = {
                        Toast.makeText(proContext, proContext.getString(R.string.pro_billing_coming_soon), Toast.LENGTH_SHORT)
                            .show()
                    },
                    onDismiss = { navController.popBackStack() },
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
                val suggestionsContext = LocalContext.current
                val clipboard = suggestionsContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

                LaunchedEffect(Unit) {
                    suggestionsViewModel.effects.collect { effect ->
                        when (effect) {
                            is SuggestionsEffect.CopyToClipboard -> {
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("suggestion", effect.text))
                                Toast.makeText(suggestionsContext, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            }
                            is SuggestionsEffect.ShowToast -> {
                                Toast.makeText(suggestionsContext, effect.message, Toast.LENGTH_SHORT).show()
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
}
