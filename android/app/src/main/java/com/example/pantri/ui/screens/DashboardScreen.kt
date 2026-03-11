package com.example.pantri.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantri.api.ApiClient
import com.example.pantri.api.Cache
import com.example.pantri.api.Entry
import com.example.pantri.api.TodayResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow<TodayResponse?>(null)
    val state = _state.asStateFlow()

    private val _offline = MutableStateFlow(false)
    val offline = _offline.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                _offline.value = false
                val data = ApiClient.api.getToday()
                Cache.saveToday(data)
                _state.value = data
            } catch (_: Exception) {
                val cached = Cache.loadToday()
                if (cached != null) {
                    _state.value = cached
                    _offline.value = true
                } else {
                    _state.value = null
                    _offline.value = true
                }
            }
        }
    }
}

val CalGreen = Color(0xFF4CAF50)
val ProteinBlue = Color(0xFF2196F3)
val CarbsAmber = Color(0xFFFFC107)
val FatRed = Color(0xFFF44336)
val CostCyan = Color(0xFF00BCD4)

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val data by vm.state.collectAsState()
    val offline by vm.offline.collectAsState()

    val today = data ?: return Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (offline) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No cached data", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Connect to the API to load data", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { vm.load() }) { Text("Retry") }
            }
        } else {
            CircularProgressIndicator()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        // Offline banner
        if (offline) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CarbsAmber.copy(alpha = 0.15f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Offline — showing cached data", fontSize = 13.sp, color = CarbsAmber)
                        TextButton(onClick = { vm.load() }) { Text("Retry", fontSize = 13.sp) }
                    }
                }
            }
        }

        // Big calorie ring
        item {
            CalorieRing(
                eaten = today.day_totals.kcal.toInt(),
                goal = today.goals.kcal.toInt()
            )
        }

        // Macro bars
        item {
            MacroRow(today)
        }

        // Spent today
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spent today", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "EUR %.2f".format(today.day_totals.cost_eur),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CostCyan
                    )
                }
            }
        }

        // Entries
        if (today.entries.isNotEmpty()) {
            item {
                Text(
                    "Meals",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(today.entries) { entry ->
                EntryCard(entry)
            }
        }
    }
}

@Composable
fun CalorieRing(eaten: Int, goal: Int) {
    val progress = if (goal > 0) (eaten.toFloat() / goal).coerceIn(0f, 1.5f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000),
        label = "cal_progress"
    )
    val remaining = goal - eaten
    val over = remaining < 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            // Background ring
            drawArc(
                color = Color.Gray.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = stroke
            )
            // Progress ring
            drawArc(
                color = if (over) FatRed else CalGreen,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (over) "+${-remaining}" else "$remaining",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = if (over) FatRed else CalGreen
            )
            Text(
                if (over) "kcal over" else "kcal left",
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "$eaten / $goal",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun MacroRow(today: TodayResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MacroCard(
            label = "Protein",
            value = today.day_totals.protein_g,
            goal = today.goals.protein_g,
            unit = "g",
            color = ProteinBlue,
            modifier = Modifier.weight(1f)
        )
        MacroCard(
            label = "Carbs",
            value = today.day_totals.carbs_g,
            goal = null,
            unit = "g",
            color = CarbsAmber,
            modifier = Modifier.weight(1f)
        )
        MacroCard(
            label = "Fat",
            value = today.day_totals.fat_g,
            goal = null,
            unit = "g",
            color = FatRed,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MacroCard(
    label: String,
    value: Double,
    goal: Double?,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val progress = if (goal != null && goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else null
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f,
        animationSpec = tween(1000),
        label = "${label}_progress"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = Color.Gray)
            Spacer(Modifier.height(4.dp))
            Text(
                "%.0f%s".format(value, unit),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (goal != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = color,
                    trackColor = Color.Gray.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "%.0f left".format(goal - value),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun EntryCard(entry: Entry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    entry.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (entry.timestamp.isNotEmpty()) {
                    Text(entry.timestamp, fontSize = 12.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(8.dp))
            entry.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${item.name} ${item.quantity}", fontSize = 13.sp)
                    Text(
                        "${item.kcal.toInt()} kcal  %.0fP".format(item.protein_g),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${entry.totals.kcal.toInt()} kcal",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "%.1fP  %.1fC  %.1fF  EUR %.2f".format(
                        entry.totals.protein_g,
                        entry.totals.carbs_g,
                        entry.totals.fat_g,
                        entry.totals.cost_eur
                    ),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
