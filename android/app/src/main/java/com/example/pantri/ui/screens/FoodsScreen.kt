package com.example.pantri.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantri.api.*
import com.example.pantri.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class FoodSummary(
    val name: String,
    val count: Int,
    val totalKcal: Double,
    val totalProtein: Double,
    val totalCost: Double,
    val totalGrams: Double?,
    val perServing: Triple<Double, Double, Double> // kcal, protein, cost
)

private val gramsRegex = Regex("""(\d+(?:[.,]\d+)?)\s*g\b""", RegexOption.IGNORE_CASE)

private fun parseGrams(quantity: String): Double? {
    val match = gramsRegex.find(quantity) ?: return null
    return match.groupValues[1].replace(',', '.').toDoubleOrNull()
}

enum class FoodPeriod(val label: String) {
    WEEK("This Week"),
    MONTH("This Month"),
    ALL("All Time")
}

class FoodsViewModel : ViewModel() {
    private val _allDays = MutableStateFlow<List<DayDetail>>(emptyList())
    val allDays = _allDays.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _allDays.value = ApiClient.getDaysDetailed()
            } catch (_: Exception) {}
            _loading.value = false
        }
    }
}

fun aggregateFoods(days: List<DayDetail>, period: FoodPeriod): List<FoodSummary> {
    val today = LocalDate.now()
    val since = when (period) {
        FoodPeriod.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong())
        FoodPeriod.MONTH -> today.withDayOfMonth(1)
        FoodPeriod.ALL -> LocalDate.MIN
    }

    val filtered = days.filter {
        try {
            val d = LocalDate.parse(it.date)
            d >= since && d <= today
        } catch (_: Exception) { false }
    }

    data class Agg(
        var count: Int = 0,
        var kcal: Double = 0.0,
        var protein: Double = 0.0,
        var cost: Double = 0.0,
        var grams: Double = 0.0,
        var hasGrams: Boolean = false,
        var displayName: String = ""
    )

    val map = mutableMapOf<String, Agg>()
    for (day in filtered) {
        for (entry in day.entries) {
            for (item in entry.items) {
                val key = item.name.trim().lowercase()
                if (key.isBlank()) continue
                val a = map.getOrPut(key) { Agg(displayName = item.name.trim()) }
                a.count++
                a.kcal += item.kcal
                a.protein += item.protein_g
                a.cost += item.cost_eur
                val g = parseGrams(item.quantity)
                if (g != null) {
                    a.grams += g
                    a.hasGrams = true
                }
                if (item.name.trim().length > a.displayName.length) {
                    a.displayName = item.name.trim()
                }
            }
        }
    }

    return map.values.map { a ->
        FoodSummary(
            name = a.displayName,
            count = a.count,
            totalKcal = a.kcal,
            totalProtein = a.protein,
            totalCost = a.cost,
            totalGrams = if (a.hasGrams) a.grams else null,
            perServing = Triple(
                if (a.count > 0) a.kcal / a.count else 0.0,
                if (a.count > 0) a.protein / a.count else 0.0,
                if (a.count > 0) a.cost / a.count else 0.0
            )
        )
    }.sortedByDescending { it.totalKcal }
}

@Composable
fun FoodsScreen(vm: FoodsViewModel = viewModel()) {
    val allDays by vm.allDays.collectAsState()
    val loading by vm.loading.collectAsState()

    var period by remember { mutableStateOf(FoodPeriod.WEEK) }
    var sortBy by remember { mutableStateOf("kcal") } // kcal, frequency, cost
    var showScan by remember { mutableStateOf(false) }

    if (showScan) {
        AlertDialog(
            onDismissRequest = { showScan = false },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showScan = false }) { Text("Close") }
            },
            title = null,
            text = { ScanScreen() }
        )
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SpinningAppIcon()
        }
        return
    }

    if (allDays.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No food data yet", style = MaterialTheme.typography.titleMedium)
                Text("Track some food first", fontSize = 14.sp, color = Color.Gray)
            }
        }
        return
    }

    val rawFoods = remember(allDays, period) { aggregateFoods(allDays, period) }
    val foods = remember(rawFoods, sortBy) {
        when (sortBy) {
            "frequency" -> rawFoods.sortedByDescending { it.count }
            "cost" -> rawFoods.sortedByDescending { it.totalCost }
            else -> rawFoods.sortedByDescending { it.totalKcal }
        }
    }
    val totalKcal = remember(foods) { foods.sumOf { it.totalKcal } }
    val totalProtein = remember(foods) { foods.sumOf { it.totalProtein } }
    val totalCost = remember(foods) { foods.sumOf { it.totalCost } }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showScan = true },
                containerColor = CostCyan,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Search, contentDescription = "Scan barcode")
            }
        },
        containerColor = Color.Transparent
    ) { scaffoldPadding ->
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(scaffoldPadding)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item {
            Text("Foods", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // Period toggle
        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                FoodPeriod.entries.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = period == p,
                        onClick = { period = p },
                        shape = SegmentedButtonDefaults.itemShape(index, FoodPeriod.entries.size)
                    ) {
                        Text(p.label, fontSize = 13.sp)
                    }
                }
            }
        }

        // Sort chips
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("kcal" to "Calories", "frequency" to "Frequency", "cost" to "Cost").forEach { (key, label) ->
                    FilterChip(
                        selected = sortBy == key,
                        onClick = { sortBy = key },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CalGreen.copy(alpha = 0.2f),
                            selectedLabelColor = CalGreen
                        )
                    )
                }
            }
        }

        // Summary bar
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Surface2)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${foods.size}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("foods", fontSize = 11.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${totalKcal.toInt()}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CalGreen)
                        Text("kcal", fontSize = 11.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.0f g".format(totalProtein), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ProteinBlue)
                        Text("protein", fontSize = 11.sp, color = Color.Gray)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.2f".format(totalCost), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CostCyan)
                        Text("EUR", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
        }

        if (foods.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No data for this period", color = Color.Gray)
                }
            }
        }

        // Food list
        itemsIndexed(foods) { index, food ->
            FoodRow(index + 1, food, totalKcal)
        }
    }
    }
}

@Composable
fun FoodRow(rank: Int, food: FoodSummary, periodTotalKcal: Double) {
    val kcalPct = if (periodTotalKcal > 0) (food.totalKcal / periodTotalKcal * 100) else 0.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            // Top row: rank + name + count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$rank",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    food.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (food.totalGrams != null) {
                    Text(
                        "%.0fg".format(food.totalGrams),
                        fontSize = 12.sp,
                        color = CarbsAmber.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Text(
                    "${food.count}x",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }

            Spacer(Modifier.height(6.dp))

            // Kcal bar
            LinearProgressIndicator(
                progress = { kcalPct.toFloat().coerceIn(0f, 100f) / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = CalGreen.copy(alpha = 0.6f),
                trackColor = Color.White.copy(alpha = 0.04f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )

            Spacer(Modifier.height(6.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Kcal
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${food.totalKcal.toInt()}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CalGreen
                    )
                    Text(
                        " kcal",
                        fontSize = 10.sp,
                        color = CalGreen.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                    Text(
                        "  %.0f%%".format(kcalPct),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Protein
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.0f".format(food.totalProtein),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ProteinBlue
                    )
                    Text(
                        "g prot",
                        fontSize = 10.sp,
                        color = ProteinBlue.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }

                // Cost
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "%.2f".format(food.totalCost),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CostCyan
                    )
                    Text(
                        "\u20AC",
                        fontSize = 10.sp,
                        color = CostCyan.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 1.dp)
                    )
                }
            }
        }
    }
}
