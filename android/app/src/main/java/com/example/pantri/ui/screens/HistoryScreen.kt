package com.example.pantri.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.pantri.api.DayDetail
import com.example.pantri.api.DaySummary
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
                val data = ApiClient.api.getDays()
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
                val data = ApiClient.api.getDay(dateStr)
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

@Composable
fun HistoryScreen(vm: HistoryViewModel = viewModel()) {
    val currentDate by vm.currentDate.collectAsState()
    val day by vm.currentDay.collectAsState()
    val loading by vm.loading.collectAsState()
    val isToday = currentDate == LocalDate.now()

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
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        val dt = day?.day_totals ?: Totals()
        val entries = day?.entries ?: emptyList()

        // Day summary card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${dt.kcal.toInt()}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("kcal", fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.0f".format(dt.protein_g), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = ProteinBlue)
                        Text("protein", fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("EUR %.2f".format(dt.cost_eur), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = CostCyan)
                        Text("spent", fontSize = 12.sp)
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
    }
}
