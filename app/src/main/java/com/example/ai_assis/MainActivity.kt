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
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.ai_assis.data.local.setIntroFlowCompleted
import com.example.ai_assis.data.local.isIntroFlowCompleted
import com.example.ai_assis.presentation.navigation.Screen
import com.example.ai_assis.presentation.suggestions.SuggestionsEffect
import com.example.ai_assis.presentation.suggestions.SuggestionsScreen
import com.example.ai_assis.presentation.suggestions.SuggestionsViewModel
import com.example.ai_assis.presentation.ui.flow.LoginScreen
import com.example.ai_assis.presentation.ui.flow.NotificationPermissionScreen
import com.example.ai_assis.presentation.ui.flow.OnboardingScreen
import com.example.ai_assis.presentation.ui.flow.OverlayPermissionScreen
import com.example.ai_assis.presentation.ui.flow.SplashScreen
import com.example.ai_assis.presentation.ui.screen.AppFilterScreen
import com.example.ai_assis.presentation.ui.screen.MainDashboardShell
import com.example.ai_assis.presentation.ui.screen.PaymentSuccessScreen
import com.example.ai_assis.presentation.viewmodel.AppFilterViewModel
import com.example.ai_assis.presentation.viewmodel.AppInitViewModel
import com.example.ai_assis.domain.repository.NotificationAnalyticsRepository
import com.example.ai_assis.notifications.NavTarget
import com.example.ai_assis.notifications.applyNotificationIntentForPendingNav
import com.example.ai_assis.notifications.clearNotificationNavExtras
import com.example.ai_assis.service.MainAppForegroundTracker
import com.example.ai_assis.service.NotificationEventBus
import com.example.ai_assis.service.OverlayService
import com.example.ai_assis.ui.theme.AI_AssisTheme
import com.example.ai_assis.payment.RazorpayPaymentRelay
import com.example.ai_assis.payment.RazorpayPaymentResult
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity(), PaymentResultWithDataListener {

    @Inject
    lateinit var razorpayPaymentRelay: RazorpayPaymentRelay

    @Inject
    lateinit var notificationAnalyticsRepository: NotificationAnalyticsRepository

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
                AppNav(notificationAnalyticsRepository = notificationAnalyticsRepository)
            }
        }
    }

    override fun onPaymentSuccess(razorpayPaymentID: String?, paymentData: PaymentData?) {
        val orderId = paymentData?.orderId
        val paymentId = razorpayPaymentID?.takeIf { it.isNotBlank() } ?: paymentData?.paymentId
        val signature = paymentData?.signature
        if (orderId.isNullOrBlank() || paymentId.isNullOrBlank() || signature.isNullOrBlank()) {
            razorpayPaymentRelay.publish(
                RazorpayPaymentResult.Failure("Missing payment confirmation from Razorpay.", -1),
            )
            return
        }
        razorpayPaymentRelay.publish(
            RazorpayPaymentResult.Success(
                orderId = orderId,
                paymentId = paymentId,
                signature = signature,
            ),
        )
    }

    override fun onPaymentError(errorCode: Int, response: String?, paymentData: PaymentData?) {
        val message = response?.takeIf { it.isNotBlank() } ?: "Payment could not be completed."
        razorpayPaymentRelay.publish(RazorpayPaymentResult.Failure(message, errorCode))
    }

    companion object {
        const val EXTRA_OPEN_PRO_UPGRADE: String = "com.example.ai_assis.extra.OPEN_PRO_UPGRADE"
    }
}

