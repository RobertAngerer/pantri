package com.example.pantri.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.pantri.ui.theme.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantri.R
import com.example.pantri.WeightReminderScheduler
import com.example.pantri.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.roundToInt

const val GOAL_WEIGHT_KG = 95.0
val PLAN_START_DATE: LocalDate = LocalDate.of(2026, 3, 11)
const val PLAN_START_WEIGHT = 108.0
val PLAN_GOAL_DATE: LocalDate = LocalDate.of(2026, 7, 1)
val PLAN_TOTAL_DAYS: Long = ChronoUnit.DAYS.between(PLAN_START_DATE, PLAN_GOAL_DATE) // 112
const val PLAN_TOTAL_LOSS = PLAN_START_WEIGHT - GOAL_WEIGHT_KG // 13.0

fun plannedWeightAt(date: LocalDate): Double {
    val daysSinceStart = ChronoUnit.DAYS.between(PLAN_START_DATE, date).coerceIn(0, PLAN_TOTAL_DAYS)
    return PLAN_START_WEIGHT - (daysSinceStart.toDouble() / PLAN_TOTAL_DAYS * PLAN_TOTAL_LOSS)
}

class WeightViewModel : ViewModel() {
    private val _weights = MutableStateFlow<List<WeightEntry>>(emptyList())
    val weights = _weights.asStateFlow()

    private val _days = MutableStateFlow<List<DaySummary>>(emptyList())
    val days = _days.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { load() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            loadData()
            _refreshing.value = false
        }
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            loadData()
            _loading.value = false
        }
    }

    private suspend fun loadData() {
            // Load weight from Supabase, fall back to cache
            try {
                val remote = ApiClient.getWeight()
                val local = Cache.loadWeight()
                // Merge: remote wins for same date, keep local-only entries
                val remoteDates = remote.map { it.date }.toSet()
                val merged = (remote + local.filter { it.date !in remoteDates }).sortedBy { it.date }
                Cache.saveWeight(merged)
                _weights.value = merged
            } catch (_: Exception) {
                _weights.value = Cache.loadWeight()
            }

            // Calorie data from Supabase
            try {
                val data = ApiClient.getDays()
                Cache.saveDays(data)
                _days.value = data
            } catch (_: Exception) {
                _days.value = Cache.loadDays()
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

        viewModelScope.launch {
            try {
                ApiClient.postWeight(date, weightKg)
            } catch (_: Exception) {
                // saved locally, will sync next time
            }
        }
    }
}

data class WeekAnalysis(
    val weekLabel: String,
    val avgWeight: Double,
    val startWeight: Double,
    val endWeight: Double,
    val weightChangeKg: Double,
    val avgDailyChangeKg: Double,
    val avgDailyKcal: Double,
    val daysWithFood: Int,
    val daysWeighed: Int,
    val estimatedTdee: Double,
    val dailySurplus: Double
)

