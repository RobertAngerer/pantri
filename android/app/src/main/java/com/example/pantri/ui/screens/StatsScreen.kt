package com.example.pantri.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

class StatsViewModel : ViewModel() {
    private val _days = MutableStateFlow<List<DaySummary>>(emptyList())
    val days = _days.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val data = ApiClient.api.getDays()
                Cache.saveDays(data)
                _days.value = data
            } catch (_: Exception) {
                _days.value = Cache.loadDays()
            }
        }
    }
}

data class PeriodStats(
    val label: String,
    val dayCount: Int,
    val avgKcal: Double,
    val avgProtein: Double,
    val avgCarbs: Double,
    val avgFat: Double,
    val avgCost: Double,
    val totalCost: Double
)

fun computeStats(days: List<DaySummary>, label: String, since: LocalDate): PeriodStats? {
    val filtered = days.filter {
        try { LocalDate.parse(it.date) >= since } catch (_: Exception) { false }
    }
    if (filtered.isEmpty()) return null
    val n = filtered.size
    return PeriodStats(
        label = label,
        dayCount = n,
        avgKcal = filtered.sumOf { it.day_totals.kcal } / n,
        avgProtein = filtered.sumOf { it.day_totals.protein_g } / n,
        avgCarbs = filtered.sumOf { it.day_totals.carbs_g } / n,
        avgFat = filtered.sumOf { it.day_totals.fat_g } / n,
        avgCost = filtered.sumOf { it.day_totals.cost_eur } / n,
        totalCost = filtered.sumOf { it.day_totals.cost_eur }
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
    val weekStats = computeStats(days, "This week", today.minusDays(6))
    val monthStats = computeStats(days, "This month", today.minusDays(29))
    val allStats = computeStats(days, "All time", LocalDate.MIN)

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
                Text(stats.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
