package com.example.ai_assis.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface AuthRepository {
    fun currentUid(): String?
    suspend fun signInAnonymously(): String
    suspend fun signInWithGoogle(idToken: String): String
}

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    override fun currentUid(): String? = auth.currentUser?.uid

    override suspend fun signInAnonymously(): String {
        val existing = auth.currentUser
        if (existing != null) return existing.uid
        val result = auth.signInAnonymously().await()
        return result.user?.uid ?: error("Anonymous sign-in returned null UID")
    }

    override suspend fun signInWithGoogle(idToken: String): String {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val currentUser = auth.currentUser
        return if (currentUser != null && currentUser.isAnonymous) {
            // Link anonymous account → Google account so usage history is preserved
            val result = currentUser.linkWithCredential(credential).await()
            result.user?.uid ?: error("Link with Google returned null UID")
        } else {
            val result = auth.signInWithCredential(credential).await()
            result.user?.uid ?: error("Google sign-in returned null UID")
        }
    }
}
