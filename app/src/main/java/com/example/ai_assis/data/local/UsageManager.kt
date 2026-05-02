package com.example.ai_assis.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.usageLimitsDataStore by preferencesDataStore(name = "usage_limits")

@Singleton
class UsageManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val mutex = Mutex()

    suspend fun resetIfNeeded() {
        mutex.withLock { resetIfNeededLocked() }
    }

    suspend fun canUseAI(): Boolean {
        return mutex.withLock {
            resetIfNeededLocked()
            val prefs = context.usageLimitsDataStore.data.first()
            if (prefs[isProUserKey] == true) return@withLock true
            val count = prefs[dailyUsageCountKey] ?: 0
            count < FREE_DAILY_LIMIT
        }
    }

    suspend fun incrementUsage() {
        mutex.withLock {
            resetIfNeededLocked()
            val prefs = context.usageLimitsDataStore.data.first()
            if (prefs[isProUserKey] == true) return@withLock
            context.usageLimitsDataStore.edit { editable ->
                val current = editable[dailyUsageCountKey] ?: 0
                editable[dailyUsageCountKey] = current + 1
            }
        }
    }

    suspend fun setProUser(isPro: Boolean) {
        mutex.withLock {
            context.usageLimitsDataStore.edit { editable ->
                editable[isProUserKey] = isPro
            }
        }
    }

    private suspend fun resetIfNeededLocked() {
        val prefs = context.usageLimitsDataStore.data.first()
        val lastResetTime = prefs[lastResetTimeKey] ?: 0L
        val todayStart = startOfTodayMillis()
        if (lastResetTime < todayStart) {
            context.usageLimitsDataStore.edit { editable ->
                editable[dailyUsageCountKey] = 0
                editable[lastResetTimeKey] = todayStart
            }
        }
    }

    private fun startOfTodayMillis(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private companion object {
        val dailyUsageCountKey = intPreferencesKey("daily_usage_count")
        val lastResetTimeKey = longPreferencesKey("last_reset_time")
        val isProUserKey = booleanPreferencesKey("is_pro_user")

        const val FREE_DAILY_LIMIT = 30
    }
}
