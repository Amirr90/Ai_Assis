package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data object Success : LoginState
    data class Error(val message: String) : LoginState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestoreRepo: FirestoreUsageRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val uid = authRepository.signInWithGoogle(idToken)
                firestoreRepo.getOrCreateUser(uid)
                _state.value = LoginState.Success
            } catch (e: Exception) {
                _state.value = LoginState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val uid = authRepository.signInAnonymously()
                firestoreRepo.getOrCreateUser(uid)
                _state.value = LoginState.Success
            } catch (e: Exception) {
                _state.value = LoginState.Error(e.message ?: "Sign-in failed")
            }
        }
    }

    fun resetState() {
        _state.value = LoginState.Idle
    }
}
