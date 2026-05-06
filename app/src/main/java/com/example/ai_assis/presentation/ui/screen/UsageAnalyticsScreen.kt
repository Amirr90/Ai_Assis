package com.example.ai_assis.presentation.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import androidx.compose.ui.text.font.FontWeight
import java.text.NumberFormat
import com.example.ai_assis.R
import com.example.ai_assis.data.remote.model.SubscriptionType
import com.example.ai_assis.data.remote.model.UserUsageRecord
import com.example.ai_assis.presentation.viewmodel.DailyCount
import com.example.ai_assis.presentation.viewmodel.UsageAnalyticsViewModel
import kotlin.math.roundToInt

private enum class UsagePeriod { Today, Weekly, Monthly }

private data class BarSelection(val label: String, val replies: Int)

@Composable
fun UsageAnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: UsageAnalyticsViewModel = hiltViewModel(),
) {
    val subscriptionType by viewModel.subscriptionType.collectAsState()
    val freeUsageCount by viewModel.freeUsageCount.collectAsState()
    val creditsRemaining by viewModel.creditsRemaining.collectAsState()
    val todayCount by viewModel.todayCount.collectAsState()
    val todayHourly by viewModel.todayHourly.collectAsState()
    val last7Days by viewModel.last7Days.collectAsState()
    val last4Weeks by viewModel.last4Weeks.collectAsState()
    val peakDaily by viewModel.peakDailyCount.collectAsState()
    val averagePerDay by viewModel.averagePerDay.collectAsState()
    val openAiApiCalls by viewModel.openAiApiCalls.collectAsState()
    val openAiPromptTokens by viewModel.openAiPromptTokensTotal.collectAsState()
    val openAiCompletionTokens by viewModel.openAiCompletionTokensTotal.collectAsState()
    val openAiTotalTokens by viewModel.openAiTotalTokensTotal.collectAsState()

    val limit = UserUsageRecord.FREE_SUGGESTION_LIMIT

    var selectedPeriod by remember { mutableStateOf(UsagePeriod.Today) }
    var barSelection by remember { mutableStateOf<BarSelection?>(null) }

    val chartData: List<DailyCount> = when (selectedPeriod) {
        UsagePeriod.Today -> todayHourly
        UsagePeriod.Weekly -> last7Days
        UsagePeriod.Monthly -> last4Weeks
    }

    LaunchedEffect(selectedPeriod) { barSelection = null }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        SummarySection(
            subscriptionType = subscriptionType,
            freeUsageCount = freeUsageCount,
            creditsRemaining = creditsRemaining,
            todayCount = todayCount,
            limit = limit,
        )
        Spacer(modifier = Modifier.height(16.dp))
        OpenAiUsageSection(
            apiCalls = openAiApiCalls,
            promptTokens = openAiPromptTokens,
            completionTokens = openAiCompletionTokens,
            totalTokens = openAiTotalTokens,
        )
        Spacer(modifier = Modifier.height(20.dp))
        FilterChips(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = { selectedPeriod = it },
        )
        Spacer(modifier = Modifier.height(24.dp))
        GraphSection(
            chartData = chartData,
            period = selectedPeriod,
            onBarSelected = { barSelection = it },
        )
        GraphSelectionHint(selection = barSelection, period = selectedPeriod)
        Spacer(modifier = Modifier.height(16.dp))
        InsightsSection(
            subscriptionType = subscriptionType,
            freeUsageCount = freeUsageCount,
            limit = limit,
            todayCount = todayCount,
            peakDaily = peakDaily,
            averagePerDay = averagePerDay,
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SummarySection(
    subscriptionType: String,
    freeUsageCount: Int,
    creditsRemaining: Int,
    todayCount: Int,
    limit: Int,
) {
    val bg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (subscriptionType) {
            SubscriptionType.MONTHLY, SubscriptionType.YEARLY -> {
                Text(
                    text = "Pro Plan – Today's Usage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$todayCount replies generated today",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Unlimited — ${subscriptionType.replaceFirstChar { it.uppercase() }} plan active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            SubscriptionType.CREDITS -> {
                Text(
                    text = "Credits – Today's Usage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$todayCount replies today  •  $creditsRemaining credits left",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val creditProgress = (creditsRemaining.toFloat() / 50f).coerceIn(0f, 1f)
                val animatedCreditProgress by animateFloatAsState(
                    targetValue = creditProgress,
                    animationSpec = tween(650),
                    label = "credit_progress",
                )
                LinearProgressIndicator(
                    progress = { animatedCreditProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    strokeCap = StrokeCap.Round,
                )
            }
            else -> {
                // Free tier
                val used = freeUsageCount.coerceAtMost(limit)
                val progress = used.toFloat() / limit.coerceAtLeast(1).toFloat()
                val animatedProgress by animateFloatAsState(
                    targetValue = progress.coerceIn(0f, 1f),
                    animationSpec = tween(650),
                    label = "usage_progress",
                )
                Text(
                    text = "Free Usage",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$used / $limit total suggestions used",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (used >= limit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    strokeCap = StrokeCap.Round,
                )
                if (used >= limit) {
                    Text(
                        text = "Free limit reached — upgrade to keep generating",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Text(
                        text = "${limit - used} suggestions remaining on free plan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    selectedPeriod: UsagePeriod,
    onPeriodSelected: (UsagePeriod) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UsagePeriod.entries.forEach { period ->
            UsageFilterChip(
                label = when (period) {
                    UsagePeriod.Today -> "Today"
                    UsagePeriod.Weekly -> "Weekly"
                    UsagePeriod.Monthly -> "Monthly"
                },
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
            )
        }
    }
}

@Composable
private fun UsageFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(220),
        label = "chip_bg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "chip_fg",
    )
    Surface(shape = RoundedCornerShape(50.dp), color = bg, modifier = Modifier.clickable(onClick = onClick)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GraphSection(
    chartData: List<DailyCount>,
    period: UsagePeriod,
    onBarSelected: (BarSelection?) -> Unit,
) {
    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val onBarSelectedState by rememberUpdatedState(onBarSelected)

    val entries = chartData.mapIndexed { i, d -> BarEntry(i.toFloat(), d.count.toFloat()) }
    val xLabels = chartData.map { it.label }.toTypedArray()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Usage Overview",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (entries.isEmpty() || entries.all { it.y == 0f }) {
            Text(
                text = "No data yet for this period.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 60.dp).align(Alignment.CenterHorizontally),
            )
        } else {
            AndroidView(
                factory = { context ->
                    BarChart(context).apply {
                        description.isEnabled = false
                        legend.isEnabled = false
                        setScaleEnabled(false)
                        setPinchZoom(false)
                        setDrawGridBackground(false)
                        axisRight.isEnabled = false
                        axisLeft.axisMinimum = 0f
                        axisLeft.setDrawGridLines(false)
                        axisLeft.textColor = labelColor
                        axisLeft.setDrawAxisLine(false)
                        axisLeft.labelCount = 4
                        xAxis.position = XAxis.XAxisPosition.BOTTOM
                        xAxis.setDrawGridLines(false)
                        xAxis.textColor = labelColor
                        xAxis.granularity = 1f
                        extraBottomOffset = 8f
                    }
                },
                update = { chart ->
                    chart.xAxis.setLabelCount(xLabels.size.coerceAtLeast(1), false)
                    chart.xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)
                    val set = BarDataSet(entries, "").apply {
                        color = primaryArgb
                        setDrawValues(false)
                    }
                    val data = BarData(set).also {
                        it.barWidth = when (period) {
                            UsagePeriod.Monthly -> 0.35f
                            UsagePeriod.Today -> 0.45f
                            UsagePeriod.Weekly -> 0.55f
                        }
                    }
                    chart.data = data
                    chart.axisLeft.textColor = labelColor
                    chart.xAxis.textColor = labelColor
                    chart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                        override fun onValueSelected(e: Entry?, h: Highlight?) {
                            if (e == null) return
                            val idx = e.x.toInt().coerceIn(0, xLabels.lastIndex.coerceAtLeast(0))
                            onBarSelectedState(BarSelection(label = xLabels.getOrElse(idx) { "" }, replies = e.y.toInt()))
                        }
                        override fun onNothingSelected() = onBarSelectedState(null)
                    })
                    chart.invalidate()
                },
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
    }
}

@Composable
private fun OpenAiUsageSection(
    apiCalls: Long,
    promptTokens: Long,
    completionTokens: Long,
    totalTokens: Long,
) {
    val numberFormat = remember { NumberFormat.getIntegerInstance() }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.analytics_openai_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OpenAiStatRow(stringResource(R.string.analytics_openai_api_calls), formatCompactNumber(apiCalls, numberFormat))
            OpenAiStatRow(stringResource(R.string.analytics_openai_prompt_tokens), formatCompactNumber(promptTokens, numberFormat))
            OpenAiStatRow(
                stringResource(R.string.analytics_openai_completion_tokens),
                formatCompactNumber(completionTokens, numberFormat),
            )
            OpenAiStatRow(stringResource(R.string.analytics_openai_total_tokens), formatCompactNumber(totalTokens, numberFormat))
            Text(
                text = stringResource(R.string.analytics_openai_sync_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OpenAiStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatCompactNumber(value: Long, numberFormat: NumberFormat): String {
    val absValue = kotlin.math.abs(value.toDouble())
    return when {
        absValue >= 1_000_000_000 -> formatScaled(value, 1_000_000_000.0, "B")
        absValue >= 1_000_000 -> formatScaled(value, 1_000_000.0, "M")
        absValue >= 1_000 -> formatScaled(value, 1_000.0, "K")
        else -> numberFormat.format(value)
    }
}

private fun formatScaled(value: Long, divisor: Double, suffix: String): String {
    val scaled = value / divisor
    val oneDecimal = String.format("%.1f", scaled)
    val compact = oneDecimal.removeSuffix(".0")
    return "$compact$suffix"
}

@Composable
private fun GraphSelectionHint(selection: BarSelection?, period: UsagePeriod) {
    val periodHint = when (period) {
        UsagePeriod.Today -> "Tap an hour slot to see replies generated in that hour."
        UsagePeriod.Weekly -> "Tap a day to see replies generated that day."
        UsagePeriod.Monthly -> "Tap a week to see total replies generated that week."
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (selection != null) {
            Text(
                text = "${selection.label}: ${selection.replies} replies",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = periodHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InsightsSection(
    subscriptionType: String,
    freeUsageCount: Int,
    limit: Int,
    todayCount: Int,
    peakDaily: Int,
    averagePerDay: Double,
) {
    val remaining = when (subscriptionType) {
        SubscriptionType.MONTHLY, SubscriptionType.YEARLY -> "Unlimited"
        SubscriptionType.CREDITS -> "See credits"
        else -> "${(limit - freeUsageCount).coerceAtLeast(0)} left"
    }
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        InsightRow(label = "Today", value = "$todayCount replies")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        InsightRow(label = "Peak day", value = if (peakDaily > 0) "$peakDaily replies" else "—")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        InsightRow(label = "Daily average", value = if (averagePerDay > 0) "${averagePerDay.roundToInt()} / day" else "—")
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        InsightRow(label = "Remaining", value = remaining)
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
