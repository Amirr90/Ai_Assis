package com.example.ai_assis.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai_assis.data.local.UsageManager
import com.example.ai_assis.data.remote.model.UserUsageRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DailyCount(val label: String, val count: Int)

@HiltViewModel
class UsageAnalyticsViewModel @Inject constructor(
    private val usageManager: UsageManager,
) : ViewModel() {

    /**
     * Same pipeline as the rest of the app: [UsageManager.startObserving] pushes Firestore into this flow.
     * Avoids a duplicate Firestore listener keyed off a one-shot [currentUid] (race + drift vs UsageManager).
     */
    private val userRecordFlow: StateFlow<UserUsageRecord> =
        usageManager.userRecord
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserUsageRecord())

    val subscriptionType: StateFlow<String> = userRecordFlow
        .map { it.subscriptionType }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "free")

    val freeUsageCount: StateFlow<Int> = userRecordFlow
        .map { record -> record.freeUsageCount.coerceAtLeast(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val creditsRemaining: StateFlow<Int> = userRecordFlow
        .map { it.creditsRemaining }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val openAiApiCalls: StateFlow<Long> = userRecordFlow
        .map { it.openAiApiCalls }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val openAiPromptTokensTotal: StateFlow<Long> = userRecordFlow
        .map { it.openAiPromptTokensTotal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val openAiCompletionTokensTotal: StateFlow<Long> = userRecordFlow
        .map { it.openAiCompletionTokensTotal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val openAiTotalTokensTotal: StateFlow<Long> = userRecordFlow
        .map { it.openAiTotalTokensTotal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /**
     * Today’s total: Firestore dailyCounts plus local DataStore cache (updated immediately on each suggestion).
     * Prevents “0 today” while Firestore snapshot lags or misses the first write.
     */
    val todayCount: StateFlow<Int> = combine(
        userRecordFlow,
        usageManager.dailyUsageFlow,
    ) { record, localToday ->
        val todayKey = LocalDate.now().toString()
        val remoteToday = record.dailyCounts[todayKey] ?: 0
        maxOf(remoteToday, localToday)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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
                val label =
                    if (hour == 0) "12am" else if (hour < 12) "${hour}am" else if (hour == 12) "12pm" else "${hour - 12}pm"
                DailyCount(label = label, count = record.hourlyCounts[key] ?: 0)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Last 7 calendar days (oldest → newest). Today’s bar uses max(remote, local daily cache). */
    val last7Days: StateFlow<List<DailyCount>> = combine(
        userRecordFlow,
        usageManager.dailyUsageFlow,
    ) { record, localToday ->
        val today = LocalDate.now()
        val todayStr = today.toString()
        (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val dateStr = date.toString()
            val remote = record.dailyCounts[dateStr] ?: 0
            val count = if (dateStr == todayStr) maxOf(remote, localToday) else remote
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            DailyCount(label = label, count = count)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Last 4 calendar weeks (oldest → newest), each summing 7 days.
     * Week 1 = 28 days ago…22 days ago, Week 4 = 7 days ago…yesterday + today.
     */
    val last4Weeks: StateFlow<List<DailyCount>> = combine(
        userRecordFlow,
        usageManager.dailyUsageFlow,
    ) { record, localToday ->
        val today = LocalDate.now()
        val todayStr = today.toString()
        (3 downTo 0).mapIndexed { idx, weekOffset ->
            val weekEnd = today.minusDays((weekOffset * 7).toLong())
            val weekStart = weekEnd.minusDays(6)
            var sum = 0
            var cursor = weekStart
            while (!cursor.isAfter(weekEnd)) {
                val key = cursor.toString()
                val base = record.dailyCounts[key] ?: 0
                sum += if (key == todayStr) maxOf(base, localToday) else base
                cursor = cursor.plusDays(1)
            }
            DailyCount(label = "Week ${4 - idx}", count = sum)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Peak daily count across stored daily data and today’s local cache. */
    val peakDailyCount: StateFlow<Int> = combine(
        userRecordFlow,
        usageManager.dailyUsageFlow,
    ) { record, localToday ->
        val remotePeak = record.dailyCounts.values.maxOrNull() ?: 0
        maxOf(remotePeak, localToday)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Average suggestions per day across stored daily data (includes blended today). */
    val averagePerDay: StateFlow<Double> = combine(
        userRecordFlow,
        usageManager.dailyUsageFlow,
    ) { record, localToday ->
        val todayStr = LocalDate.now().toString()
        val blendedCounts = record.dailyCounts.toMutableMap()
        val remoteToday = blendedCounts[todayStr] ?: 0
        blendedCounts[todayStr] = maxOf(remoteToday, localToday)
        if (blendedCounts.isEmpty()) 0.0
        else blendedCounts.values.map { it.toDouble() }.average()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)
}
