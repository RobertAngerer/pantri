package com.example.pantri.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.pantri.ui.theme.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantri.api.ApiClient
import com.example.pantri.api.Cache
import com.example.pantri.api.DayDetail
import com.example.pantri.api.DaySummary
import com.example.pantri.api.FoodDiaryResult
import com.example.pantri.api.Totals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class HistoryViewModel : ViewModel() {
    private val _days = MutableStateFlow<List<DaySummary>>(emptyList())
    val days = _days.asStateFlow()

    private val _currentDate = MutableStateFlow(LocalDate.now())
    val currentDate = _currentDate.asStateFlow()

    private val _currentDay = MutableStateFlow<DayDetail?>(null)
    val currentDay = _currentDay.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()

    init {
        loadDays()
        loadDay(LocalDate.now())
    }

    fun loadDays() {
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

    fun loadDay(date: LocalDate) {
        _currentDate.value = date
        _loading.value = true
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        viewModelScope.launch {
            try {
                val data = ApiClient.getDay(dateStr)
                Cache.saveDay(dateStr, data)
                _currentDay.value = data
            } catch (_: Exception) {
                _currentDay.value = Cache.loadDay(dateStr)
            }
            _loading.value = false
        }
    }

    fun goBack() = loadDay(_currentDate.value.minusDays(1))
    fun goForward() {
        val next = _currentDate.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) loadDay(next)
    }

    val canGoForward: Boolean get() = _currentDate.value.isBefore(LocalDate.now())
}

// Dracula-themed syntax highlighting matching neovim pantri.vim
private val highlightRules = listOf(
    Regex("\\[.+?]") to SpanStyle(color = Color.White, fontWeight = FontWeight.Bold),           // headers
    Regex("\\|") to SpanStyle(color = Color(0xFF666666)),                                        // separator
    Regex("\\d+\\.?\\d*P") to SpanStyle(color = CalGreen, fontWeight = FontWeight.Bold),         // protein
    Regex("\\d+\\.?\\d*C") to SpanStyle(color = CarbsAmber),                                    // carbs
    Regex("\\d+\\.?\\d*F") to SpanStyle(color = FatRed),                                        // fat
    Regex("\\d+ kcal") to SpanStyle(color = Color(0xFFF8F8F2), fontWeight = FontWeight.Bold),    // calories
    Regex("EUR \\d+\\.\\d+") to SpanStyle(color = CostCyan),                                    // cost
    Regex("\\d+(?:\\.\\d+)?(?:g|ml|kg|l)\\b") to SpanStyle(color = WeightPurple),               // quantity
    Regex("@\\s*\\d+:\\d+") to SpanStyle(color = Color(0xFF6272A4)),                             // timestamp
)

private fun highlightDayFile(text: String) = buildAnnotatedString {
    text.lines().forEachIndexed { lineIdx, line ->
        if (lineIdx > 0) append("\n")

        // Find all matches and their styles
        data class Match(val start: Int, val end: Int, val style: SpanStyle)
        val matches = mutableListOf<Match>()
        for ((regex, style) in highlightRules) {
            for (m in regex.findAll(line)) {
                matches.add(Match(m.range.first, m.range.last + 1, style))
            }
        }
        // Sort by start, resolve overlaps (first match wins)
        matches.sortBy { it.start }
        val used = BooleanArray(line.length)

        for (m in matches) {
            var overlaps = false
            for (i in m.start until m.end) {
                if (i < used.size && used[i]) { overlaps = true; break }
            }
            if (overlaps) continue
            for (i in m.start until m.end) {
                if (i < used.size) used[i] = true
            }
        }

        // Build the line
        val activeMatches = matches.filter { m ->
            (m.start until m.end).none { i -> i < used.size && !used[i] && matches.indexOf(m) > 0 }
        }.sortedBy { it.start }

        // Actually rebuild: iterate char by char
        var pos = 0
        val sortedNonOverlapping = matches.filter { m ->
            var ok = true
            for (i in m.start until m.end) {
                if (i >= line.length) { ok = false; break }
            }
            ok
        }.sortedBy { it.start }.distinctBy { it.start }

        // Remove overlapping (keep first)
        val finalMatches = mutableListOf<Match>()
        var lastEnd = 0
        for (m in sortedNonOverlapping) {
            if (m.start >= lastEnd) {
                finalMatches.add(m)
                lastEnd = m.end
            }
        }

        for (m in finalMatches) {
            if (pos < m.start) {
                append(line.substring(pos, m.start))
            }
            withStyle(m.style) {
                append(line.substring(m.start, m.end.coerceAtMost(line.length)))
            }
            pos = m.end
        }
        if (pos < line.length) {
            append(line.substring(pos))
        }
    }
}