private fun analyzeWeeks(
    weights: List<WeightEntry>,
    days: List<DaySummary>
): List<WeekAnalysis> {
    if (weights.size < 2) return emptyList()

    val today = LocalDate.now()
    val weightByDate = weights.associate { LocalDate.parse(it.date) to it.weight_kg }
    val kcalByDate = days.associate { LocalDate.parse(it.date) to it.day_totals.kcal }
        .filterKeys { it < today }

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
                        avgWeight = weekWeights.values.average(),
                        startWeight = wStart,
                        endWeight = wEnd,
                        weightChangeKg = weightChange,
                        avgDailyChangeKg = avgDailyChange,
                        avgDailyKcal = avgDailyKcal,
                        daysWithFood = weekKcals.size,
                        daysWeighed = weekWeights.size,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(vm: WeightViewModel = viewModel()) {
    val weights by vm.weights.collectAsState()
    val days by vm.days.collectAsState()
    val loading by vm.loading.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val context = LocalContext.current

    var showWeightDialog by remember { mutableStateOf(false) }

    val weekAnalyses = remember(weights, days) { analyzeWeeks(weights, days) }

    var reminderEnabled by remember { mutableStateOf(WeightReminderScheduler.isEnabled(context)) }
    var reminderHour by remember { mutableIntStateOf(WeightReminderScheduler.getHour(context)) }
    var reminderMinute by remember { mutableIntStateOf(WeightReminderScheduler.getMinute(context)) }
    var showTimePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            WeightReminderScheduler.schedule(context, reminderHour, reminderMinute)
            reminderEnabled = true
        }
    }

    fun enableReminder(hour: Int, minute: Int) {
        reminderHour = hour
        reminderMinute = minute
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            WeightReminderScheduler.schedule(context, hour, minute)
            reminderEnabled = true
        }
    }

    if (showTimePicker) {
        val tps = rememberTimePickerState(initialHour = reminderHour, initialMinute = reminderMinute)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    showTimePicker = false
                    enableReminder(tps.hour, tps.minute)
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            title = { Text("Reminder time") },
            text = { TimePicker(state = tps) }
        )
    }

    if (showWeightDialog) {
        WeightInputSheet(
            initialWeight = weights.lastOrNull()?.weight_kg ?: 100.0,
            onDismiss = { showWeightDialog = false },
            onSave = { date, w ->
                vm.saveWeight(date, w)
                showWeightDialog = false
            }
        )
    }

    // Initial loading — show spinning app icon
    if (loading && weights.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SpinningAppIcon()
        }
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showWeightDialog = true },
                containerColor = WeightPurple,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log weight")
            }
        },
        containerColor = Color.Transparent
    ) { scaffoldPadding ->
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
    ) {
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

        // Goal weight projection
        if (weights.size >= 2) {
            item {
                val latest = weights.last()
                val remaining = latest.weight_kg - GOAL_WEIGHT_KG

                // Calculate avg daily change from last 14+ days of data
                val recentWeights = weights.takeLast(30)
                val firstW = recentWeights.first()
                val lastW = recentWeights.last()
                val daysBetween = LocalDate.parse(firstW.date).until(LocalDate.parse(lastW.date), ChronoUnit.DAYS)
                val avgDailyChange = if (daysBetween > 0) (lastW.weight_kg - firstW.weight_kg) / daysBetween else 0.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface2),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Goal", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            Text("%.0f kg".format(GOAL_WEIGHT_KG), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CalGreen)
                        }
                        Spacer(Modifier.height(8.dp))

                        // Progress bar
                        if (remaining > 0) {
                            val startWeight = weights.first().weight_kg
                            val totalToLose = startWeight - GOAL_WEIGHT_KG
                            val lost = startWeight - latest.weight_kg
                            val progressFraction = if (totalToLose > 0) (lost / totalToLose).toFloat().coerceIn(0f, 1f) else 0f

                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = CalGreen,
                                trackColor = Color.White.copy(alpha = 0.06f),
                                strokeCap = StrokeCap.Round,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("%.1f kg to go".format(remaining), fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f))
                        } else {
                            Text("Goal reached!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CalGreen)
                        }

                        // On-track indicator
                        Spacer(Modifier.height(8.dp))
                        val today = LocalDate.now()
                        val plannedNow = plannedWeightAt(today)
                        val diff = latest.weight_kg - plannedNow
                        val onTrackColor = if (diff <= 0) CalGreen else FatRed
                        val onTrackLabel = if (diff <= 0) "Ahead of plan" else "Behind plan"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Plan target today", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                                Text("%.1f kg".format(plannedNow), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(onTrackLabel, fontSize = 12.sp, color = onTrackColor, fontWeight = FontWeight.Bold)
                                Text(
                                    "%+.1f kg".format(diff),
                                    fontSize = 16.sp, fontWeight = FontWeight.Bold, color = onTrackColor
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // Required rate
                        val weeksLeft = ChronoUnit.DAYS.between(today, PLAN_GOAL_DATE) / 7.0
                        if (weeksLeft > 0 && remaining > 0) {
                            val requiredPerWeek = remaining / weeksLeft
                            Text(
                                "Need %.2f kg/week (%.0f g/day) to reach %.0f kg by %s".format(
                                    requiredPerWeek,
                                    requiredPerWeek / 7 * 1000,
                                    GOAL_WEIGHT_KG,
                                    PLAN_GOAL_DATE.format(DateTimeFormatter.ofPattern("d MMM"))
                                ),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        Spacer(Modifier.height(8.dp))

                        if (avgDailyChange < 0 && remaining > 0) {
                            val daysToGoal = (remaining / -avgDailyChange).toLong()
                            val projectedDate = LocalDate.now().plusDays(daysToGoal)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Projected date", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                                    Text(
                                        projectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("At current rate", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                                    Text(
                                        "%+.0f g/day".format(avgDailyChange * 1000),
                                        fontSize = 14.sp, color = CalGreen
                                    )
                                }
                            }
                        } else if (remaining > 0) {
                            Text(
                                if (avgDailyChange >= 0) "Currently gaining — no projection"
                                else "Not enough data for projection",
                                fontSize = 13.sp, color = Color.White.copy(alpha = 0.4f)
                            )
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

        // Weekly averages chart
        if (weekAnalyses.size >= 2) {
            item {
                WeeklyAveragesChart(weekAnalyses.reversed())
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

        // Daily reminder
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Daily reminder", fontSize = 14.sp)
                        if (reminderEnabled) {
                            Text(
                                "%d:%02d".format(reminderHour, reminderMinute),
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (reminderEnabled) {
                            TextButton(onClick = { showTimePicker = true }) {
                                Text("Change", fontSize = 12.sp)
                            }
                        }
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    showTimePicker = true
                                } else {
                                    WeightReminderScheduler.cancel(context)
                                    reminderEnabled = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }
    }
    }
}

@Composable
fun SpinningAppIcon(size: Int = 64) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    Image(
        painter = painterResource(id = R.drawable.ic_loading),
        contentDescription = "Loading",
        modifier = Modifier
            .size(size.dp)
            .rotate(rotation)
    )
}

@Composable
fun WeightChart(weights: List<WeightEntry>) {
    if (weights.size < 2) return

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    // Date range: from first entry to plan goal date (so you can scroll into the future)
    val firstDate = LocalDate.parse(weights.first().date)
    val chartEndDate = maxOf(LocalDate.parse(weights.last().date).plusDays(7), PLAN_GOAL_DATE)
    val totalDays = ChronoUnit.DAYS.between(firstDate, chartEndDate).toInt().coerceAtLeast(1)

    // Y range: include all data + goal + plan start
    val dataMin = weights.minOf { it.weight_kg }
    val dataMax = weights.maxOf { it.weight_kg }
    val yMin = minOf(dataMin, GOAL_WEIGHT_KG) - 1.0
    val yMax = maxOf(dataMax, PLAN_START_WEIGHT) + 1.0
    val yRange = yMax - yMin

    // Y-axis: nice round grid lines every 2kg
    val gridStep = 2.0
    val gridBottom = (Math.floor(yMin / gridStep) * gridStep).toInt()
    val gridTop = (Math.ceil(yMax / gridStep) * gridStep).toInt()

    // Chart sizing
    val chartHeight = 300.dp
    val yAxisWidth = 40.dp
    val xAxisHeight = 24.dp
    val dpPerDay = 10.dp // horizontal density — makes chart scrollable
    val chartWidth = with(density) { (dpPerDay * totalDays) }

    // Weight data indexed by days-since-first
    val weightByDay = weights.associate {
        val d = LocalDate.parse(it.date)
        ChronoUnit.DAYS.between(firstDate, d).toInt() to it.weight_kg
    }

    // Scroll state — start scrolled to show the latest data
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        // Scroll to show today's position
        val todayDay = ChronoUnit.DAYS.between(firstDate, LocalDate.now()).toInt()
        val todayPx = with(density) { (dpPerDay * todayDay).toPx() }
        val viewportWidth = with(density) { 300.dp.toPx() } // approximate
        scrollState.scrollTo((todayPx - viewportWidth / 2).toInt().coerceAtLeast(0))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp, end = 16.dp)) {
            Text(
                "Weight Trend",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 12.dp)
            )

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 4.dp, bottom = 8.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(WeightPurple) }
                    Text("  Actual", fontSize = 10.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(Color(0xFFFFA726)) }
                    Text("  Plan", fontSize = 10.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(CalGreen) }
                    Text("  Goal", fontSize = 10.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(WeightPurple.copy(alpha = 0.4f)) }
                    Text("  Trend", fontSize = 10.sp, color = Color.Gray)
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                // Y-axis labels (fixed, doesn't scroll)
                val yLabelStyle = TextStyle(fontSize = 9.sp, color = Color.Gray)
                Canvas(
                    modifier = Modifier
                        .width(yAxisWidth)
                        .height(chartHeight)
                ) {
                    val h = size.height
                    val pt = 4.dp.toPx()
                    val pb = 4.dp.toPx()
                    val cH = h - pt - pb

                    var kg = gridBottom
                    while (kg <= gridTop) {
                        val yNorm = ((kg - yMin) / yRange).toFloat()
                        val y = pt + cH * (1f - yNorm)
                        val label = textMeasurer.measure("$kg", yLabelStyle)
                        drawText(
                            label,
                            topLeft = Offset(
                                size.width - label.size.width - 4.dp.toPx(),
                                y - label.size.height / 2f
                            )
                        )
                        kg += gridStep.toInt()
                    }
                }

                // Scrollable chart area
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(chartHeight)
                            .horizontalScroll(scrollState)
                    ) {
                        val gridColor = Color.Gray.copy(alpha = 0.12f)
                        val goalColor = CalGreen
                        val planColor = Color(0xFFFFA726)

                        Canvas(
                            modifier = Modifier
                                .width(chartWidth)
                                .fillMaxHeight()
                        ) {
                            val w = size.width
                            val h = size.height
                            val pt = 4.dp.toPx()
                            val pb = 4.dp.toPx()
                            val cH = h - pt - pb
                            val pxPerDay = w / totalDays.toFloat()

                            fun dayToX(day: Int): Float = day * pxPerDay
                            fun weightToY(kg: Double): Float {
                                val yNorm = ((kg - yMin) / yRange).toFloat()
                                return pt + cH * (1f - yNorm)
                            }

                            // Horizontal grid lines every 2kg
                            var kg = gridBottom
                            while (kg <= gridTop) {
                                val y = weightToY(kg.toDouble())
                                drawLine(gridColor, Offset(0f, y), Offset(w, y))
                                kg += gridStep.toInt()
                            }

                            // Vertical grid lines — every Monday
                            var d = firstDate
                            while (!d.isAfter(chartEndDate)) {
                                if (d.dayOfWeek == DayOfWeek.MONDAY) {
                                    val day = ChronoUnit.DAYS.between(firstDate, d).toInt()
                                    val x = dayToX(day)
                                    drawLine(gridColor, Offset(x, 0f), Offset(x, h))
                                }
                                d = d.plusDays(1)
                            }

                            // "Today" vertical line
                            val todayDay = ChronoUnit.DAYS.between(firstDate, LocalDate.now()).toInt()
                            if (todayDay in 0..totalDays) {
                                val tx = dayToX(todayDay)
                                drawLine(
                                    Color.White.copy(alpha = 0.15f),
                                    Offset(tx, 0f), Offset(tx, h),
                                    strokeWidth = 2.dp.toPx()
                                )
                            }

                            // Goal line — dashed horizontal
                            val goalY = weightToY(GOAL_WEIGHT_KG)
                            val dashLen = 8.dp.toPx()
                            val gapLen = 6.dp.toPx()
                            var dx = 0f
                            while (dx < w) {
                                val end = (dx + dashLen).coerceAtMost(w)
                                drawLine(goalColor.copy(alpha = 0.4f), Offset(dx, goalY), Offset(end, goalY), strokeWidth = 1.5.dp.toPx())
                                dx += dashLen + gapLen
                            }

                            // Plan line — full trajectory from plan start to plan goal
                            val planStartDay = ChronoUnit.DAYS.between(firstDate, PLAN_START_DATE).toInt().coerceIn(0, totalDays)
                            val planEndDay = ChronoUnit.DAYS.between(firstDate, PLAN_GOAL_DATE).toInt().coerceIn(0, totalDays)
                            val planPath = Path().apply {
                                moveTo(dayToX(planStartDay), weightToY(PLAN_START_WEIGHT))
                                lineTo(dayToX(planEndDay), weightToY(GOAL_WEIGHT_KG))
                            }
                            drawPath(
                                planPath, planColor.copy(alpha = 0.6f),
                                style = Stroke(
                                    width = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                        floatArrayOf(10.dp.toPx(), 6.dp.toPx())
                                    )
                                )
                            )

                            // Data line
                            val sortedDays = weightByDay.keys.sorted()
                            if (sortedDays.size >= 2) {
                                val linePath = Path()
                                sortedDays.forEachIndexed { i, day ->
                                    val x = dayToX(day)
                                    val y = weightToY(weightByDay[day]!!)
                                    if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
                                }
                                drawPath(linePath, WeightPurple, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
                            }

                            // Linear regression trend line through all data
                            if (sortedDays.size >= 2) {
                                val n = sortedDays.size.toDouble()
                                val xMean = sortedDays.map { it.toDouble() }.average()
                                val yMean = sortedDays.map { weightByDay[it]!! }.average()
                                var num = 0.0
                                var den = 0.0
                                sortedDays.forEach { day ->
                                    val xd = day.toDouble() - xMean
                                    val yd = weightByDay[day]!! - yMean
                                    num += xd * yd
                                    den += xd * xd
                                }
                                if (den > 0) {
                                    val slope = num / den // kg per day
                                    val intercept = yMean - slope * xMean
                                    // Draw from first data day to last data day (extended a bit)
                                    val trendStartDay = sortedDays.first()
                                    val trendEndDay = totalDays // extend to chart end
                                    val trendStartW = intercept + slope * trendStartDay
                                    val trendEndW = intercept + slope * trendEndDay

                                    val trendPath = Path().apply {
                                        moveTo(dayToX(trendStartDay), weightToY(trendStartW))
                                        lineTo(dayToX(trendEndDay), weightToY(trendEndW))
                                    }
                                    drawPath(
                                        trendPath, WeightPurple.copy(alpha = 0.3f),
                                        style = Stroke(
                                            width = 2.dp.toPx(),
                                            cap = StrokeCap.Round,
                                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                                floatArrayOf(6.dp.toPx(), 8.dp.toPx())
                                            )
                                        )
                                    )
                                }
                            }

                            // Data points — colored by plan status
                            sortedDays.forEach { day ->
                                val w_kg = weightByDay[day]!!
                                val x = dayToX(day)
                                val y = weightToY(w_kg)
                                val entryDate = firstDate.plusDays(day.toLong())
                                val planned = plannedWeightAt(entryDate)
                                val pointColor = if (w_kg <= planned) CalGreen else FatRed
                                val radius = 4.5.dp.toPx()
                                // Glow
                                drawCircle(pointColor.copy(alpha = 0.2f), radius = radius * 2f, center = Offset(x, y))
                                // Fill
                                drawCircle(pointColor, radius = radius, center = Offset(x, y))
                                // Border
                                drawCircle(Color.White.copy(alpha = 0.8f), radius = radius, center = Offset(x, y), style = Stroke(width = 1.5.dp.toPx()))
                            }
                        }
                    }

                    // X-axis labels (scrolls with the chart)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(xAxisHeight)
                            .horizontalScroll(scrollState)
                    ) {
                        val xLabelStyle = TextStyle(fontSize = 9.sp, color = Color.Gray)
                        Canvas(
                            modifier = Modifier
                                .width(chartWidth)
                                .fillMaxHeight()
                        ) {
                            val w = size.width
                            val pxPerDay = w / totalDays.toFloat()
                            val dateFmt = DateTimeFormatter.ofPattern("d MMM")

                            // Label every Monday
                            var d = firstDate
                            while (!d.isAfter(chartEndDate)) {
                                if (d.dayOfWeek == DayOfWeek.MONDAY) {
                                    val day = ChronoUnit.DAYS.between(firstDate, d).toInt()
                                    val x = day * pxPerDay
                                    val label = textMeasurer.measure(d.format(dateFmt), xLabelStyle)
                                    drawText(
                                        label,
                                        topLeft = Offset(x - label.size.width / 2f, 4.dp.toPx())
                                    )
                                }
                                d = d.plusDays(1)
                            }
                        }
                    }
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

            // Plan target for this week
            val weekLabelParts = week.weekLabel.split(" - ")
            val weekStartStr = weekLabelParts.first().trim()
            val currentYear = LocalDate.now().year
            val weekStartDate = try {
                LocalDate.parse("$weekStartStr $currentYear", DateTimeFormatter.ofPattern("d MMM yyyy"))
            } catch (_: Exception) { null }
            val planTarget = if (weekStartDate != null) {
                (0..6).map { plannedWeightAt(weekStartDate.plusDays(it.toLong())) }.average()
            } else null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(week.avgWeight), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WeightPurple)
                    Text("avg kg", fontSize = 11.sp, color = Color.Gray)
                }
                if (planTarget != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.1f".format(planTarget), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFFFA726))
                        Text("plan avg", fontSize = 11.sp, color = Color.Gray)
                    }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${week.daysWeighed}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("weighed", fontSize = 11.sp, color = Color.Gray)
                }
            }

            // On track indicator for the week
            if (planTarget != null) {
                val diff = week.avgWeight - planTarget
                Spacer(Modifier.height(4.dp))
                Text(
                    if (diff <= 0) "%.1f kg ahead of plan".format(-diff)
                    else "%.1f kg behind plan".format(diff),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (diff <= 0) CalGreen else FatRed
                )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightInputSheet(
    initialWeight: Double,
    onDismiss: () -> Unit,
    onSave: (date: String, weight: Double) -> Unit
) {
    // Weight range: 40.0 to 200.0 in 0.1 steps = 1600 items
    val minWeight = 40.0
    val maxWeight = 200.0
    val stepSize = 0.1
    val totalSteps = ((maxWeight - minWeight) / stepSize).roundToInt()

    // Initial index from weight
    val initialIndex = ((initialWeight - minWeight) / stepSize).roundToInt().coerceIn(0, totalSteps)

    // Each tick is 12dp wide
    val density = LocalDensity.current
    val tickWidthPx = with(density) { 12.dp.toPx() }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialIndex - 15).coerceAtLeast(0)
    )

    // Derive current weight from scroll position
    val currentWeight by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val viewportCenter = layoutInfo.viewportStartOffset +
                    (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset) / 2
            val closestItem = layoutInfo.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - viewportCenter)
            }
            if (closestItem != null) {
                (minWeight + closestItem.index * stepSize)
            } else {
                initialWeight
            }
        }
    }

    // Date picker state
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM yyyy")

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE), currentWeight)
                },
                colors = ButtonDefaults.buttonColors(containerColor = WeightPurple)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Date selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", tint = Color.Gray)
                    }
                    TextButton(onClick = { selectedDate = LocalDate.now() }) {
                        Text(
                            if (selectedDate == LocalDate.now()) "Today"
                            else selectedDate.format(dateFormatter),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(
                        onClick = { selectedDate = selectedDate.plusDays(1) },
                        enabled = selectedDate < LocalDate.now()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day",
                            tint = if (selectedDate < LocalDate.now()) Color.Gray
                            else Color.Gray.copy(alpha = 0.2f)
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Big weight display
                Text(
                    "%.1f".format(currentWeight),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeightPurple
                )
                Text("kg", fontSize = 16.sp, color = Color.Gray)

                Spacer(Modifier.height(24.dp))

                // Scale ruler
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val textMeasurer = rememberTextMeasurer()
                    val tickColor = Color.Gray.copy(alpha = 0.3f)
                    val majorTickColor = WeightPurple.copy(alpha = 0.6f)
                    val labelStyle = TextStyle(fontSize = 10.sp, color = Color.Gray)

                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = with(density) {
                                // Center the first/last items
                                (LocalDensity.current.run { 140.dp })
                            }
                        ),
                        flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
                    ) {
                        items(totalSteps + 1) { index ->
                            val weight = minWeight + index * stepSize
                            val isWhole = (index % 10) == 0
                            val isHalf = (index % 5) == 0

                            Canvas(
                                modifier = Modifier
                                    .width(12.dp)
                                    .fillMaxHeight()
                            ) {
                                val centerX = size.width / 2
                                val bottom = size.height

                                when {
                                    isWhole -> {
                                        // Tall tick + label
                                        drawLine(
                                            majorTickColor,
                                            Offset(centerX, bottom),
                                            Offset(centerX, bottom - 40.dp.toPx()),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                        val label = "%.0f".format(weight)
                                        val textResult = textMeasurer.measure(label, labelStyle)
                                        drawText(
                                            textResult,
                                            topLeft = Offset(
                                                centerX - textResult.size.width / 2,
                                                bottom - 40.dp.toPx() - textResult.size.height - 4.dp.toPx()
                                            )
                                        )
                                    }
                                    isHalf -> {
                                        // Medium tick
                                        drawLine(
                                            tickColor,
                                            Offset(centerX, bottom),
                                            Offset(centerX, bottom - 28.dp.toPx()),
                                            strokeWidth = 1.5.dp.toPx()
                                        )
                                    }
                                    else -> {
                                        // Short tick
                                        drawLine(
                                            tickColor,
                                            Offset(centerX, bottom),
                                            Offset(centerX, bottom - 16.dp.toPx()),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Center indicator triangle
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .size(16.dp)
                            .offset(y = (-2).dp)
                    ) {
                        val path = Path().apply {
                            moveTo(size.width / 2, 0f)
                            lineTo(0f, size.height)
                            lineTo(size.width, size.height)
                            close()
                        }
                        drawPath(path, WeightPurple)
                    }

                    // Top center line
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxHeight(0.4f)
                            .width(2.dp)
                    ) {
                        drawLine(
                            WeightPurple,
                            Offset(size.width / 2, 0f),
                            Offset(size.width / 2, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Quick adjust buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(-1.0, -0.5, +0.5, +1.0).forEach { delta ->
                        FilledTonalButton(
                            onClick = {
                                val targetWeight = (currentWeight + delta).coerceIn(minWeight, maxWeight)
                                val targetIndex = ((targetWeight - minWeight) / stepSize).roundToInt()
                                coroutineScope.launch {
                                    listState.animateScrollToItem((targetIndex - 15).coerceAtLeast(0))
                                }
                            },
                            modifier = Modifier.width(64.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "%+.1f".format(delta),
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun WeeklyAveragesChart(weeks: List<WeekAnalysis>) {
    if (weeks.size < 2) return

    val textMeasurer = rememberTextMeasurer()

    // Calculate planned weekly averages
    val planWeeklyAvgs = weeks.map { week ->
        val weekLabelParts = week.weekLabel.split(" - ")
        val weekStartStr = weekLabelParts.first().trim()
        val currentYear = LocalDate.now().year
        val weekStart = try {
            LocalDate.parse("$weekStartStr $currentYear", DateTimeFormatter.ofPattern("d MMM yyyy"))
        } catch (_: Exception) { null }
        if (weekStart != null) {
            (0..6).map { plannedWeightAt(weekStart.plusDays(it.toLong())) }.average()
        } else null
    }

    // Y range
    val avgs = weeks.map { it.avgWeight }
    val planVals = planWeeklyAvgs.filterNotNull()
    val allVals = avgs + planVals
    val yMin = allVals.min() - 0.5
    val yMax = allVals.max() + 0.5
    val yRange = (yMax - yMin).coerceAtLeast(1.0)

    // Nice grid step
    val gridStep = if (yRange > 8) 2.0 else 1.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Weekly Averages", fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))

            val yLabelStyle = TextStyle(fontSize = 9.sp, color = Color.Gray)
            val xLabelStyle = TextStyle(fontSize = 8.sp, color = Color.Gray)
            val changeLabelStyle = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold)
            val gridColor = Color.Gray.copy(alpha = 0.12f)
            val planColor = Color(0xFFFFA726)

            // Chart with built-in axes
            val leftPad = 36.dp
            val bottomPad = 20.dp

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                val w = size.width
                val h = size.height
                val lp = leftPad.toPx()
                val bp = bottomPad.toPx()
                val chartW = w - lp
                val chartH = h - bp
                val pt = 12.dp.toPx()
                val drawH = chartH - pt

                fun weightToY(kg: Double): Float {
                    val norm = ((kg - yMin) / yRange).toFloat()
                    return pt + drawH * (1f - norm)
                }
                fun weekToX(i: Int): Float {
                    return lp + (i.toFloat() / (weeks.size - 1).coerceAtLeast(1)) * chartW * 0.9f + chartW * 0.05f
                }

                // Y-axis grid lines + labels
                var kg = (Math.ceil(yMin / gridStep) * gridStep)
                while (kg <= yMax) {
                    val y = weightToY(kg)
                    drawLine(gridColor, Offset(lp, y), Offset(w, y))
                    val label = textMeasurer.measure("%.0f".format(kg), yLabelStyle)
                    drawText(label, topLeft = Offset(lp - label.size.width - 4.dp.toPx(), y - label.size.height / 2f))
                    kg += gridStep
                }

                // X-axis labels
                weeks.forEachIndexed { i, week ->
                    val x = weekToX(i)
                    // Show short date like "11 Mar"
                    val label = week.weekLabel.take(5).trim()
                    val measured = textMeasurer.measure(label, xLabelStyle)
                    // Only show every other label if crowded
                    val showLabel = weeks.size <= 6 || i % 2 == 0 || i == weeks.size - 1
                    if (showLabel) {
                        drawText(measured, topLeft = Offset(x - measured.size.width / 2f, chartH + 4.dp.toPx()))
                    }
                    // Small tick
                    drawLine(gridColor, Offset(x, chartH), Offset(x, chartH + 3.dp.toPx()))
                }

                // Plan line (dashed orange)
                val planPath = Path()
                var started = false
                planWeeklyAvgs.forEachIndexed { i, avg ->
                    if (avg != null) {
                        val x = weekToX(i)
                        val y = weightToY(avg)
                        if (!started) { planPath.moveTo(x, y); started = true }
                        else planPath.lineTo(x, y)
                    }
                }
                if (started) {
                    drawPath(planPath, planColor.copy(alpha = 0.5f),
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
                }
                // Plan dots
                planWeeklyAvgs.forEachIndexed { i, avg ->
                    if (avg != null) {
                        val x = weekToX(i)
                        val y = weightToY(avg)
                        drawCircle(planColor.copy(alpha = 0.4f), radius = 3.dp.toPx(), center = Offset(x, y))
                    }
                }

                // Actual line (solid purple)
                val actualPath = Path()
                weeks.forEachIndexed { i, week ->
                    val x = weekToX(i)
                    val y = weightToY(week.avgWeight)
                    if (i == 0) actualPath.moveTo(x, y) else actualPath.lineTo(x, y)
                }
                drawPath(actualPath, WeightPurple, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

                // Actual dots — colored green/red vs plan
                weeks.forEachIndexed { i, week ->
                    val x = weekToX(i)
                    val y = weightToY(week.avgWeight)
                    val planAvg = planWeeklyAvgs[i]
                    val dotColor = if (planAvg != null && week.avgWeight <= planAvg) CalGreen else FatRed
                    drawCircle(dotColor.copy(alpha = 0.15f), radius = 8.dp.toPx(), center = Offset(x, y))
                    drawCircle(dotColor, radius = 4.dp.toPx(), center = Offset(x, y))
                    drawCircle(Color.White.copy(alpha = 0.7f), radius = 4.dp.toPx(), center = Offset(x, y), style = Stroke(width = 1.dp.toPx()))

                    // Weight change label above/below dot
                    if (i > 0) {
                        val change = week.avgWeight - weeks[i - 1].avgWeight
                        val changeStr = "%+.1f".format(change)
                        val color = if (change <= 0) CalGreen else FatRed
                        val measured = textMeasurer.measure(changeStr, changeLabelStyle.copy(color = color))
                        val labelY = if (change <= 0) y - measured.size.height - 4.dp.toPx() else y + 6.dp.toPx()
                        drawText(measured, topLeft = Offset(x - measured.size.width / 2f, labelY))
                    }
                }
            }

            // Bottom summary
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Canvas(Modifier.size(8.dp)) { drawCircle(WeightPurple, radius = size.minDimension / 2) }
                    Text(" Actual  ", fontSize = 10.sp, color = Color.Gray)
                    Canvas(Modifier.size(8.dp)) { drawCircle(Color(0xFFFFA726), radius = size.minDimension / 2) }
                    Text(" Plan", fontSize = 10.sp, color = Color.Gray)
                }

                // vs plan
                val lastPlan = planWeeklyAvgs.lastOrNull()
                if (lastPlan != null) {
                    val diff = weeks.last().avgWeight - lastPlan
                    Text(
                        "%+.1f kg vs plan".format(diff),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (diff <= 0) CalGreen else FatRed
                    )
                }
            }
        }
    }
}
