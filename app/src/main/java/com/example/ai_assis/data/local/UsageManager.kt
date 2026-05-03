package com.example.ai_assis.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.example.ai_assis.data.remote.model.UserUsageRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.usageLimitsDataStore by preferencesDataStore(name = "usage_limits")

@Singleton
class UsageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val firestoreRepo: FirestoreUsageRepository,
    private val authRepository: AuthRepository,
) {
    private val mutex = Mutex()

    // Local DataStore cache for instant UI reads (reflects today's count from Firestore sync)
    val dailyUsageFlow: Flow<Int> = context.usageLimitsDataStore.data
        .map { prefs -> prefs[localDailyCountKey] ?: 0 }
        .distinctUntilChanged()

    // Live Firestore record exposed to analytics ViewModel
    private val _userRecord = MutableStateFlow(UserUsageRecord())
    val userRecord: StateFlow<UserUsageRecord> = _userRecord.asStateFlow()

    /**
     * Start observing Firestore for the given UID. Call once after authentication.
     * Updates both the live [userRecord] and the local DataStore cache.
     */
    suspend fun startObserving(uid: String) {
        firestoreRepo.observeUserRecord(uid).collect { record ->
            _userRecord.value = record
            val todayKey = LocalDate.now().toString()
            val todayCount = record.dailyCounts[todayKey] ?: 0
            context.usageLimitsDataStore.edit { it[localDailyCountKey] = todayCount }
        }
    }

    /**
     * Server-authoritative check. Returns false if the free limit is exhausted
     * and the user has no active paid subscription.
     */
    suspend fun canUseAI(): Boolean {
        return mutex.withLock {
            val uid = authRepository.currentUid() ?: return@withLock true
            firestoreRepo.canGenerate(uid)
        }
    }

    /**
     * Atomically records one suggestion on Firestore and updates the local cache.
     * Pro/credits users are handled transparently inside [FirestoreUsageRepository].
     */
    suspend fun incrementUsage() {
        mutex.withLock {
            val uid = authRepository.currentUid() ?: return@withLock
            val now = LocalDateTime.now()
            val dateKey = now.toLocalDate().toString()
            val hourKey = "${dateKey}_${now.hour.toString().padStart(2, '0')}"
            firestoreRepo.incrementSuggestion(uid, dateKey, hourKey)
            // Optimistically update local cache for immediate UI reflection
            context.usageLimitsDataStore.edit { prefs ->
                val current = prefs[localDailyCountKey] ?: 0
                prefs[localDailyCountKey] = current + 1
            }
        }
    }

    /**
     * Rolls back one local-cache increment when a cloud request returned no reply.
     * Firestore state is authoritative; the local decrement only adjusts the UI.
     */
    suspend fun decrementUsage() {
        mutex.withLock {
            context.usageLimitsDataStore.edit { prefs ->
                val current = prefs[localDailyCountKey] ?: 0
                if (current > 0) prefs[localDailyCountKey] = current - 1
            }
        }
    }

    /**
     * Kept for compatibility — delegates to Firestore subscription write.
     * Prefer calling [FirestoreUsageRepository.setSubscription] directly from the upgrade flow.
     */
    suspend fun setProUser(isPro: Boolean) {
        val uid = authRepository.currentUid() ?: return
        val plan = if (isPro) {
            com.example.ai_assis.presentation.ui.screen.PricingPlan.Monthly
        } else {
            com.example.ai_assis.presentation.ui.screen.PricingPlan.Free
        }
        firestoreRepo.setSubscription(uid, plan)
    }

    companion object {
        /** Kept for UI references (e.g. progress bar max). */
        const val FREE_TOTAL_LIMIT = UserUsageRecord.FREE_SUGGESTION_LIMIT
        private val localDailyCountKey = intPreferencesKey("local_daily_count")
    }
}