@Composable
private fun AppNav(
    notificationAnalyticsRepository: NotificationAnalyticsRepository,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val appInitViewModel: AppInitViewModel = hiltViewModel()
    var pendingDashboardInnerRoute by remember { mutableStateOf<String?>(null) }

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

    val activity = context as MainActivity
    DisposableEffect(lifecycleOwner, navController) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pending = activity.applyNotificationIntentForPendingNav()
                val hasNav =
                    pending.target != NavTarget.DEFAULT ||
                        pending.innerRoute != null ||
                        pending.trackClick
                if (hasNav) {
                    if (pending.trackClick && pending.analyticsKey != null) {
                        scope.launch {
                            notificationAnalyticsRepository.recordClick(
                                pending.analyticsKey,
                                System.currentTimeMillis(),
                            )
                        }
                    }
                    when (pending.target) {
                        NavTarget.PRO_UPGRADE ->
                            navController.navigate(Screen.ProUpgrade.route) {
                                launchSingleTop = true
                            }
                        NavTarget.APP_FILTER ->
                            navController.navigate(Screen.AppFilter.route) {
                                launchSingleTop = true
                            }
                        NavTarget.SUGGESTIONS ->
                            navController.navigate(Screen.Suggestions.route) {
                                launchSingleTop = true
                            }
                        NavTarget.ANALYTICS -> {
                            navController.navigate(Screen.MainDashboard.route) {
                                launchSingleTop = true
                            }
                            pendingDashboardInnerRoute = Screen.Analytics.route
                        }
                        NavTarget.MAIN, NavTarget.DEFAULT -> {
                            navController.navigate(Screen.MainDashboard.route) {
                                launchSingleTop = true
                            }
                            pending.innerRoute?.let { pendingDashboardInnerRoute = it }
                        }
                    }
                    activity.clearNotificationNavExtras()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var initialRoute: String? by remember { mutableStateOf(null) }
    LaunchedEffect(Unit) {
        initialRoute = if (context.isIntroFlowCompleted()) {
            Screen.MainDashboard.route
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
                    onOpenNotificationSettings = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onContinue = {
                        navController.navigate(Screen.OverlayPermission.route)
                    },
                    stepIndex = 0,
                )
            }

            composable(route = Screen.OverlayPermission.route) {
                OverlayPermissionScreen(
                    onOpenOverlaySettings = {
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
                    stepIndex = 1,
                )
            }

            composable(route = Screen.Login.route) {
                val currentContext by rememberUpdatedState(context)
                val finishLogin = remember(scope, navController) {
                    {
                        scope.launch {
                            currentContext.setIntroFlowCompleted(true)
                            navController.navigate(Screen.MainDashboard.route) {
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

            composable(route = Screen.MainDashboard.route) {
                LaunchedEffect(Unit) { appInitViewModel.ensureAuthAndObserve() }
                MainDashboardShell(
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
                    initialInnerRoute = pendingDashboardInnerRoute,
                    onInitialInnerRouteConsumed = { pendingDashboardInnerRoute = null },
                )
            }

            composable(route = Screen.ProUpgrade.route) {
                ProUpgradeScreen(
                    onUpgrade = { planId, amountPaise, currency, creditsToAdd, orderId, paymentId ->
                        navController.navigate(
                            Screen.PaymentSuccess.createRoute(
                                planId = planId,
                                amountPaise = amountPaise,
                                currency = currency,
                                creditsToAdd = creditsToAdd,
                                orderId = orderId,
                                paymentId = paymentId,
                            ),
                        ) {
                            popUpTo(Screen.ProUpgrade.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onDismiss = { navController.popBackStack() },
                )
            }

            composable(
                route = Screen.PaymentSuccess.route,
                arguments = listOf(
                    navArgument(Screen.PaymentSuccess.PLAN_ID) { type = NavType.StringType },
                    navArgument(Screen.PaymentSuccess.AMOUNT_PAISE) { type = NavType.LongType },
                    navArgument(Screen.PaymentSuccess.CURRENCY) { type = NavType.StringType },
                    navArgument(Screen.PaymentSuccess.CREDITS_TO_ADD) { type = NavType.IntType },
                    navArgument(Screen.PaymentSuccess.ORDER_ID) { type = NavType.StringType },
                    navArgument(Screen.PaymentSuccess.PAYMENT_ID) { type = NavType.StringType },
                ),
            ) { backStackEntry ->
                PaymentSuccessScreen(
                    planId = backStackEntry.arguments?.getString(Screen.PaymentSuccess.PLAN_ID).orEmpty(),
                    amountPaise = backStackEntry.arguments?.getLong(Screen.PaymentSuccess.AMOUNT_PAISE) ?: 0L,
                    currency = backStackEntry.arguments?.getString(Screen.PaymentSuccess.CURRENCY).orEmpty(),
                    creditsToAdd = backStackEntry.arguments?.getInt(Screen.PaymentSuccess.CREDITS_TO_ADD) ?: 0,
                    orderId = backStackEntry.arguments?.getString(Screen.PaymentSuccess.ORDER_ID).orEmpty(),
                    paymentId = backStackEntry.arguments?.getString(Screen.PaymentSuccess.PAYMENT_ID).orEmpty(),
                    onContinue = {
                        navController.navigate(Screen.MainDashboard.route) {
                            popUpTo(Screen.MainDashboard.route) { inclusive = false }
                            launchSingleTop = true
                        }
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
