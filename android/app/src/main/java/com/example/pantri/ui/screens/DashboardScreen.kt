package com.example.pantri.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
import com.example.pantri.api.MealPrepItem
import com.example.pantri.api.TodayResponse
import com.example.pantri.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    private val _state = MutableStateFlow<TodayResponse?>(null)
    val state = _state.asStateFlow()

    private val _offline = MutableStateFlow(false)
    val offline = _offline.asStateFlow()

    private val _mealPreps = MutableStateFlow<List<MealPrepItem>>(emptyList())
    val mealPreps = _mealPreps.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { load() }

    private suspend fun doLoad() {
        // Load settings first so goals are correct
        try {
            val s = ApiClient.loadSettings()
            Cache.saveSettings(s)
        } catch (_: Exception) {
            // Use cached settings
            ApiClient.applySettings(Cache.loadSettings())
        }

        try {
            _offline.value = false
            val data = ApiClient.getToday()
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
        try {
            val preps = ApiClient.getMealPreps()
            Cache.saveMealPreps(preps)
            _mealPreps.value = preps
        } catch (_: Exception) {
            _mealPreps.value = Cache.loadMealPreps()
        }
    }

    fun load() {
        viewModelScope.launch { doLoad() }
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            doLoad()
            _refreshing.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val data by vm.state.collectAsState()
    val offline by vm.offline.collectAsState()
    val mealPreps by vm.mealPreps.collectAsState()
    val refreshing by vm.refreshing.collectAsState()

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
            SpinningAppIcon()
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        if (offline) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = CarbsAmber.copy(alpha = 0.12f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Offline — cached data", fontSize = 13.sp, color = CarbsAmber)
                        TextButton(onClick = { vm.load() }) { Text("Retry", fontSize = 13.sp) }
                    }
                }
            }
        }

        item { CalorieRing(eaten = today.day_totals.kcal.toInt(), goal = today.goals.kcal.toInt()) }
        item { MacroRow(today) }

        // Spent today
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spent today", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "€%.2f".format(today.day_totals.cost_eur),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = CostCyan
                    )
                }
            }
        }

        if (today.entries.isNotEmpty()) {
            item {
                Text(
                    "Meals",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(today.entries) { entry -> EntryCard(entry) }
        }

        if (mealPreps.isNotEmpty()) {
            item {
                Text(
                    "Fridge",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(mealPreps) { prep -> MealPrepCard(prep) }
        }
    }
    }
}

@Composable
fun CalorieRing(eaten: Int, goal: Int) {
    val progress = if (goal > 0) (eaten.toFloat() / goal).coerceIn(0f, 1.5f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress, animationSpec = tween(1000), label = "cal_progress"
    )
    val remaining = goal - eaten
    val over = remaining < 0

    Box(
        modifier = Modifier.fillMaxWidth().height(210.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(175.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(
                color = Color.White.copy(alpha = 0.06f),
                startAngle = -90f, sweepAngle = 360f,
                useCenter = false, style = stroke
            )
            drawArc(
                color = if (over) FatRed else CalGreen,
                startAngle = -90f, sweepAngle = animatedProgress * 360f,
                useCenter = false, style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (over) "+${-remaining}" else "$remaining",
                fontSize = 38.sp, fontWeight = FontWeight.Bold,
                color = if (over) FatRed else CalGreen
            )
            Text(
                if (over) "kcal over" else "kcal left",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "$eaten / $goal",
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
fun MacroRow(today: TodayResponse) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MacroCard("Protein", today.day_totals.protein_g, today.goals.protein_g, "g", ProteinBlue, Modifier.weight(1f))
        MacroCard("Carbs", today.day_totals.carbs_g, today.goals.carbs_g, "g", CarbsAmber, Modifier.weight(1f))
        MacroCard("Fat", today.day_totals.fat_g, today.goals.fat_g, "g", FatRed, Modifier.weight(1f))
    }
}

@Composable
fun MacroCard(label: String, value: Double, goal: Double?, unit: String, color: Color, modifier: Modifier = Modifier) {
    val progress = if (goal != null && goal > 0) (value / goal).toFloat().coerceIn(0f, 1f) else null
    val animatedProgress by animateFloatAsState(
        targetValue = progress ?: 0f, animationSpec = tween(1000), label = "${label}_progress"
    )

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
            Spacer(Modifier.height(4.dp))
            Text("%.0f%s".format(value, unit), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            if (goal != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = color,
                    trackColor = Color.White.copy(alpha = 0.06f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(Modifier.height(4.dp))
                Text("%.0f left".format(goal - value), fontSize = 10.sp, color = Color.White.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
fun EntryCard(entry: Entry) {
    var expanded by remember { mutableStateOf(false) }
    val sorted = remember(entry.items) { entry.items.sortedByDescending { it.kcal } }
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f, animationSpec = tween(250), label = "arrow"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = MaterialTheme.shapes.large
    ) {
        Column {
            // Header — always visible, tappable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${entry.totals.kcal.toInt()} kcal", fontSize = 12.sp, color = CalGreen)
                        Text("%.0fP".format(entry.totals.protein_g), fontSize = 12.sp, color = ProteinBlue)
                        Text("€%.2f".format(entry.totals.cost_eur), fontSize = 12.sp, color = CostCyan)
                    }
                }
                if (entry.timestamp.isNotEmpty()) {
                    Text(entry.timestamp, fontSize = 12.sp, color = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.padding(end = 4.dp))
                }
                Icon(
                    Icons.Default.KeyboardArrowDown, contentDescription = "expand",
                    modifier = Modifier.size(20.dp).rotate(arrowRotation),
                    tint = Color.White.copy(alpha = 0.4f)
                )
            }

            // Expandable detail
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Spacer(Modifier.height(8.dp))

                    // Column headers
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                        Text("Item", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.weight(1f))
                        Text("kcal", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                        Text("P", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                        Text("C", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                        Text("F", fontSize = 10.sp, color = Color.White.copy(alpha = 0.3f), modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                    }

                    sorted.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, fontSize = 13.sp)
                                Text(item.quantity, fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
                            }
                            Text("${item.kcal.toInt()}", fontSize = 13.sp, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                            Text("%.0f".format(item.protein_g), fontSize = 13.sp, color = ProteinBlue, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                            Text("%.0f".format(item.carbs_g), fontSize = 13.sp, color = CarbsAmber, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                            Text("%.0f".format(item.fat_g), fontSize = 13.sp, color = FatRed, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("€%.2f".format(entry.totals.cost_eur), fontSize = 12.sp, color = CostCyan, modifier = Modifier.weight(1f))
                        Text("${entry.totals.kcal.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                        Text("%.0f".format(entry.totals.protein_g), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ProteinBlue, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                        Text("%.0f".format(entry.totals.carbs_g), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CarbsAmber, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                        Text("%.0f".format(entry.totals.fat_g), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FatRed, modifier = Modifier.width(32.dp), textAlign = TextAlign.End)
                    }
                }
            }
        }
    }
}

@Composable
fun MealPrepCard(prep: MealPrepItem) {
    val pct = if (prep.initial_g > 0) (prep.remaining_g / prep.initial_g).toFloat().coerceIn(0f, 1f) else 0f
    val color = when {
        pct > 0.3f -> CalGreen
        pct > 0.1f -> CarbsAmber
        else -> FatRed
    }
    val animatedProgress by animateFloatAsState(
        targetValue = pct, animationSpec = tween(800), label = "prep_${prep.id}"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Surface2),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    prep.food,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    prep.created,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.3f)
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color,
                trackColor = Color.White.copy(alpha = 0.06f),
                strokeCap = StrokeCap.Round,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "%.0fg remaining".format(prep.remaining_g),
                    fontSize = 12.sp,
                    color = color
                )
                Text(
                    "%.0fg / %.0fg".format(prep.remaining_g, prep.initial_g),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}
