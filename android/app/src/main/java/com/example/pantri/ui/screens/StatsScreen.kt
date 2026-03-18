package com.example.pantri.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.example.pantri.ui.theme.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantri.api.ApiClient
import com.example.pantri.api.Cache
import com.example.pantri.api.DaySummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class StatsViewModel : ViewModel() {
    private val _days = MutableStateFlow<List<DaySummary>>(emptyList())
    val days = _days.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val data = ApiClient.getDays()
                Cache.saveDays(data)
                _days.value = data
            } catch (_: Exception) {
                _days.value = Cache.loadDays()
            }
        }
    }
}

enum class PeriodType { WEEK, MONTH, YEAR }

data class PeriodStats(
    val label: String,
    val type: PeriodType,
    val dayCount: Int,
    val avgKcal: Double,
    val avgProtein: Double,
    val avgCarbs: Double,
    val avgFat: Double,
    val avgCost: Double,
    val totalCost: Double,
    val forecastCost: Double?,   // projected total for the period
    val daysInPeriod: Int,       // total days in this period
    val budgetGoal: Double?      // budget target (only for month)
)

fun computeStats(days: List<DaySummary>, label: String, since: LocalDate, type: PeriodType, periodEnd: LocalDate? = null, budgetMonthly: Double = 600.0): PeriodStats? {
    val today = LocalDate.now()
    val filtered = days.filter {
        try {
            val d = LocalDate.parse(it.date)
            d >= since && d < today
        } catch (_: Exception) { false }
    }
    if (filtered.isEmpty()) return null
    val n = filtered.size
    val totalCost = filtered.sumOf { it.day_totals.cost_eur }
    val avgCost = totalCost / n

    val daysInPeriod = if (periodEnd != null) ChronoUnit.DAYS.between(since, periodEnd).toInt() else n
    val forecastCost = if (type != PeriodType.YEAR && periodEnd != null) {
        avgCost * daysInPeriod
    } else null

    return PeriodStats(
        label = label,
        type = type,
        dayCount = n,
        avgKcal = filtered.sumOf { it.day_totals.kcal } / n,
        avgProtein = filtered.sumOf { it.day_totals.protein_g } / n,
        avgCarbs = filtered.sumOf { it.day_totals.carbs_g } / n,
        avgFat = filtered.sumOf { it.day_totals.fat_g } / n,
        avgCost = avgCost,
        totalCost = totalCost,
        forecastCost = forecastCost,
        daysInPeriod = daysInPeriod,
        budgetGoal = if (type == PeriodType.MONTH) budgetMonthly else null
    )
}

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel()) {
    val days by vm.days.collectAsState()

    if (days.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No data yet", style = MaterialTheme.typography.titleMedium)
                Text("Track some food first", fontSize = 14.sp, color = Color.Gray)
            }
        }
        return
    }

    val today = LocalDate.now()
    // Week: Monday to Sunday
    val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val weekEnd = weekStart.plusDays(7)
    // Month: 1st to end of month
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1)
    // Year: Jan 1 to Dec 31
    val yearStart = today.withDayOfYear(1)

    val budget = ApiClient.getSettings().budget_monthly
    val weekStats = computeStats(days, "This Week", weekStart, PeriodType.WEEK, weekEnd, budget)
    val monthStats = computeStats(days, "This Month", monthStart, PeriodType.MONTH, monthEnd, budget)
    val allStats = computeStats(days, "This Year", yearStart, PeriodType.YEAR, budgetMonthly = budget)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Text("Stats", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        listOfNotNull(weekStats, monthStats, allStats).forEach { stats ->
            item {
                StatsCard(stats)
            }
        }
    }
}

@Composable
fun StatsCard(stats: PeriodStats) {
    val icon = when (stats.type) {
        PeriodType.WEEK -> Icons.Default.DateRange
        PeriodType.MONTH -> Icons.Default.Star
        PeriodType.YEAR -> Icons.Default.Info
    }
    val iconTint = when (stats.type) {
        PeriodType.WEEK -> CarbsAmber
        PeriodType.MONTH -> CostCyan
        PeriodType.YEAR -> WeightPurple
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        icon,
                        contentDescription = stats.label,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stats.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("${stats.dayCount} days", fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(12.dp))

            // Averages per day
            Text("Daily averages", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatValue("${stats.avgKcal.toInt()}", "kcal", CalGreen)
                StatValue("%.0f".format(stats.avgProtein), "protein", ProteinBlue)
                StatValue("%.0f".format(stats.avgCarbs), "carbs", CarbsAmber)
                StatValue("%.0f".format(stats.avgFat), "fat", FatRed)
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Cost
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Avg/day", fontSize = 12.sp, color = Color.Gray)
                    Text("EUR %.2f".format(stats.avgCost), fontWeight = FontWeight.Bold, color = CostCyan)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Total spent", fontSize = 12.sp, color = Color.Gray)
                    Text("EUR %.2f".format(stats.totalCost), fontWeight = FontWeight.Bold, color = CostCyan)
                }
            }

            // Forecast (week and month only)
            if (stats.forecastCost != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Forecast: EUR %.2f".format(stats.forecastCost),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Monthly budget goal
            if (stats.budgetGoal != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                val progress = (stats.totalCost / stats.budgetGoal).toFloat().coerceIn(0f, 1f)
                val overBudget = stats.totalCost > stats.budgetGoal
                val forecastOver = stats.forecastCost != null && stats.forecastCost > stats.budgetGoal
                val progressColor = when {
                    overBudget -> FatRed
                    forecastOver -> CarbsAmber
                    else -> CalGreen
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Budget", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        "EUR %.0f / %.0f".format(stats.totalCost, stats.budgetGoal),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = progressColor
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = progressColor,
                    trackColor = Color.White.copy(alpha = 0.06f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(4.dp))

                val remaining = stats.budgetGoal - stats.totalCost
                if (remaining > 0) {
                    Text(
                        "EUR %.2f remaining".format(remaining),
                        fontSize = 11.sp,
                        color = CalGreen.copy(alpha = 0.8f)
                    )
                    if (stats.forecastCost != null) {
                        val forecastDiff = stats.forecastCost - stats.budgetGoal
                        Text(
                            if (forecastDiff > 0) "On track to overspend by EUR %.2f".format(forecastDiff)
                            else "On track to be EUR %.2f under budget".format(-forecastDiff),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (forecastDiff > 0) FatRed else CalGreen
                        )
                    }
                } else {
                    Text(
                        "EUR %.2f over budget".format(-remaining),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = FatRed
                    )
                }
            }
        }
    }
}

@Composable
fun StatValue(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}
