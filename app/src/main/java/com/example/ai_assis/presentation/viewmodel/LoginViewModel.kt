package com.example.ai_assis.presentation.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.BuildConfig
import com.example.ai_assis.R
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val appContext: Context,
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
                syncUserRecordOrContinue(uid)
                _state.value = LoginState.Success
            } catch (e: Exception) {
                _state.value = LoginState.Error(messageForSignInFailure(e))
            }
        }
    }

    fun signInAnonymously() {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                val uid = authRepository.signInAnonymously()
                syncUserRecordOrContinue(uid)
                _state.value = LoginState.Success
            } catch (e: Exception) {
                _state.value = LoginState.Error(messageForSignInFailure(e))
            }
        }
    }

    private suspend fun syncUserRecordOrContinue(uid: String) {
        try {
            firestoreRepo.getOrCreateUser(uid)
        } catch (e: Exception) {
            if (e.firestorePermissionOrCause() != null) {
                Log.w(
                    TAG,
                    "Firestore user bootstrap permission denied; continuing login without remote profile sync.",
                    e,
                )
                return
            }
            throw e
        }
    }

    private fun messageForSignInFailure(e: Exception): String {
        val authEx = e.firebaseAuthOrCause()
        if (authEx != null && authEx.errorCode == ERROR_ADMIN_RESTRICTED_OPERATION) {
            Log.e(TAG, "Firebase Auth (${authEx.errorCode}): ${authEx.message}", e)
            return appContext.getString(R.string.flow_login_error_admin_restricted)
        }
        if (authEx != null) {
            Log.e(TAG, "Firebase Auth (${authEx.errorCode}): ${authEx.message}", e)
        }
        return e.message ?: appContext.getString(R.string.flow_login_error_generic)
    }

    private fun Throwable.firebaseAuthOrCause(): FirebaseAuthException? {
        var t: Throwable? = this
        while (t != null) {
            if (t is FirebaseAuthException) return t
            t = t.cause
        }
        return null
    }

    private fun Throwable.firestorePermissionOrCause(): FirebaseFirestoreException? {
        var t: Throwable? = this
        while (t != null) {
            if (t is FirebaseFirestoreException && t.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                return t
            }
            t = t.cause
        }
        return null
    }

    fun resetState() {
        _state.value = LoginState.Idle
    }

    private companion object {
        private const val TAG = BuildConfig.APPLICATION_ID
        /** Matches [FirebaseAuthException] when Identity Platform blocks user creation / sign-up. */
        private const val ERROR_ADMIN_RESTRICTED_OPERATION = "ERROR_ADMIN_RESTRICTED_OPERATION"
    }
}
