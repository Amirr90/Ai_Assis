package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.data.remote.AuthRepository
import com.example.ai_assis.data.remote.FirestoreUsageRepository
import com.example.ai_assis.data.remote.model.UserUsageRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DailyCount(val label: String, val count: Int)

@HiltViewModel
class UsageAnalyticsViewModel @Inject constructor(
    usageManager: UsageManager,
    authRepository: AuthRepository,
    firestoreRepo: FirestoreUsageRepository,
) : ViewModel() {

    private val uid: String? = authRepository.currentUid()

    private val userRecordFlow: StateFlow<UserUsageRecord> = if (uid != null) {
        firestoreRepo.observeUserRecord(uid)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserUsageRecord())
    } else {
        // Fallback to local-cache-backed record when not yet authenticated
        usageManager.userRecord
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserUsageRecord())
    }

    val subscriptionType: StateFlow<String> = userRecordFlow
        .map { it.subscriptionType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "free")

    val freeUsageCount: StateFlow<Int> = userRecordFlow
        .map { it.freeUsageCount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val creditsRemaining: StateFlow<Int> = userRecordFlow
        .map { it.creditsRemaining }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Today's total count from Firestore. */
    val todayCount: StateFlow<Int> = userRecordFlow
        .map { record ->
            val todayKey = LocalDate.now().toString()
            record.dailyCounts[todayKey] ?: 0
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Hourly breakdown for today (last 8 hours up to and including current hour).
     * Uses [UserUsageRecord.hourlyCounts] with keys "YYYY-MM-DD_HH".
     */
    val todayHourly: StateFlow<List<DailyCount>> = userRecordFlow
        .map { record ->
            val today = LocalDate.now().toString()
            val currentHour = LocalDateTime.now().hour
            (0..7).map { offset ->
                val hour = (currentHour - 7 + offset).let { if (it < 0) it + 24 else it }
                val hourStr = hour.toString().padStart(2, '0')
                val key = "${today}_$hourStr"
                val label = if (hour == 0) "12am" else if (hour < 12) "${hour}am" else if (hour == 12) "12pm" else "${hour - 12}pm"
                DailyCount(label = label, count = record.hourlyCounts[key] ?: 0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 7 calendar days (oldest → newest). */
    val last7Days: StateFlow<List<DailyCount>> = userRecordFlow
        .map { record ->
            val today = LocalDate.now()
            (6 downTo 0).map { offset ->
                val date = today.minusDays(offset.toLong())
                val count = record.dailyCounts[date.toString()] ?: 0
                val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                DailyCount(label = label, count = count)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Last 4 calendar weeks (oldest → newest), each summing 7 days.
     * Week 1 = 28 days ago…22 days ago, Week 4 = 7 days ago…yesterday + today.
     */
    val last4Weeks: StateFlow<List<DailyCount>> = userRecordFlow
        .map { record ->
            val today = LocalDate.now()
            (3 downTo 0).mapIndexed { idx, weekOffset ->
                val weekEnd = today.minusDays((weekOffset * 7).toLong())
                val weekStart = weekEnd.minusDays(6)
                var sum = 0
                var cursor = weekStart
                while (!cursor.isAfter(weekEnd)) {
                    sum += record.dailyCounts[cursor.toString()] ?: 0
                    cursor = cursor.plusDays(1)
                }
                DailyCount(label = "Week ${4 - idx}", count = sum)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Peak daily count across all stored daily data. */
    val peakDailyCount: StateFlow<Int> = userRecordFlow
        .map { it.dailyCounts.values.maxOrNull() ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Average suggestions per day across stored daily data. */
    val averagePerDay: StateFlow<Double> = userRecordFlow
        .map { record ->
            if (record.dailyCounts.isEmpty()) 0.0
            else record.dailyCounts.values.average()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
}
