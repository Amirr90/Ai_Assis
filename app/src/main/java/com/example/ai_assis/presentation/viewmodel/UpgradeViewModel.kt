package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.example.ai_assis.presentation.ui.screen.PricingPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpgradeState {
    data object Idle : UpgradeState
    data object Loading : UpgradeState
    data object Success : UpgradeState
    data class Error(val message: String) : UpgradeState
}

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val firestoreRepo: FirestoreUsageRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UpgradeState>(UpgradeState.Idle)
    val state: StateFlow<UpgradeState> = _state.asStateFlow()

    /**
     * Records the selected plan in Firestore and activates credits if applicable.
     * [creditsToAdd] is only used when [plan] == [PricingPlan.Credits].
     */
    fun selectPlan(plan: PricingPlan, creditsToAdd: Int = 50) {
        if (plan == PricingPlan.Free) return
        viewModelScope.launch {
            _state.value = UpgradeState.Loading
            val uid = authRepository.currentUid()
            if (uid == null) {
                _state.value = UpgradeState.Error("Not signed in. Please restart the app.")
                return@launch
            }
            try {
                firestoreRepo.setSubscription(uid, plan, creditsToAdd)
                _state.value = UpgradeState.Success
            } catch (e: Exception) {
                _state.value = UpgradeState.Error(e.message ?: "Upgrade failed. Try again.")
            }
        }
    }

    fun resetState() {
        _state.value = UpgradeState.Idle
    }
}