private fun stripFormatting(rawText: String): String {
    return rawText.lines().mapNotNull { line ->
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return@mapNotNull null
        // date line
        if (trimmed.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return@mapNotNull null
        // header like "[entry 1]  @ 14:30" -> "[entry 1]"
        val headerMatch = Regex("(\\[.+?])\\s*@.*").find(trimmed)
        if (headerMatch != null) return@mapNotNull headerMatch.groupValues[1]
        // formatted item like "chicken 200g  |  312 kcal ..." -> "chicken 200g"
        if ("|" in trimmed) return@mapNotNull trimmed.split("|")[0].trim()
        trimmed
    }.joinToString("\n")
}

private fun formatDayFile(date: String, result: FoodDiaryResult): String {
    val lines = mutableListOf(date)
    for (entry in result.entries) {
        val ts = entry.timestamp
        lines.add(if (ts.isNotEmpty()) "[${entry.label}]  @ $ts" else "[${entry.label}]")
        for (item in entry.items) {
            lines.add(
                "  ${item.name} ${item.quantity}" +
                "  |  ${item.kcal.toInt()} kcal  ${item.protein_g}P  ${item.carbs_g}C  ${item.fat_g}F  EUR ${"%.2f".format(item.cost_eur)}"
            )
        }
        lines.add("")
    }
    return lines.joinToString("\n")
}

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentDate by vm.currentDate.collectAsState()
    val day by vm.currentDay.collectAsState()
    val loading by vm.loading.collectAsState()
    val isToday = currentDate == LocalDate.now()

    var editing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var processing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<FoodDiaryResult?>(null) }
    var saving by remember { mutableStateOf(false) }

    // Reset edit state when navigating days
    LaunchedEffect(currentDate) {
        editing = false
        preview = null
        processing = false
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        // Date navigator
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day", modifier = Modifier.size(32.dp))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (isToday) "Today" else currentDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        currentDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
                IconButton(
                    onClick = { vm.goForward() },
                    enabled = vm.canGoForward
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day",
                        modifier = Modifier.size(32.dp),
                        tint = if (vm.canGoForward) LocalContentColor.current else Color.Gray.copy(alpha = 0.3f)
                    )
                }
            }
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    SpinningAppIcon()
                }
            }
            return@LazyColumn
        }

        // Edit mode
        if (editing) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Surface2),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Edit food diary", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "Type your food like a day file — groups with [breakfast], items like 'chicken 200g'",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                        )
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp),
                            textStyle = LocalTextStyle.current.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            placeholder = { Text("[breakfast]\ncereal 100g\nmilk 200ml", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 14.sp, color = Color.Gray.copy(alpha = 0.4f)) }
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (editText.isBlank()) return@Button
                                    processing = true
                                    preview = null
                                    scope.launch {
                                        try {
                                            preview = ApiClient.parseFoodText(editText)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            processing = false
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !processing && editText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = CalGreen)
                            ) {
                                Text(if (processing) "Processing..." else "Process")
                            }
                            OutlinedButton(
                                onClick = {
                                    editing = false
                                    preview = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            }

            // Processing indicator
            if (processing) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SpinningAppIcon()
                            Spacer(Modifier.height(8.dp))
                            Text("Sending to OpenAI...", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Preview results
            if (preview != null) {
                val result = preview!!

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Surface2),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Day totals summary
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${result.day_totals.kcal.toInt()}", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = CalGreen)
                                Text("€%.2f".format(result.day_totals.cost_eur), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CostCyan)
                            }
                            Text("kcal", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%.0f".format(result.day_totals.protein_g), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ProteinBlue)
                                    Text("protein", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%.0f".format(result.day_totals.carbs_g), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CarbsAmber)
                                    Text("carbs", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("%.0f".format(result.day_totals.fat_g), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = FatRed)
                                    Text("fat", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                                }
                            }
                        }
                    }
                }

                items(result.entries) { entry ->
                    EntryCard(entry)
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                saving = true
                                val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
                                val rawText = formatDayFile(dateStr, result)
                                scope.launch {
                                    try {
                                        ApiClient.saveDay(dateStr, result, rawText)
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        editing = false
                                        preview = null
                                        vm.loadDay(currentDate)
                                        vm.loadDays()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    } finally {
                                        saving = false
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = !saving,
                            colors = ButtonDefaults.buttonColors(containerColor = CalGreen)
                        ) {
                            Text(if (saving) "Saving..." else "Save")
                        }
                        OutlinedButton(
                            onClick = { preview = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Discard")
                        }
                    }
                }
            }

            return@LazyColumn
        }

        // -- Normal view mode --

        val dt = day?.day_totals ?: Totals()
        val entries = day?.entries ?: emptyList()

        // Day summary card
        item {
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
                        Text("${dt.kcal.toInt()}", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = CalGreen)
                        Text("€%.2f".format(dt.cost_eur), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CostCyan)
                    }
                    Text("kcal", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%.0f".format(dt.protein_g), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = ProteinBlue)
                            Text("protein", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%.0f".format(dt.carbs_g), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CarbsAmber)
                            Text("carbs", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("%.0f".format(dt.fat_g), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = FatRed)
                            Text("fat", fontSize = 11.sp, color = Color.White.copy(alpha = 0.45f))
                        }
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("No entries for this day", color = Color.Gray)
                }
            }
        }

        // Meal entries
        items(entries) { entry ->
            EntryCard(entry)
        }

        // Raw text file
        val rawText = day?.raw_text ?: ""
        if (rawText.isNotBlank()) {
            item {
                Text(
                    "Day file",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Text(
                        highlightDayFile(rawText.trimEnd()),
                        modifier = Modifier.padding(14.dp),
                        fontSize = 12.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Edit button at the bottom
        item {
            Button(
                onClick = {
                    val rt = day?.raw_text ?: ""
                    editText = if (rt.isNotBlank()) stripFormatting(rt) else ""
                    editing = true
                    preview = null
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ProteinBlue)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (entries.isEmpty()) "Add Food" else "Edit")
            }
        }
    }
}
