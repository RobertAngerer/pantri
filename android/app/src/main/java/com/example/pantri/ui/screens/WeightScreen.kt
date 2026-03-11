package com.example.pantri.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantri.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

val WeightPurple = Color(0xFF9C27B0)

class WeightViewModel : ViewModel() {
    private val _weights = MutableStateFlow<List<WeightEntry>>(emptyList())
    val weights = _weights.asStateFlow()

    private val _days = MutableStateFlow<List<DaySummary>>(emptyList())
    val days = _days.asStateFlow()

    init { load() }

    fun load() {
        // Weight is local-only
        _weights.value = Cache.loadWeight()
        // Calorie data from API (cached)
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

    fun saveWeight(date: String, weightKg: Double) {
        val current = _weights.value.toMutableList()
        val idx = current.indexOfFirst { it.date == date }
        if (idx >= 0) {
            current[idx] = WeightEntry(date, weightKg)
        } else {
            current.add(WeightEntry(date, weightKg))
        }
        val sorted = current.sortedBy { it.date }
        Cache.saveWeight(sorted)
        _weights.value = sorted
    }
}

data class WeekAnalysis(
    val weekLabel: String,
    val startWeight: Double,
    val endWeight: Double,
    val weightChangeKg: Double,
    val avgDailyChangeKg: Double,
    val avgDailyKcal: Double,
    val daysWithFood: Int,
    val estimatedTdee: Double,
    val dailySurplus: Double
)

private fun analyzeWeeks(
    weights: List<WeightEntry>,
    days: List<DaySummary>
): List<WeekAnalysis> {
    if (weights.size < 2) return emptyList()

    val weightByDate = weights.associate { LocalDate.parse(it.date) to it.weight_kg }
    val kcalByDate = days.associate { LocalDate.parse(it.date) to it.day_totals.kcal }

    val allDates = weightByDate.keys.sorted()
    val firstDate = allDates.first()
    val lastDate = allDates.last()

    val weeks = mutableListOf<WeekAnalysis>()
    var weekStart = firstDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    while (!weekStart.isAfter(lastDate)) {
        val weekEnd = weekStart.plusDays(6)

        val weekWeights = weightByDate.filter { (d, _) -> d in weekStart..weekEnd }
            .toSortedMap()

        if (weekWeights.size >= 2) {
            val wStart = weekWeights.values.first()
            val wEnd = weekWeights.values.last()
            val daySpan = weekWeights.keys.first().until(weekWeights.keys.last(), ChronoUnit.DAYS).toInt()

            if (daySpan > 0) {
                val weightChange = wEnd - wStart
                val avgDailyChange = weightChange / daySpan

                val weekKcals = kcalByDate.filter { (d, _) -> d in weekStart..weekEnd }
                val avgDailyKcal = if (weekKcals.isNotEmpty()) weekKcals.values.average() else 0.0

                // TDEE = avg_intake - (weight_change_per_day_kg * 7700)
                val estimatedTdee = if (weekKcals.isNotEmpty()) {
                    avgDailyKcal - (avgDailyChange * 7700)
                } else 0.0

                // surplus = intake - TDEE (positive = gaining, negative = losing)
                val surplus = if (estimatedTdee > 0 && avgDailyKcal > 0) {
                    avgDailyKcal - estimatedTdee
                } else 0.0

                weeks.add(
                    WeekAnalysis(
                        weekLabel = "${weekStart.format(DateTimeFormatter.ofPattern("d MMM"))} - ${weekEnd.format(DateTimeFormatter.ofPattern("d MMM"))}",
                        startWeight = wStart,
                        endWeight = wEnd,
                        weightChangeKg = weightChange,
                        avgDailyChangeKg = avgDailyChange,
                        avgDailyKcal = avgDailyKcal,
                        daysWithFood = weekKcals.size,
                        estimatedTdee = estimatedTdee,
                        dailySurplus = surplus
                    )
                )
            }
        }

        weekStart = weekStart.plusWeeks(1)
    }

    return weeks.reversed()
}

@Composable
fun WeightScreen(vm: WeightViewModel = viewModel()) {
    val weights by vm.weights.collectAsState()
    val days by vm.days.collectAsState()
    val focusManager = LocalFocusManager.current

    var weightInput by remember { mutableStateOf("") }
    val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    val weekAnalyses = remember(weights, days) { analyzeWeeks(weights, days) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Text("Weight", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Input
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Weight (kg)") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            val w = weightInput.replace(",", ".").toDoubleOrNull()
                            if (w != null && w > 0) {
                                vm.saveWeight(today, w)
                                weightInput = ""
                            }
                        }),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val w = weightInput.replace(",", ".").toDoubleOrNull()
                            if (w != null && w > 0) {
                                vm.saveWeight(today, w)
                                weightInput = ""
                            }
                        },
                        enabled = weightInput.replace(",", ".").toDoubleOrNull()?.let { it > 0 } == true
                    ) {
                        Text("Save")
                    }
                }
            }
        }

        // Current weight card
        if (weights.isNotEmpty()) {
            item {
                val latest = weights.last()
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Current", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                "%.1f kg".format(latest.weight_kg),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeightPurple
                            )
                        }
                        if (weights.size >= 2) {
                            val change = latest.weight_kg - weights.first().weight_kg
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Total change", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    "%+.1f kg".format(change),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (change < 0) CalGreen else if (change > 0) FatRed else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chart
        if (weights.size >= 2) {
            item {
                WeightChart(weights)
            }
        }

        // Weekly analysis
        if (weekAnalyses.isNotEmpty()) {
            item {
                Text(
                    "Weekly Analysis",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            weekAnalyses.forEach { week ->
                item {
                    WeekCard(week)
                }
            }
        }

        // Recent entries
        if (weights.isNotEmpty()) {
            item {
                Text(
                    "Recent Entries",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            weights.reversed().take(14).forEach { entry ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(entry.date, fontSize = 14.sp, color = Color.Gray)
                            Text(
                                "%.1f kg".format(entry.weight_kg),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeightChart(weights: List<WeightEntry>) {
    val entries = weights.takeLast(60)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weight Trend", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            val minW = entries.minOf { it.weight_kg }
            val maxW = entries.maxOf { it.weight_kg }
            val range = (maxW - minW).coerceAtLeast(0.5)
            // Add padding so dots aren't clipped at edges
            val padded = range * 0.1

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("%.1f kg".format(maxW), fontSize = 10.sp, color = Color.Gray)
                Text("%.1f kg".format(minW), fontSize = 10.sp, color = Color.Gray)
            }

            val lineColor = WeightPurple
            val gridColor = Color.Gray.copy(alpha = 0.15f)

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val w = size.width
                val h = size.height
                val pt = 8.dp.toPx()
                val pb = 8.dp.toPx()
                val chartH = h - pt - pb

                // Grid
                for (i in 0..4) {
                    val y = pt + chartH * i / 4f
                    drawLine(gridColor, Offset(0f, y), Offset(w, y))
                }

                if (entries.size < 2) return@Canvas

                val stepX = w / (entries.size - 1).toFloat()
                val effMin = minW - padded
                val effRange = range + padded * 2

                val path = Path()
                entries.forEachIndexed { i, entry ->
                    val x = i * stepX
                    val yNorm = ((entry.weight_kg - effMin) / effRange).toFloat()
                    val y = pt + chartH * (1f - yNorm)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))

                entries.forEachIndexed { i, entry ->
                    val x = i * stepX
                    val yNorm = ((entry.weight_kg - effMin) / effRange).toFloat()
                    val y = pt + chartH * (1f - yNorm)
                    drawCircle(lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
                }
            }

            if (entries.size >= 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(entries.first().date.takeLast(5), fontSize = 10.sp, color = Color.Gray)
                    Text(entries.last().date.takeLast(5), fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun WeekCard(week: WeekAnalysis) {
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
                Text(week.weekLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "%+.1f kg".format(week.weightChangeKg),
                    fontWeight = FontWeight.Bold,
                    color = if (week.weightChangeKg < 0) CalGreen
                    else if (week.weightChangeKg > 0) FatRed
                    else Color.Gray
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(week.startWeight), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("start kg", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(week.endWeight), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("end kg", fontSize = 11.sp, color = Color.Gray)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val dailyG = week.avgDailyChangeKg * 1000
                    Text(
                        "%+.0f".format(dailyG),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (dailyG < 0) CalGreen else if (dailyG > 0) FatRed else Color.Gray
                    )
                    Text("g/day", fontSize = 11.sp, color = Color.Gray)
                }
            }

            if (week.daysWithFood > 0) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                Text("Energy Balance", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${week.avgDailyKcal.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = CalGreen
                        )
                        Text("avg intake", fontSize = 11.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${week.estimatedTdee.toInt()}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = ProteinBlue
                        )
                        Text("est. TDEE", fontSize = 11.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val color = if (week.dailySurplus < 0) CalGreen
                            else if (week.dailySurplus > 0) FatRed
                            else Color.Gray
                        Text(
                            "%+d".format(week.dailySurplus.toInt()),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = color
                        )
                        Text("surplus/day", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    "${week.daysWithFood} days with food data",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
