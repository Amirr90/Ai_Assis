package com.example.ai_assis.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.example.ai_assis.data.remote.model.OpenAiTokenUsage
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
import kotlinx.coroutines.flow.update
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
     * Persists OpenAI token usage and increments API call count after a successful chat/completions response.
     */
    suspend fun recordOpenAiUsage(usage: OpenAiTokenUsage?, chargedApiCall: Boolean) {
        if (!chargedApiCall) return
        val uid = authRepository.currentUid() ?: return
        val prompt = usage?.promptTokens?.toLong() ?: 0L
        val completion = usage?.completionTokens?.toLong() ?: 0L
        val total = usage?.totalTokens?.toLong() ?: 0L

        mutex.withLock {
            // Optimistic local update so Analytics reflects usage instantly.
            _userRecord.update { current ->
                current.copy(
                    openAiApiCalls = current.openAiApiCalls + 1L,
                    openAiPromptTokensTotal = current.openAiPromptTokensTotal + prompt,
                    openAiCompletionTokensTotal = current.openAiCompletionTokensTotal + completion,
                    openAiTotalTokensTotal = current.openAiTotalTokensTotal + total,
                )
            }
        }

        val result = firestoreRepo.recordOpenAiUsage(uid, usage)
        if (result.isFailure) {
            mutex.withLock {
                // Roll back optimistic counters if Firestore persistence fails.
                _userRecord.update { current ->
                    current.copy(
                        openAiApiCalls = (current.openAiApiCalls - 1L).coerceAtLeast(0L),
                        openAiPromptTokensTotal = (current.openAiPromptTokensTotal - prompt).coerceAtLeast(0L),
                        openAiCompletionTokensTotal = (current.openAiCompletionTokensTotal - completion).coerceAtLeast(0L),
                        openAiTotalTokensTotal = (current.openAiTotalTokensTotal - total).coerceAtLeast(0L),
                    )
                }
            }
        }
    }

    /**
     * Server-authoritative check. Returns false if the free limit is exhausted
     * and the user has no active paid subscription.
     */
    suspend fun canUseAI(): Boolean {
        return mutex.withLock {
            val uid = authRepository.currentUid() ?: return@withLock true
            // Firestore check evaluates activePlanId/entitlements with legacy subscription fallback.
            firestoreRepo.canGenerate(uid)
        }
    }

    fun activePlanId(): String = _userRecord.value.resolvedPlanId()

    fun hasUnlimitedPlanAccess(): Boolean = _userRecord.value.hasUnlimitedSuggestions()

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
     * Previously toggled subscription from the device. Paid access is granted only via
     * Razorpay + Firebase Cloud Functions; this stub remains for callers that referenced the API.
     */
    suspend fun setProUser(@Suppress("UNUSED_PARAMETER") isPro: Boolean) {
        /* no-op — entitlements enforced server-side */
    }

    companion object {
        /** Kept for UI references (e.g. progress bar max). */
        const val FREE_TOTAL_LIMIT = UserUsageRecord.FREE_SUGGESTION_LIMIT
        private val localDailyCountKey = intPreferencesKey("local_daily_count")
    }
}
