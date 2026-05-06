package com.example.ai_assis.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuthException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Bootstraps Firebase Auth and Firestore observation at app start.
 * Instantiated once at the root of the nav graph.
 */
@HiltViewModel
class AppInitViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val firestoreRepo: FirestoreUsageRepository,
    private val usageManager: UsageManager,
) : ViewModel() {

    /**
     * Called when the user reaches the main dashboard — either after login
     * or on a cold start where intro flow was already completed.
     * Ensures an anonymous auth session exists and starts streaming the Firestore usage record.
     */
    fun ensureAuthAndObserve() {
        viewModelScope.launch {
            try {
                val uid = authRepository.currentUid()
                    ?: authRepository.signInAnonymously()
                firestoreRepo.getOrCreateUser(uid)
                usageManager.startObserving(uid)
            } catch (e: FirebaseAuthException) {
                Log.e(
                    TAG,
                    "Firebase Auth failed (${e.errorCode}): ${e.message}. " +
                        "In Firebase Console: enable Authentication, turn on Anonymous sign-in, " +
                        "create Firestore if needed, and re-download google-services.json (add SHA-1 for Google).",
                    e,
                )
            } catch (e: FirebaseException) {
                Log.e(TAG, "Firebase error: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "Auth/Firestore bootstrap failed", e)
            }
        }
    }

    private companion object {
        private const val TAG = "AppInitViewModel"
    }
}
