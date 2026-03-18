package com.example.pantri.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pantri.api.ApiClient
import com.example.pantri.api.AppSettings
import com.example.pantri.api.Cache
import com.example.pantri.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val _settings = MutableStateFlow(ApiClient.getSettings())
    val settings = _settings.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved = _saved.asStateFlow()

    fun save(s: AppSettings) {
        viewModelScope.launch {
            _saving.value = true
            try {
                ApiClient.saveSettings(s)
                Cache.saveSettings(s)
                _settings.value = s
                _saved.value = true
            } catch (_: Exception) {
                // Still apply locally
                ApiClient.applySettings(s)
                Cache.saveSettings(s)
                _settings.value = s
            }
            _saving.value = false
        }
    }

    fun clearSaved() { _saved.value = false }
}

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()
    val saving by vm.saving.collectAsState()
    val saved by vm.saved.collectAsState()

    var kcal by remember(settings) { mutableStateOf(settings.goal_kcal.toInt().toString()) }
    var protein by remember(settings) { mutableStateOf(settings.goal_protein_g.toInt().toString()) }
    var fat by remember(settings) { mutableStateOf(settings.goal_fat_g.toInt().toString()) }
    var budget by remember(settings) { mutableStateOf(settings.budget_monthly.toInt().toString()) }

    // Computed carbs
    val kcalVal = kcal.toDoubleOrNull() ?: 0.0
    val proteinVal = protein.toDoubleOrNull() ?: 0.0
    val fatVal = fat.toDoubleOrNull() ?: 0.0
    val carbsVal = ((kcalVal - proteinVal * 4 - fatVal * 9) / 4).coerceAtLeast(0.0)

    if (saved) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            vm.clearSaved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Calorie goal
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Daily Goals", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))

                SettingsField("Calories (kcal)", kcal, CalGreen) { kcal = it }
                Spacer(Modifier.height(8.dp))
                SettingsField("Protein (g)", protein, ProteinBlue) { protein = it }
                Spacer(Modifier.height(8.dp))
                SettingsField("Fat (g)", fat, FatRed) { fat = it }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                Spacer(Modifier.height(12.dp))

                // Computed carbs display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Carbs (calculated)", fontSize = 14.sp, color = Color.Gray)
                    Text(
                        "%.0f g".format(carbsVal),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CarbsAmber
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Macro breakdown
                Text("Macro split", fontSize = 12.sp, color = Color.Gray)
                Spacer(Modifier.height(4.dp))
                val proteinKcal = proteinVal * 4
                val fatKcal = fatVal * 9
                val carbsKcal = carbsVal * 4
                val totalMacroKcal = proteinKcal + fatKcal + carbsKcal
                if (totalMacroKcal > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        MacroSplit("P", proteinKcal / totalMacroKcal * 100, ProteinBlue)
                        MacroSplit("C", carbsKcal / totalMacroKcal * 100, CarbsAmber)
                        MacroSplit("F", fatKcal / totalMacroKcal * 100, FatRed)
                    }
                }
            }
        }

        // Budget
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Monthly Budget", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                SettingsField("Budget (EUR)", budget, CostCyan) { budget = it }
            }
        }

        // Save button
        Button(
            onClick = {
                vm.save(
                    AppSettings(
                        goal_kcal = kcal.toDoubleOrNull() ?: settings.goal_kcal,
                        goal_protein_g = protein.toDoubleOrNull() ?: settings.goal_protein_g,
                        goal_fat_g = fat.toDoubleOrNull() ?: settings.goal_fat_g,
                        budget_monthly = budget.toDoubleOrNull() ?: settings.budget_monthly
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving,
            colors = ButtonDefaults.buttonColors(containerColor = CalGreen)
        ) {
            if (saving) {
                SpinningAppIcon(size = 24)
            } else {
                Text(if (saved) "Saved!" else "Save", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        if (saved) {
            Text(
                "Settings saved and synced to cloud",
                fontSize = 12.sp,
                color = CalGreen,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SettingsField(label: String, value: String, color: Color, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = color,
            focusedLabelColor = color,
            cursorColor = color
        )
    )
}

@Composable
private fun MacroSplit(label: String, pct: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("%.0f%%".format(pct), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}
