package com.example.ai_assis.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.example.ai_assis.data.remote.model.PlanRecord
import com.example.ai_assis.payment.RazorpayPaymentRelay
import com.example.ai_assis.payment.RazorpayPaymentResult
import com.example.ai_assis.payment.SubscriptionPaymentRepository
import com.example.ai_assis.payment.VerifyOutcome
import com.example.ai_assis.presentation.ui.screen.PlanUiModel
import com.example.ai_assis.presentation.ui.screen.PricingPlan
import com.example.ai_assis.presentation.ui.screen.RazorpayOrderSession
import com.example.ai_assis.presentation.ui.screen.isPaid
import com.example.ai_assis.presentation.ui.screen.planId
import com.example.ai_assis.presentation.ui.screen.pricingPlanFromId
import com.example.ai_assis.presentation.ui.screen.toPlanUiModelOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UpgradeState {
    data object Idle : UpgradeState
    data object CreatingOrder : UpgradeState
    data object ConfirmingReceipt : UpgradeState
    data object Success : UpgradeState
    data class Error(val message: String) : UpgradeState
}

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val subscriptionPaymentRepository: SubscriptionPaymentRepository,
    private val authRepository: AuthRepository,
    private val razorpayPaymentRelay: RazorpayPaymentRelay,
    firestoreUsageRepository: FirestoreUsageRepository,
    usageManager: UsageManager,
) : ViewModel() {

    private val _state = MutableStateFlow<UpgradeState>(UpgradeState.Idle)
    val state: StateFlow<UpgradeState> = _state.asStateFlow()

    private val _checkoutSessions = MutableSharedFlow<RazorpayOrderSession>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val checkoutSessions: SharedFlow<RazorpayOrderSession> = _checkoutSessions.asSharedFlow()

    val plans: StateFlow<List<PlanUiModel>> = firestoreUsageRepository.observePlans()
        .map { remote ->
            remote.mapNotNull { it.toPlanUiModelOrNull() }.ifEmpty { fallbackPlans() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), fallbackPlans())

    val activePricingPlan: StateFlow<PricingPlan> = usageManager.userRecord
        .combine(plans) { record, loadedPlans ->
            pricingPlanFromId(record.resolvedPlanId())
                ?: loadedPlans.firstOrNull()?.pricingPlan
                ?: PricingPlan.Free
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PricingPlan.Free)

    private var awaiting: RazorpayOrderSession? = null

    init {
        viewModelScope.launch {
            razorpayPaymentRelay.events.collect { onRazorpayResult(it) }
        }
    }

    fun resetState() {
        Log.d(TAG, "resetState() called. Clearing awaiting session and setting Idle.")
        awaiting = null
        _state.value = UpgradeState.Idle
    }

    /**
     * Creates a Razorpay order on the backend, then emits [checkoutSessions] so the UI can open Checkout.
     */
    fun beginPaidCheckout(plan: PricingPlan, creditsToAdd: Int = 500) {
        if (!plan.isPaid()) {
            Log.w(TAG, "beginPaidCheckout ignored for non-paid plan=$plan")
            return
        }

        viewModelScope.launch {
            Log.d(TAG, "beginPaidCheckout start: plan=$plan, creditsToAdd=$creditsToAdd")
            val uid = authRepository.currentUid()
            if (uid == null) {
                Log.e(TAG, "beginPaidCheckout failed: uid is null (user not signed in)")
                _state.value = UpgradeState.Error("Not signed in. Please restart the app.")
                return@launch
            }
            Log.d(TAG, "Authenticated uid present. Proceeding to create order for uid=$uid")

            awaiting = null
            _state.value = UpgradeState.CreatingOrder

            subscriptionPaymentRepository.createSubscriptionOrder(plan, creditsToAdd).fold(
                onSuccess = { session ->
                    Log.i(
                        TAG,
                        "createSubscriptionOrder success: orderId=${session.orderId}, plan=${session.plan}, amountPaise=${session.amountPaise}",
                    )
                    awaiting = session
                    _state.value = UpgradeState.Idle
                    _checkoutSessions.emit(session)
                },
                onFailure = { e ->
                    Log.e(TAG, "createSubscriptionOrder failed: ${e.message}", e)
                    awaiting = null
                    _state.value = UpgradeState.Error(e.message ?: "Could not start payment. Try again.")
                },
            )
        }
    }

    private suspend fun onRazorpayResult(result: RazorpayPaymentResult) {
        val pending = awaiting ?: return
        Log.d(TAG, "onRazorpayResult received: result=$result, pendingOrderId=${pending.orderId}")
        when (result) {
            is RazorpayPaymentResult.Success -> {
                if (result.orderId != pending.orderId) {
                    Log.e(
                        TAG,
                        "Razorpay order mismatch. resultOrderId=${result.orderId}, pendingOrderId=${pending.orderId}",
                    )
                    awaiting = null
                    return
                }

                awaiting = null
                _state.value = UpgradeState.ConfirmingReceipt
                Log.d(
                    TAG,
                    "Verifying payment: orderId=${result.orderId}, paymentId=${result.paymentId}",
                )
                subscriptionPaymentRepository.verifyAndFulfill(
                    orderId = result.orderId,
                    paymentId = result.paymentId,
                    signature = result.signature,
                ).fold(
                    onSuccess = { outcome ->
                        Log.i(TAG, "verifyAndFulfill success: outcome=$outcome")
                        _state.value = when (outcome) {
                            VerifyOutcome.Granted, VerifyOutcome.AlreadyVerified -> UpgradeState.Success
                        }
                    },
                    onFailure = { e ->
                        Log.e(TAG, "verifyAndFulfill failed: ${e.message}", e)
                        awaiting = null
                        _state.value =
                            UpgradeState.Error(
                                e.message
                                    ?: "Could not activate your subscription. Contact support if you were charged.",
                            )
                    },
                )
            }
            is RazorpayPaymentResult.Failure -> {
                val lowered = result.message.lowercase()
                Log.w(TAG, "Razorpay checkout failure: code=${result.code}, message=${result.message}")
                awaiting = null
                if (lowered.contains("cancel")) {
                    Log.d(TAG, "User canceled Razorpay checkout.")
                    _state.value = UpgradeState.Idle
                } else {
                    _state.value = UpgradeState.Error(result.message)
                }
            }
        }
    }

    private fun fallbackPlans(): List<PlanUiModel> = listOf(
        PlanRecord(
            planId = PricingPlan.Free.planId(),
            name = "Free",
            features = listOf("30 replies/day", "Basic suggestions"),
            price = 0L,
            sortOrder = 0,
        ).toPlanUiModelOrNull(),
        PlanRecord(
            planId = PricingPlan.Monthly.planId(),
            name = "Pro Monthly",
            features = listOf("Unlimited replies", "Faster response", "All tones"),
            price = 9_900L,
            sortOrder = 1,
        ).toPlanUiModelOrNull(),
        PlanRecord(
            planId = PricingPlan.Yearly.planId(),
            name = "Pro Yearly",
            features = listOf("Unlimited replies", "Faster response", "All tones"),
            price = 69_900L,
            sortOrder = 2,
        ).toPlanUiModelOrNull(),
        PlanRecord(
            planId = PricingPlan.Credits.planId(),
            name = "Pay as you go",
            features = emptyList(),
            price = 4_900L,
            creditsToAdd = 500,
            sortOrder = 3,
        ).toPlanUiModelOrNull(),
    ).mapNotNull { it }

    private companion object {
        private const val TAG = "UpgradeFlow"
    }
}
